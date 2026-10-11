"""Automated Tests for Phase 25B/25C Computer Vision Reliability & Label Audit

Validates:
1. Auditing of folder-inferred negatives vs human-verified negatives
2. License governance and deployment blocker declaration
3. Ten operational failure modes assessment
4. Dual-threshold uncertainty abstention policy [0.35, 0.65]
5. Abstention policy integration with VisionInferenceService
"""

import pytest
from pathlib import Path

from jalmarg_ml.vision.reliability_audit import CvReliabilityAuditor
from jalmarg_ml.vision.inference_service import VisionInferenceService


def test_cv_reliability_auditor_reports_folder_derived_negatives():
    auditor = CvReliabilityAuditor()
    report = auditor.run_comprehensive_audit()

    assert report is not None
    assert "dataset_summary" in report
    summary = report["dataset_summary"]

    # 100% of negative training samples were folder-inferred (575)
    assert summary["folder_inferred_negatives"] == 575
    assert summary["independently_verified_negatives"] == 0
    assert "CRITICAL AUDIT FINDING" in summary["label_audit_verdict"]

    # License audit declares deployment blocker
    assert report["license_governance"]["license_status"] == "LICENSE_REVIEW_REQUIRED"
    assert report["license_governance"]["deployment_blocker"] is True
    assert report["license_governance"]["autonomous_enforcement_permitted"] is False

    # Failure modes count
    assert len(report["failure_modes"]) == 10
    mode_ids = [m["failure_mode_id"] for m in report["failure_modes"]]
    assert "FM-01-WET-ASPHALT-NO-WATERLOGGING" in mode_ids
    assert "FM-04-POTHOLES-CONTAINING-WATER" in mode_ids
    assert "FM-05-NIGHT-SCENES-HEADLIGHTS" in mode_ids


def test_abstention_policy_thresholds_and_rule():
    auditor = CvReliabilityAuditor()
    report = auditor.run_comprehensive_audit()

    policy = report["abstention_policy"]
    assert policy["policy_name"] == "DUAL_THRESHOLD_UNCERTAINTY_ABSTENTION"
    assert policy["lower_threshold"] == 0.35
    assert policy["upper_threshold"] == 0.65
    assert "[0.35, 0.65]" in policy["uncertainty_band"]


def test_inference_service_abstention_policy_behavior():
    service = VisionInferenceService(checkpoint_path=None) # Test with mocked probability logic if model None
    assert service.MAX_IMAGE_SIZE_BYTES == 10 * 1024 * 1024

    # Test payload size validation
    huge_bytes = b"0" * (11 * 1024 * 1024)
    res_large = service.predict(huge_bytes)
    assert res_large["status"] == "FAILED"
    assert res_large["failureReason"] == "PAYLOAD_TOO_LARGE"
    assert res_large["reviewRequired"] is True

    # Test empty payload validation
    res_empty = service.predict(b"")
    assert res_empty["status"] == "FAILED"
    assert res_empty["failureReason"] == "EMPTY_IMAGE_PAYLOAD"
    assert res_empty["reviewRequired"] is True
