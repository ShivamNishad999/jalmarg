import pytest
from pathlib import Path

from jalmarg_ml.audit.evidence_verifier import (
    EvidenceStatus,
    DepthEvidenceStatus,
    RainfallAlignmentStatus,
    TrainingEligibilityStatus,
    HistoricalEvidenceAuditor
)


@pytest.fixture
def auditor():
    artifacts_dir = Path(__file__).resolve().parent.parent / "artifacts"
    return HistoricalEvidenceAuditor(artifacts_dir=artifacts_dir)


def test_historical_evidence_classification_states(auditor):
    # Historical municipal report with public corroboration but no raw file
    hist_obs = {
        "observation_id": "HIST-2023-DEL-01",
        "corridor_name": "Connaught Place - Paharganj",
        "location_description": "Minto Bridge Underpass",
        "observation_timestamp": "2023-07-09T14:00:00Z",
        "waterlogging_status": "VERIFIED_WATERLOGGING",
        "measured_water_depth_m": 1.25,
        "source_reference": "DTP Traffic Advisory #2023/07/09/MB-1",
        "evidence_file_reference": None
    }
    audit = auditor.audit_observation(hist_obs)
    assert audit.evidence_status == EvidenceStatus.EVIDENCE_PARTIALLY_VERIFIED
    assert "publicly corroborated" in audit.evidence_rationale.lower()
    assert audit.training_eligibility != TrainingEligibilityStatus.ELIGIBLE_FOR_CLASSIFICATION


def test_depth_measurement_audit_excludes_uncalibrated_depths(auditor):
    obs_with_depth = {
        "observation_id": "HIST-2024-DEL-01",
        "corridor_name": "Connaught Place - Paharganj",
        "location_description": "Minto Bridge Underpass",
        "observation_timestamp": "2024-06-28T05:00:00Z",
        "waterlogging_status": "VERIFIED_WATERLOGGING",
        "measured_water_depth_m": 0.95,
        "source_reference": "DTP Traffic Advisory #2024/06/28/MB-01",
        "evidence_file_reference": None
    }
    audit = auditor.audit_observation(obs_with_depth)
    assert audit.depth_status == DepthEvidenceStatus.DEPTH_UNVERIFIED_CLAIM
    assert audit.training_eligibility != TrainingEligibilityStatus.ELIGIBLE_FOR_DEPTH_REGRESSION
    assert any("gauge" in r.lower() or "uncalibrated" in r.lower() for r in audit.eligibility_exclusion_reasons)


def test_rainfall_alignment_forensics_detects_temporal_gap(auditor):
    # Unacquired historical period (e.g. 2022 monsoon or 2024 pre-monsoon dry season) has zero data
    status_2022 = auditor.audit_rainfall_coverage("2022-07-09T14:00:00Z")
    assert status_2022 == RainfallAlignmentStatus.ALIGNMENT_MISSING_DATA

    status_dry_season_2024 = auditor.audit_rainfall_coverage("2024-01-15T10:00:00Z")
    assert status_dry_season_2024 == RainfallAlignmentStatus.ALIGNMENT_MISSING_DATA

    status_post_monsoon_2024 = auditor.audit_rainfall_coverage("2024-11-20T10:00:00Z")
    assert status_post_monsoon_2024 == RainfallAlignmentStatus.ALIGNMENT_MISSING_DATA

    # Authentic monsoon periods acquired in repository detect valid partial reanalysis coverage
    status_2023 = auditor.audit_rainfall_coverage("2023-07-09T14:00:00Z")
    assert status_2023 == RainfallAlignmentStatus.ALIGNMENT_PARTIAL

    status_june_2024 = auditor.audit_rainfall_coverage("2024-06-28T05:00:00Z")
    assert status_june_2024 == RainfallAlignmentStatus.ALIGNMENT_PARTIAL

    status_july_2024 = auditor.audit_rainfall_coverage("2024-07-15T10:00:00Z")
    assert status_july_2024 == RainfallAlignmentStatus.ALIGNMENT_PARTIAL


def test_training_eligibility_evaluator_disqualifies_unverified_records(auditor):
    # Unverified negative inspection without photo
    neg_obs = {
        "observation_id": "INSP-NEG-2024-01",
        "corridor_name": "Connaught Place Arterial",
        "location_description": "Minto Bridge Underpass",
        "observation_timestamp": "2024-07-02T15:30:00Z",
        "waterlogging_status": "VERIFIED_NO_WATERLOGGING",
        "measured_water_depth_m": 0.0,
        "source_reference": "FIELD-LOG-INSP-NEG-2024-01",
        "evidence_file_reference": None
    }
    audit = auditor.audit_observation(neg_obs)
    assert audit.evidence_status == EvidenceStatus.EVIDENCE_UNVERIFIED
    assert audit.training_eligibility != TrainingEligibilityStatus.ELIGIBLE_FOR_CLASSIFICATION


def test_full_audit_census_and_readiness_reassessment(auditor):
    observations = [
        {
            "observation_id": "HIST-2023-DEL-01",
            "observation_timestamp": "2023-07-09T14:00:00Z",
            "waterlogging_status": "VERIFIED_WATERLOGGING",
            "measured_water_depth_m": 1.25,
            "source_reference": "DTP #1"
        },
        {
            "observation_id": "INSP-NEG-2024-01",
            "observation_timestamp": "2024-07-02T15:30:00Z",
            "waterlogging_status": "VERIFIED_NO_WATERLOGGING",
            "measured_water_depth_m": 0.0,
            "source_reference": "INSP #1"
        }
    ]
    report = auditor.run_full_audit(observations, save_to_disk=False)
    census = report["recalculated_census"]
    readiness = report["readiness_reassessment"]

    assert census["evidence_verified_positives"] == 0
    assert census["evidence_verified_negatives"] == 0
    assert census["verified_calibrated_depth_measurements"] == 0
    assert census["eligible_for_classification_training"] == 0

    assert readiness["readiness_verdict"] == "BLOCKED_UNVERIFIED_GROUND_TRUTH"
    assert readiness["supervised_training_executed"] is False
