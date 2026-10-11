"""Tests for automated data quality validation engine"""

import pytest
from datetime import datetime, timezone, timedelta

from jalmarg_ml.validation.quality import DataQualityValidator


def test_validation_rejects_negative_rainfall():
    validator = DataQualityValidator()
    records = [
        {"timestamp_utc": "2024-07-01T10:00:00Z", "latitude": 28.63, "longitude": 77.22, "precipitation_mm": -5.2},
        {"timestamp_utc": "2024-07-01T11:00:00Z", "latitude": 28.63, "longitude": 77.22, "precipitation_mm": 12.0}
    ]
    report = validator.validate_rainfall_timeseries("TEST_RAIN", records)
    assert report.implausible_values_count >= 1
    assert any(v.rule_name == "negative_rainfall_prohibited" for v in report.violations)
    assert report.quality_score < 100


def test_validation_detects_missing_timestamps():
    validator = DataQualityValidator()
    records = [
        {"timestamp_utc": "", "latitude": 28.63, "longitude": 77.22, "precipitation_mm": 5.0},
        {"latitude": 28.63, "longitude": 77.22, "precipitation_mm": 10.0}
    ]
    report = validator.validate_rainfall_timeseries("TEST_RAIN", records)
    assert report.missing_timestamps_count == 2
    assert report.verdict == "FAILED"


def test_validation_detects_future_timestamps():
    validator = DataQualityValidator()
    future_time = (datetime.now(timezone.utc) + timedelta(days=30)).isoformat()
    records = [
        {"timestamp_utc": future_time, "latitude": 28.63, "longitude": 77.22, "precipitation_mm": 15.0}
    ]
    report = validator.validate_rainfall_timeseries("TEST_RAIN", records)
    assert any(v.rule_name == "future_timestamp_in_historical" for v in report.violations)


def test_validation_detects_coordinate_out_of_bounds():
    validator = DataQualityValidator()
    records = [
        # Mumbai coordinate (19.07, 72.87) outside Delhi NCR envelope
        {"timestamp_utc": "2024-07-01T10:00:00Z", "latitude": 19.07, "longitude": 72.87, "precipitation_mm": 25.0}
    ]
    report = validator.validate_rainfall_timeseries("TEST_RAIN", records)
    assert report.coordinate_anomalies_count == 1
    assert any(v.rule_name == "coordinate_out_of_operational_bounds" for v in report.violations)


def test_validation_clean_records_pass():
    validator = DataQualityValidator()
    records = [
        {"timestamp_utc": "2024-07-01T10:00:00Z", "latitude": 28.63, "longitude": 77.22, "precipitation_mm": 12.5},
        {"timestamp_utc": "2024-07-01T11:00:00Z", "latitude": 28.63, "longitude": 77.22, "precipitation_mm": 3.0}
    ]
    report = validator.validate_rainfall_timeseries("TEST_RAIN", records)
    assert report.quality_score == 100
    assert report.verdict == "PASSED"
    assert len(report.violations) == 0
