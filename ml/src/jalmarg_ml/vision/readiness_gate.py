from typing import Dict, Any, List

class VisionReadinessEvaluator:
    """Evaluates IRDID dataset training readiness gate based on multi-dimensional quality criteria."""

    GATE_READY = "READY_FOR_BASELINE_TRAINING"
    GATE_PARTIALLY_READY = "PARTIALLY_READY"
    GATE_BLOCKED_LABELS = "BLOCKED_INVALID_LABELS"
    GATE_BLOCKED_DATA = "BLOCKED_INSUFFICIENT_DATA"
    GATE_BLOCKED_LICENSE = "BLOCKED_LICENSE_REVIEW"
    GATE_BLOCKED_QUALITY = "BLOCKED_DATA_QUALITY"
    GATE_BLOCKED_DEPENDENCY = "BLOCKED_DEPENDENCY"

    def evaluate(self, audit_report: Dict[str, Any], manifest_summary: Dict[str, Any], split_summary: Dict[str, Any]) -> Dict[str, Any]:
        """Evaluates readiness criteria and calculates verdict."""
        blockers: List[str] = []
        warnings: List[str] = []

        # 1. Dependency check
        try:
            import torch
            import torchvision
            has_torch = True
        except ImportError as e:
            has_torch = False
            blockers.append(f"Missing required ML dependency: {e}")

        # 2. Dataset readability
        corrupted = audit_report.get("corrupted_images_count", 0)
        if corrupted > 0:
            warnings.append(f"Found {corrupted} corrupted images in dataset.")

        total_discovered = audit_report.get("total_images_discovered", 0)
        if total_discovered < 100:
            blockers.append(f"Insufficient total images ({total_discovered} discovered; minimum 100 required).")

        # 3. Label validity & sample counts
        positives = manifest_summary.get("usable_positive_samples", 0)
        negatives = manifest_summary.get("usable_negative_samples", 0)

        if positives < 30:
            blockers.append(f"Insufficient verified positive samples ({positives} available; minimum 30 required).")
        if negatives < 30:
            blockers.append(f"Insufficient verified negative samples ({negatives} available; minimum 30 required).")

        # 4. Leakage & duplicate isolation
        leakage_status = split_summary.get("leakage_verification")
        if leakage_status != "VERIFIED_ZERO_LEAKAGE":
            blockers.append(f"Dataset split failed zero-leakage verification: {leakage_status}")

        # 5. License review
        license_status = audit_report.get("license_status", "UNKNOWN")
        if license_status == "LICENSE_REVIEW_REQUIRED":
            warnings.append("Dataset lacks an explicit open-source license file. Permitted for internal research baseline and human-review assist only.")

        # Determine gate status
        if not has_torch:
            verdict = self.GATE_BLOCKED_DEPENDENCY
        elif blockers:
            verdict = self.GATE_BLOCKED_DATA
        else:
            verdict = self.GATE_READY

        return {
            "readiness_gate": verdict,
            "usable_positive_samples": positives,
            "usable_negative_samples": negatives,
            "total_usable_unique_samples": positives + negatives,
            "class_imbalance_ratio": manifest_summary.get("class_imbalance_ratio", "N/A"),
            "duplicates_excluded": manifest_summary.get("excluded_duplicate_samples", 0),
            "ambiguous_excluded": manifest_summary.get("excluded_ambiguous_samples", 0),
            "train_samples": split_summary.get("train_count", 0),
            "val_samples": split_summary.get("val_count", 0),
            "test_samples": split_summary.get("test_count", 0),
            "license_status": license_status,
            "blockers": blockers,
            "warnings": warnings,
            "release_policy_recommendation": "HUMAN_REVIEW_ASSIST"
        }
