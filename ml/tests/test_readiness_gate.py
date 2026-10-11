"""Tests for training readiness decision gate and label integrity"""

import pytest

from jalmarg_ml.labeling.schema import WaterloggingLabel, GroundTruthSample
from jalmarg_ml.labeling.readiness_gate import TrainingReadinessEvaluator


def test_gate_blocks_when_positives_insufficient():
    evaluator = TrainingReadinessEvaluator({
        "minimum_verified_positive_events": 50,
        "minimum_verified_negative_observations": 50,
        "minimum_monsoon_seasons": 2
    })

    # Only 5 positive samples and 0 negative
    samples = [
        GroundTruthSample(
            event_id=f"rep_{i}",
            latitude=28.63,
            longitude=77.22,
            timestamp_utc="2024-07-10T10:00:00Z",
            label=WaterloggingLabel.VERIFIED_WATERLOGGING,
            depth_ft=1.5,
            is_measured_depth=True
        )
        for i in range(5)
    ]

    verdict = evaluator.evaluate(samples, distinct_monsoon_seasons=1, rainfall_data_accessible=True)
    assert verdict.readiness_gate == "BLOCKED_INSUFFICIENT_GROUND_TRUTH"
    assert verdict.usable_positive_events == 5
    assert verdict.usable_negative_observations == 0
    assert any("positive waterlogging events" in r for r in verdict.missing_requirements)
    assert any("negative observations" in r for r in verdict.missing_requirements)
    assert verdict.actionable_gap_report["verified_positives_deficit"] == 45


def test_unknown_or_unverified_never_treated_as_negative():
    evaluator = TrainingReadinessEvaluator()

    # 10 unverified citizen reports and 5 positive
    samples = [
        GroundTruthSample(
            event_id=f"unv_{i}",
            latitude=28.63,
            longitude=77.22,
            timestamp_utc="2024-07-10T10:00:00Z",
            label=WaterloggingLabel.REPORTED_UNVERIFIED
        )
        for i in range(10)
    ] + [
        GroundTruthSample(
            event_id="pos_1",
            latitude=28.63,
            longitude=77.22,
            timestamp_utc="2024-07-10T10:00:00Z",
            label=WaterloggingLabel.VERIFIED_WATERLOGGING
        )
    ]

    verdict = evaluator.evaluate(samples, distinct_monsoon_seasons=1)
    # Negative count MUST be 0 (unverified reports cannot be converted to negatives)
    assert verdict.usable_negative_observations == 0
    assert verdict.readiness_gate == "BLOCKED_INSUFFICIENT_GROUND_TRUTH"


def test_gate_partially_ready_when_thresholds_approach():
    evaluator = TrainingReadinessEvaluator({
        "minimum_verified_positive_events": 50,
        "minimum_verified_negative_observations": 50,
        "minimum_monsoon_seasons": 2
    })

    # 35 positives, 20 negatives (above minimum blocker 10/1, but below 50/50)
    samples = [
        GroundTruthSample(
            event_id=f"pos_{i}",
            latitude=28.63,
            longitude=77.22,
            timestamp_utc="2024-07-10T10:00:00Z",
            label=WaterloggingLabel.VERIFIED_WATERLOGGING
        )
        for i in range(35)
    ] + [
        GroundTruthSample(
            event_id=f"neg_{i}",
            latitude=28.63,
            longitude=77.22,
            timestamp_utc="2024-07-10T10:00:00Z",
            label=WaterloggingLabel.VERIFIED_NO_WATERLOGGING
        )
        for i in range(20)
    ]

    verdict = evaluator.evaluate(samples, distinct_monsoon_seasons=1)
    assert verdict.readiness_gate == "PARTIALLY_READY"
