#!/usr/bin/env python3
"""Executes Phase 25D Evidence Verification, Historical Data Forensics & Database Integrity Audit"""

import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ML_ROOT = REPO_ROOT / "ml"
SRC_DIR = ML_ROOT / "src"

if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

import json
from jalmarg_ml.audit.evidence_verifier import HistoricalEvidenceAuditor
from jalmarg_ml.storage.db import get_db_engine
from sqlalchemy import text


def main():
    print("=" * 80)
    print("JALMARG PHASE 25D: GROUND-TRUTH EVIDENCE VERIFICATION & FORENSICS AUDIT")
    print("=" * 80)

    observations_raw = []
    # 1. Fetch observations from PostGIS port 5433 if available
    try:
        engine = get_db_engine()
        with engine.connect() as conn:
            result = conn.execute(text("""
                SELECT observation_id, corridor_name, location_description,
                       waterlogging_status, verification_status, measured_water_depth_m,
                       depth_measurement_method, observation_timestamp, event_start_time,
                       source_organization, source_reference, source_url, evidence_type,
                       evidence_file_reference, spatial_accuracy_m, is_approximate_location,
                       alignment_status, provenance_classification
                FROM ml_ground_truth_observations
                ORDER BY id;
            """))
            for r in result.mappings():
                obs_dict = dict(r)
                if obs_dict.get("observation_timestamp"):
                    obs_dict["observation_timestamp"] = obs_dict["observation_timestamp"].isoformat()
                if obs_dict.get("event_start_time"):
                    obs_dict["event_start_time"] = obs_dict["event_start_time"].isoformat()
                observations_raw.append(obs_dict)
        print(f"Loaded {len(observations_raw)} observations from PostGIS 127.0.0.1:5433/jalmarg_twin.")
    except Exception as e:
        print(f"Warning: Failed to fetch from DB: {e}. Falling back to artifacts.")
        art_path = ML_ROOT / "artifacts" / "ground_truth_observations.json"
        if art_path.exists():
            with open(art_path, "r", encoding="utf-8") as f:
                observations_raw = json.load(f)
            print(f"Loaded {len(observations_raw)} observations from artifacts.")

    auditor = HistoricalEvidenceAuditor(artifacts_dir=ML_ROOT / "artifacts")
    report = auditor.run_full_audit(observations_raw)

    census = report["recalculated_census"]
    readiness = report["readiness_reassessment"]
    flyway = report["flyway_integrity_audit"]

    print("\n--- RECALCULATED GROUND-TRUTH CENSUS ---")
    print(f"  Total Observations Audited: {census['total_observations_audited']}")
    print(f"  Evidence-Verified Positives: {census['evidence_verified_positives']}")
    print(f"  Evidence-Verified Negatives: {census['evidence_verified_negatives']}")
    print(f"  Partially Verified Observations: {census['partially_verified_observations']}")
    print(f"  Unverified Observations: {census['unverified_observations']}")
    print(f"  Verified Calibrated Depths: {census['verified_calibrated_depth_measurements']}")
    print(f"  Unverified Depth Claims: {census['unverified_depth_claims']}")
    print(f"  Rainfall Aligned: {census['rainfall_aligned_observations']}")
    print(f"  Rainfall Missing: {census['rainfall_missing_observations']}")
    print(f"  Eligible for ML Training: {census['eligible_for_classification_training']}")
    print(f"  Excluded from Training: {census['excluded_from_training']}")

    print("\n--- READINESS REASSESSMENT VERDICT ---")
    print(f"  Verdict: {readiness['readiness_verdict']}")
    for r in readiness["readiness_reasons"]:
        print(f"   * {r}")
    print(f"  Active Operational Engine: {readiness['operational_engine']}")

    print("\n--- FLYWAY V6 INTEGRITY VERDICT ---")
    print(f"  Status: {flyway['validation_status']}")
    print(f"  DB Checksum: {flyway['applied_db_checksum']}")
    print(f"  Local Checksum: {flyway['resolved_local_checksum']}")
    print(f"  Action: {flyway['remediation_status']}")
    print(f"  Remediation Note: {flyway['remediation_plan']}")

    out_file = ML_ROOT / "artifacts" / "manifests" / "phase25d_evidence_verification_report.json"
    print(f"\nAudit Report persisted to: {out_file}")


if __name__ == "__main__":
    main()
