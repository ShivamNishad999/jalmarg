"""Rainfall Accumulation & Antecedent Feature Engineering

Calculates multi-horizon rainfall accumulations (1h, 3h, 6h, 24h), peak intensities,
and exponential-decay antecedent precipitation indices (API) strictly without future data leakage.
"""

from typing import List, Dict, Any, Optional
from datetime import datetime, timezone, timedelta
import pandas as pd
import numpy as np


class RainfallFeatureCalculator:
    """Computes past-window rainfall aggregates strictly up to prediction timestamp T0."""

    def __init__(self, decay_factor: float = 0.85):
        self.decay_factor = decay_factor

    def compute_windowed_aggregates(
        self,
        observations: List[Dict[str, Any]],
        prediction_timestamp: datetime,
        timestamp_key: str = "timestamp_utc",
        value_key: str = "precipitation_mm"
    ) -> Dict[str, float]:
        """Compute windowed accumulations strictly using observations where t <= prediction_timestamp.

        Prevents target leakage by rejecting any observation occurring after prediction_timestamp.
        """
        if prediction_timestamp.tzinfo is None:
            prediction_timestamp = prediction_timestamp.replace(tzinfo=timezone.utc)

        # 1. Filter out all observations after prediction_timestamp (Leakage Protection)
        # and deduplicate identical timestamps to prevent accidental double-counting
        dedup_records = {}
        for r in observations:
            ts_str = r.get(timestamp_key)
            if not ts_str:
                continue
            ts = datetime.fromisoformat(ts_str.replace("Z", "+00:00"))
            if ts.tzinfo is None:
                ts = ts.replace(tzinfo=timezone.utc)

            if ts <= prediction_timestamp:
                val = r.get(value_key, 0.0)
                # Keep consistent value for timestamp, preventing duplicate summation
                dedup_records[ts] = float(val) if val is not None else 0.0

        if not dedup_records:
            return {
                "rainfall_1h_mm": 0.0,
                "rainfall_3h_mm": 0.0,
                "rainfall_6h_mm": 0.0,
                "rainfall_24h_mm": 0.0,
                "peak_intensity_mm_per_h": 0.0,
                "antecedent_index": 0.0,
                "observations_count_24h": 0
            }

        valid_records = sorted(dedup_records.items(), key=lambda x: x[0])

        # Calculate time difference in hours from T0
        # 1 hour window: [T0 - 1h, T0]
        t1 = prediction_timestamp - timedelta(hours=1)
        t3 = prediction_timestamp - timedelta(hours=3)
        t6 = prediction_timestamp - timedelta(hours=6)
        t24 = prediction_timestamp - timedelta(hours=24)

        rain_1h = sum(v for ts, v in valid_records if ts > t1)
        rain_3h = sum(v for ts, v in valid_records if ts > t3)
        rain_6h = sum(v for ts, v in valid_records if ts > t6)
        rain_24h = sum(v for ts, v in valid_records if ts > t24)

        in_24h = [v for ts, v in valid_records if ts > t24]
        peak_intensity = max(in_24h) if in_24h else 0.0

        # Antecedent Precipitation Index: weighted decay over prior 7 days
        # API = sum( R_day * k^decay )
        api_score = 0.0
        for day in range(1, 8):
            d_start = prediction_timestamp - timedelta(days=day)
            d_end = prediction_timestamp - timedelta(days=day - 1)
            day_rain = sum(v for ts, v in valid_records if d_start < ts <= d_end)
            weight = self.decay_factor ** day
            api_score += day_rain * weight

        return {
            "rainfall_1h_mm": round(rain_1h, 2),
            "rainfall_3h_mm": round(rain_3h, 2),
            "rainfall_6h_mm": round(rain_6h, 2),
            "rainfall_24h_mm": round(rain_24h, 2),
            "peak_intensity_mm_per_h": round(peak_intensity, 2),
            "antecedent_index": round(api_score, 2),
            "observations_count_24h": len(in_24h)
        }
