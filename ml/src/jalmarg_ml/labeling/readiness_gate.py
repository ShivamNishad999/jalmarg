"""Machine Learning Training Readiness Decision Gate

Evaluates available authentic datasets against strict criteria to determine whether
supervised machine learning training can proceed, or whether it must be BLOCKED
due to insufficient verified ground-truth labels, license restrictions, or data access.
"""

from typing import Dict, Any, List, Optional, Union
from datetime import datetime, timezone
from pydantic import BaseModel, Field

from .schema import WaterloggingLabel, GroundTruthSample, GroundTruthObservation


class TrainingReadinessVerdict(BaseModel):
    """Result of training readiness evaluation conforming to Section 10."""
    readiness_gate: str # READY_FOR_BASELINE_TRAINING, PARTIALLY_READY, BLOCKED_INSUFFICIENT_GROUND_TRUTH, BLOCKED_DATA_ACCESS, BLOCKED_DATA_QUALITY, BLOCKED_TEMPORAL_COVERAGE, BLOCKED_LICENSE_REVIEW
    usable_positive_events: int
    usable_negative_observations: int
    monsoon_seasons_covered: int
    class_imbalance_ratio: str
    depth_measurement_availability: str
    target_lead_times_supported: List[str] = Field(default_factory=lambda: ["1h", "3h", "6h"])
    target_leakage_risks_found: int = 0
    missing_requirements: List[str] = Field(default_factory=list)
    actionable_gap_report: Dict[str, Any] = Field(default_factory=dict)
    summary_notes: str = ""
    assessed_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

    def to_dict(self) -> Dict[str, Any]:
        data = self.model_dump()
        data["assessed_at"] = self.assessed_at.isoformat()
        return data


class TrainingReadinessEvaluator:
    """Evaluates whether ground-truth records meet statistical and operational criteria."""

    def __init__(self, criteria_config: Optional[Dict[str, Any]] = None):
        self.config = criteria_config or {}
        self.min_positives = self.config.get("minimum_verified_positive_events", 50)
        self.min_negatives = self.config.get("minimum_verified_negative_observations", 50)
        self.min_seasons = self.config.get("minimum_monsoon_seasons", 2)
        self.min_depth_samples = self.config.get("minimum_depth_samples", 30)

    def evaluate(
        self,
        samples: Union[List[GroundTruthSample], List[GroundTruthObservation]],
        distinct_monsoon_seasons: int = 1,
        rainfall_data_accessible: bool = True,
        license_blocked: bool = False,
        quality_passed: bool = True
    ) -> TrainingReadinessVerdict:
        """Execute decision gate evaluation conforming to Phase 25C Section 10."""
        positives = []
        negatives = []
        unverified = []
        measured_depths = []

        for s in samples:
            if isinstance(s, GroundTruthObservation):
                if s.waterlogging_status == WaterloggingLabel.VERIFIED_WATERLOGGING:
                    positives.append(s)
                    if s.measured_water_depth_m is not None and s.measured_water_depth_m > 0:
                        measured_depths.append(s)
                elif s.waterlogging_status == WaterloggingLabel.VERIFIED_NO_WATERLOGGING:
                    negatives.append(s)
                elif s.waterlogging_status == WaterloggingLabel.REPORTED_UNVERIFIED:
                    unverified.append(s)
            elif isinstance(s, GroundTruthSample):
                if s.label == WaterloggingLabel.VERIFIED_WATERLOGGING:
                    positives.append(s)
                    if s.is_measured_depth and (s.depth_ft or 0) > 0:
                        measured_depths.append(s)
                elif s.label == WaterloggingLabel.VERIFIED_NO_WATERLOGGING:
                    negatives.append(s)
                elif s.label == WaterloggingLabel.REPORTED_UNVERIFIED:
                    unverified.append(s)

        n_pos = len(positives)
        n_neg = len(negatives)
        n_depth = len(measured_depths)

        missing: List[str] = []

        if license_blocked:
            missing.append("Dataset license review required: Unresolved licensing restricts model training or deployment.")

        if not rainfall_data_accessible:
            missing.append("Historical rainfall dataset (ERA5-Land / IMD) is blocked or inaccessible.")

        if not quality_passed:
            missing.append("Data quality audit failed or contains unacceptable anomalies.")

        if n_pos < self.min_positives:
            missing.append(
                f"Insufficient verified positive waterlogging events ({n_pos} available; minimum {self.min_positives} required)."
            )

        if n_neg < self.min_negatives:
            missing.append(
                f"Insufficient verified negative observations ({n_neg} available; minimum {self.min_negatives} required). "
                "Unassessed or clear locations cannot be synthetically labeled as negative without evidence."
            )

        if distinct_monsoon_seasons < self.min_seasons:
            missing.append(
                f"Insufficient monsoon seasons represented ({distinct_monsoon_seasons} season(s); minimum {self.min_seasons} required to prevent single-season overfitting)."
            )

        if n_depth < self.min_depth_samples:
            missing.append(
                f"Water-depth regression is BLOCKED ({n_depth} measured depth readings; minimum {self.min_depth_samples} required)."
            )

        # Calculate imbalance ratio
        if n_neg > 0:
            ratio_str = f"1:{n_pos / n_neg:.1f}" if n_neg > n_pos else f"{n_pos / n_neg:.1f}:1"
        else:
            ratio_str = f"{n_pos}:0 (No verified negative samples)"

        depth_availability_str = (
            f"AVAILABLE ({n_depth} samples)" if n_depth >= self.min_depth_samples
            else f"INSUFFICIENT ({n_depth}/{self.min_depth_samples} samples)"
        )

        # Gate decision logic conforming strictly to Section 10
        if license_blocked:
            gate = "BLOCKED_LICENSE_REVIEW"
            summary = "Supervised training is BLOCKED: Dataset licensing requires legal/open-source clearance."
        elif not rainfall_data_accessible:
            gate = "BLOCKED_DATA_ACCESS"
            summary = "Supervised training is BLOCKED: Rainfall data source inaccessible."
        elif not quality_passed:
            gate = "BLOCKED_DATA_QUALITY"
            summary = "Supervised training is BLOCKED: Data quality audit threshold not met."
        elif distinct_monsoon_seasons < 1:
            gate = "BLOCKED_TEMPORAL_COVERAGE"
            summary = "Supervised training is BLOCKED: Zero monsoon seasons represented in verified records."
        elif n_pos < 10 or n_neg == 0:
            gate = "BLOCKED_INSUFFICIENT_GROUND_TRUTH"
            summary = (
                f"Supervised ML training is BLOCKED due to insufficient ground-truth labels. "
                f"Found {n_pos} verified positive events and {n_neg} verified negative observations. "
                f"Heuristic flood risk engine must continue serving operational predictions."
            )
        elif n_pos < self.min_positives or distinct_monsoon_seasons < self.min_seasons:
            gate = "PARTIALLY_READY"
            summary = (
                f"Training foundation is PARTIALLY READY for exploratory prototyping, but production "
                f"supervised training is withheld pending additional monsoon seasons ({distinct_monsoon_seasons}/{self.min_seasons}) "
                f"and verified events ({n_pos}/{self.min_positives})."
            )
        else:
            gate = "READY_FOR_BASELINE_TRAINING"
            summary = f"Criteria met with {n_pos} verified positives and {n_neg} verified negatives across {distinct_monsoon_seasons} monsoon seasons."

        gap_report = {
            "verified_positives_deficit": max(0, self.min_positives - n_pos),
            "verified_negatives_deficit": max(0, self.min_negatives - n_neg),
            "unverified_pending_reports": len(unverified),
            "monsoon_seasons_deficit": max(0, self.min_seasons - distinct_monsoon_seasons),
            "depth_regression_status": "BLOCKED" if n_depth < self.min_depth_samples else "READY",
            "interim_recommendation": (
                "Continue operating the Phase 22 calibrated heuristic flood-risk engine. "
                "Collect and verify crowd reports during upcoming monsoon seasons to expand the ground-truth ledger."
            )
        }

        return TrainingReadinessVerdict(
            readiness_gate=gate,
            usable_positive_events=n_pos,
            usable_negative_observations=n_neg,
            monsoon_seasons_covered=distinct_monsoon_seasons,
            class_imbalance_ratio=ratio_str,
            depth_measurement_availability=depth_availability_str,
            missing_requirements=missing,
            actionable_gap_report=gap_report,
            summary_notes=summary
        )
