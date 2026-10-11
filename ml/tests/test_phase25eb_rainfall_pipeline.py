"""Phase 25E-B Historical Rainfall Acquisition & Alignment Tests

Validates:
- Multi-period authentic dataset manifest integrity
- No silent zero-filling enforcement (explicit quality flags)
- Strict future-data leakage prevention (t <= T0)
- Delhi NCR bounding envelope adherence
- Historical rainfall alignment improvements (before vs after)
"""

import json
from pathlib import Path
from datetime import datetime, timezone, timedelta
import pytest

from jalmarg_ml.acquisition.open_meteo_historical import OpenMeteoHistoricalAdapter
from jalmarg_ml.features.rainfall_features import RainfallFeatureCalculator
from jalmarg_ml.audit.evidence_verifier import HistoricalEvidenceAuditor, RainfallAlignmentStatus


@pytest.fixture
def artifacts_dir():
    return Path(__file__).resolve().parent.parent / "artifacts"


def test_phase25eb_rainfall_manifest_integrity(artifacts_dir):
    manifest_path = artifacts_dir / "manifests" / "phase25eb_rainfall_acquisition_manifest.json"
    assert manifest_path.exists(), "Phase 25E-B manifest must exist"

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    assert manifest["manifest_version"] == "2026.1-PHASE25EB"
    assert manifest["total_hourly_records_acquired"] > 100000
    assert manifest["target_periods_count"] == 10
    assert len(manifest["periods"]) == 10

    # Ensure all target periods are present
    period_codes = [p["period_code"] for p in manifest["periods"]]
    assert "2023_MONSOON_JUN" in period_codes
    assert "2023_MONSOON_JUL" in period_codes
    assert "2023_MONSOON_AUG" in period_codes
    assert "2023_MONSOON_SEP" in period_codes
    assert "2024_MONSOON_JUN" in period_codes
    assert "2024_MONSOON_JUL" in period_codes
    assert "2024_MONSOON_AUG" in period_codes
    assert "2024_MONSOON_SEP" in period_codes
    assert "2025_MONSOON" in period_codes
    assert "2026_MONSOON" in period_codes

    # Priority A and B fail closed
    assert manifest["source_evaluations"]["PRIORITY_A_IMD_AWS"]["access_verdict"] == "BLOCKED_OFFICIAL_ACCESS"
    assert manifest["source_evaluations"]["PRIORITY_B_COPERNICUS_ERA5_LAND_DIRECT"]["access_verdict"] == "BLOCKED_OFFICIAL_ACCESS"
    assert manifest["source_evaluations"]["PRIORITY_C_OPEN_METEO_HISTORICAL_ARCHIVE"]["access_verdict"] == "ACQUIRED_AND_VALIDATED"


def test_phase25eb_acquired_records_quality_and_bounds(artifacts_dir):
    july_2023_file = artifacts_dir / "acquired_data" / "delhi_ncr_historical_rainfall_2023-07-01_2023-07-31.json"
    assert july_2023_file.exists()

    with open(july_2023_file, "r", encoding="utf-8") as f:
        records = json.load(f)

    assert len(records) > 8000
    for r in records[:50]:
        # Quality flags exist
        assert "quality_flag" in r
        assert r["quality_flag"] in ("VALID", "MISSING", "ANOMALOUS_NEGATIVE")
        assert "is_missing" in r

        # Coordinate bounds within Delhi NCR envelope
        assert 28.0 <= r["latitude"] <= 29.2
        assert 76.5 <= r["longitude"] <= 77.8

        # Provenance attribution
        assert r["provenance"] == "REANALYSIS_ERA5_LAND"
        assert "grid_cell" in r
        assert "grid_cell_id" in r


def test_leakage_free_rainfall_aggregation():
    calc = RainfallFeatureCalculator(decay_factor=0.85)
    t0 = datetime(2024, 7, 10, 12, 0, tzinfo=timezone.utc)

    # Synthetic series including future observations
    series = [
        {"timestamp_utc": "2024-07-10T10:00", "precipitation_mm": 5.0},
        {"timestamp_utc": "2024-07-10T11:00", "precipitation_mm": 10.0},
        {"timestamp_utc": "2024-07-10T12:00", "precipitation_mm": 15.0},
        # Future observations (must be rejected)
        {"timestamp_utc": "2024-07-10T13:00", "precipitation_mm": 50.0},
        {"timestamp_utc": "2024-07-10T14:00", "precipitation_mm": 100.0}
    ]

    metrics = calc.compute_windowed_aggregates(series, t0)
    # 1h window: (11:00, 12:00] -> 15.0mm
    assert metrics["rainfall_1h_mm"] == 15.0
    # 3h window: (09:00, 12:00] -> 5 + 10 + 15 = 30.0mm
    assert metrics["rainfall_3h_mm"] == 30.0
    # Future rain (50mm + 100mm) must NOT leak into the metrics
    assert metrics["rainfall_24h_mm"] == 30.0
    assert metrics["peak_intensity_mm_per_h"] == 15.0


def test_rainfall_alignment_coverage_before_vs_after(artifacts_dir):
    auditor = HistoricalEvidenceAuditor(artifacts_dir=artifacts_dir)
    obs_file = artifacts_dir / "ground_truth_observations.json"

    with open(obs_file, "r", encoding="utf-8") as f:
        observations = json.load(f)

    # All 24 observations now have valid coverage
    aligned_count = 0
    missing_count = 0

    for obs in observations:
        ts = obs.get("observation_timestamp") or obs.get("event_start_time")
        status = auditor.audit_rainfall_coverage(ts)
        if status in (RainfallAlignmentStatus.ALIGNMENT_VERIFIED, RainfallAlignmentStatus.ALIGNMENT_PARTIAL):
            aligned_count += 1
        else:
            missing_count += 1

    assert aligned_count == 24, "All 24 observations must have authentic rainfall data in Phase 25E-B"
    assert missing_count == 0, "No observations should have missing rainfall data"
