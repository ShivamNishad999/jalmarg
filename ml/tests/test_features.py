"""Tests for feature engineering and target leakage prevention"""

import pytest
from datetime import datetime, timezone, timedelta

from jalmarg_ml.features.rainfall_features import RainfallFeatureCalculator
from jalmarg_ml.features.feature_builder import FeatureBuilder


def test_rainfall_aggregation_windows():
    calc = RainfallFeatureCalculator()
    t0 = datetime(2024, 7, 15, 12, 0, 0, tzinfo=timezone.utc)

    observations = [
        {"timestamp_utc": "2024-07-15T11:30:00Z", "precipitation_mm": 10.0}, # in 1h, 3h, 6h, 24h
        {"timestamp_utc": "2024-07-15T10:00:00Z", "precipitation_mm": 15.0}, # in 3h, 6h, 24h
        {"timestamp_utc": "2024-07-15T08:00:00Z", "precipitation_mm": 20.0}, # in 6h, 24h
        {"timestamp_utc": "2024-07-14T20:00:00Z", "precipitation_mm": 5.0},  # in 24h
    ]

    features = calc.compute_windowed_aggregates(observations, prediction_timestamp=t0)
    assert features["rainfall_1h_mm"] == 10.0
    assert features["rainfall_3h_mm"] == 25.0 # 10 + 15
    assert features["rainfall_6h_mm"] == 45.0 # 10 + 15 + 20
    assert features["rainfall_24h_mm"] == 50.0 # 10 + 15 + 20 + 5
    assert features["peak_intensity_mm_per_h"] == 20.0


def test_target_leakage_strictly_prevents_future_rainfall():
    calc = RainfallFeatureCalculator()
    t0 = datetime(2024, 7, 15, 12, 0, 0, tzinfo=timezone.utc)

    # Observations including FUTURE timestamps (12:30 and 13:00)
    observations = [
        {"timestamp_utc": "2024-07-15T11:30:00Z", "precipitation_mm": 8.0}, # VALID PAST
        {"timestamp_utc": "2024-07-15T12:00:00Z", "precipitation_mm": 4.0}, # AT T0 (VALID)
        {"timestamp_utc": "2024-07-15T12:30:00Z", "precipitation_mm": 45.0}, # FUTURE (LEAKAGE RISK)
        {"timestamp_utc": "2024-07-15T13:00:00Z", "precipitation_mm": 100.0} # FUTURE (LEAKAGE RISK)
    ]

    features = calc.compute_windowed_aggregates(observations, prediction_timestamp=t0)
    # Total rain in past 1h must only be 8.0 + 4.0 = 12.0
    # Future 45.0 and 100.0 MUST NOT leak into features
    assert features["rainfall_1h_mm"] == 12.0
    assert features["rainfall_3h_mm"] == 12.0
    assert features["peak_intensity_mm_per_h"] == 8.0


def test_historical_event_density_prevents_future_leakage():
    builder = FeatureBuilder()
    t0 = datetime(2024, 7, 15, 12, 0, 0, tzinfo=timezone.utc)

    incidents = [
        # Past incident 100m away on July 10 (VALID)
        {"created_at": "2024-07-10T10:00:00Z", "latitude": 28.6340, "longitude": 77.2260},
        # Future incident 100m away on July 16 (MUST NOT BE COUNTED)
        {"created_at": "2024-07-16T10:00:00Z", "latitude": 28.6340, "longitude": 77.2260}
    ]

    sample = builder.build_sample_features(
        latitude=28.6347,
        longitude=77.2263,
        prediction_timestamp=t0,
        rainfall_observations=[],
        elevation_m=214.2,
        historical_incidents=incidents
    )

    # Only the July 10 incident should be counted
    assert sample["prior_verified_incidents_500m"] == 1


def test_inter_hour_boundary_conditions_and_incomplete_hour_exclusion():
    """Event at 11:35 UTC: completed interval 11:00 is included; incomplete 12:00 is rejected."""
    calc = RainfallFeatureCalculator()
    t_event = datetime(2024, 7, 15, 11, 35, 0, tzinfo=timezone.utc)

    observations = [
        {"timestamp_utc": "2024-07-15T10:00:00Z", "precipitation_mm": 5.0},
        {"timestamp_utc": "2024-07-15T11:00:00Z", "precipitation_mm": 12.0},  # Completed before 11:35
        {"timestamp_utc": "2024-07-15T12:00:00Z", "precipitation_mm": 25.0},  # Incomplete at 11:35 -> EXCLUDED
    ]

    features = calc.compute_windowed_aggregates(observations, prediction_timestamp=t_event)
    # At 11:35, 1h window is [10:35, 11:35]. The 11:00 record falls within this window.
    assert features["rainfall_1h_mm"] == 12.0
    # Incomplete hour 12:00 must NOT leak
    assert features["rainfall_24h_mm"] == 17.0  # 5.0 + 12.0


def test_duplicate_timestamp_deduplication_prevents_double_counting():
    """Duplicate records for same timestamp must be deduplicated without summing twice."""
    calc = RainfallFeatureCalculator()
    t0 = datetime(2024, 7, 15, 12, 0, 0, tzinfo=timezone.utc)

    observations = [
        {"timestamp_utc": "2024-07-15T11:00:00Z", "precipitation_mm": 10.0},
        {"timestamp_utc": "2024-07-15T11:00:00Z", "precipitation_mm": 10.0},  # Duplicate!
        {"timestamp_utc": "2024-07-15T12:00:00Z", "precipitation_mm": 15.0},
    ]

    features = calc.compute_windowed_aggregates(observations, prediction_timestamp=t0)
    # 1h window (11:00-12:00] is 15.0 mm.
    # 3h window (09:00-12:00] contains 11:00 and 12:00.
    # Deduplication ensures 11:00 is counted once: 10.0 + 15.0 = 25.0 (NOT 35.0)
    assert features["rainfall_1h_mm"] == 15.0
    assert features["rainfall_3h_mm"] == 25.0


def test_timezone_conversion_boundary_handling():
    """Event in local IST (UTC+05:30) matches equivalent UTC cutoff identically."""
    calc = RainfallFeatureCalculator()
    # 17:30 IST is 12:00 UTC
    ist_tz = timezone(timedelta(hours=5, minutes=30))
    t_ist = datetime(2024, 7, 15, 17, 30, 0, tzinfo=ist_tz)

    observations = [
        {"timestamp_utc": "2024-07-15T11:00:00Z", "precipitation_mm": 14.0},
        {"timestamp_utc": "2024-07-15T12:00:00Z", "precipitation_mm": 6.0},
        {"timestamp_utc": "2024-07-15T13:00:00Z", "precipitation_mm": 50.0},  # 18:30 IST (FUTURE)
    ]

    features = calc.compute_windowed_aggregates(observations, prediction_timestamp=t_ist)
    # At 12:00 UTC, 1h window (11:00-12:00] is 6.0 mm.
    # 3h window (09:00-12:00] is 14.0 + 6.0 = 20.0 mm.
    # Future 13:00 UTC (50.0 mm) must NOT be included.
    assert features["rainfall_1h_mm"] == 6.0
    assert features["rainfall_3h_mm"] == 20.0
    assert features["rainfall_24h_mm"] == 20.0

