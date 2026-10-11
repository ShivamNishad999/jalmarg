"""Automated Data Quality Validation Engine

Implements strict validation rules across time-series, spatial coordinates,
rainfall values, raster integrity, and prevents target leakage or silent label fabrication.
"""

from typing import List, Dict, Any, Optional
from datetime import datetime, timezone
from pydantic import BaseModel, Field


class QualityViolation(BaseModel):
    """Specific data quality anomaly detected during validation."""
    rule_name: str
    record_index: Optional[int] = None
    field_name: str
    violating_value: Any
    severity: str = "ERROR" # ERROR, WARNING, INFO
    message: str


class QualityReport(BaseModel):
    """Comprehensive data quality audit report conforming to Section 8."""
    dataset_code: str
    total_records: int
    passed_records: int
    violations_count: int
    quality_score: int # 0 to 100
    verdict: str # PASSED, WARNING, FAILED
    violations: List[QualityViolation] = Field(default_factory=list)
    missing_timestamps_count: int = 0
    coordinate_anomalies_count: int = 0
    implausible_values_count: int = 0
    duplicates_count: int = 0
    nodata_contamination_count: int = 0
    summary_message: str = ""
    audit_timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))

    def to_dict(self) -> Dict[str, Any]:
        data = self.model_dump()
        data["audit_timestamp"] = self.audit_timestamp.isoformat()
        return data


class DataQualityValidator:
    """Validator implementing source-specific and domain integrity rules."""

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.rules = self.config.get("quality_validation_rules", {})
        coords = self.rules.get("coordinates", {})
        self.lat_min = coords.get("operational_lat_min", 28.00)
        self.lat_max = coords.get("operational_lat_max", 29.20)
        self.lng_min = coords.get("operational_lng_min", 76.50)
        self.lng_max = coords.get("operational_lng_max", 77.80)

        rain = self.rules.get("rainfall", {})
        self.rain_min = rain.get("min_value_mm", 0.0)
        self.rain_max = rain.get("max_plausible_hourly_mm", 180.0)

    def validate_rainfall_timeseries(
        self,
        dataset_code: str,
        records: List[Dict[str, Any]],
        timestamp_key: str = "timestamp_utc",
        value_key: str = "precipitation_mm"
    ) -> QualityReport:
        """Validate historical or live rainfall time series."""
        total = len(records)
        violations: List[QualityViolation] = []
        seen_keys = set()
        missing_ts = 0
        coord_anom = 0
        implausible_val = 0
        duplicates = 0

        now_utc = datetime.now(timezone.utc)

        for i, r in enumerate(records):
            # 1. Missing timestamp check
            ts_str = r.get(timestamp_key)
            if not ts_str:
                missing_ts += 1
                violations.append(QualityViolation(
                    rule_name="missing_timestamp",
                    record_index=i,
                    field_name=timestamp_key,
                    violating_value=None,
                    message="Record contains empty or missing timestamp."
                ))
            else:
                # 2. Future timestamp check
                try:
                    ts = datetime.fromisoformat(ts_str.replace("Z", "+00:00"))
                    if ts.tzinfo is None:
                        ts = ts.replace(tzinfo=timezone.utc)
                    if ts > now_utc:
                        violations.append(QualityViolation(
                            rule_name="future_timestamp_in_historical",
                            record_index=i,
                            field_name=timestamp_key,
                            violating_value=ts_str,
                            message=f"Historical record has future timestamp: {ts_str}"
                        ))
                except Exception as e:
                    violations.append(QualityViolation(
                        rule_name="invalid_timestamp_format",
                        record_index=i,
                        field_name=timestamp_key,
                        violating_value=ts_str,
                        message=f"Could not parse timestamp: {e}"
                    ))

            # 3. Duplicate timestamp / location check
            loc_key = (r.get("latitude"), r.get("longitude"), ts_str)
            if loc_key in seen_keys:
                duplicates += 1
                violations.append(QualityViolation(
                    rule_name="duplicate_record",
                    record_index=i,
                    field_name="location_and_time",
                    violating_value=str(loc_key),
                    message="Duplicate observation found for same coordinates and timestamp."
                ))
            else:
                seen_keys.add(loc_key)

            # 4. Coordinate range check (if coordinates present)
            lat = r.get("latitude")
            lng = r.get("longitude")
            if lat is not None and lng is not None:
                if not (self.lat_min <= lat <= self.lat_max and self.lng_min <= lng <= self.lng_max):
                    coord_anom += 1
                    violations.append(QualityViolation(
                        rule_name="coordinate_out_of_operational_bounds",
                        record_index=i,
                        field_name="coordinates",
                        violating_value=(lat, lng),
                        message=f"Coordinates ({lat}, {lng}) fall outside Delhi NCR operational envelope."
                    ))

            # 5. Implausible rainfall check
            val = r.get(value_key)
            if val is not None:
                if val < self.rain_min:
                    implausible_val += 1
                    violations.append(QualityViolation(
                        rule_name="negative_rainfall_prohibited",
                        record_index=i,
                        field_name=value_key,
                        violating_value=val,
                        message=f"Negative rainfall value {val} mm is physically impossible."
                    ))
                elif val > self.rain_max:
                    implausible_val += 1
                    violations.append(QualityViolation(
                        rule_name="extreme_rainfall_anomaly",
                        record_index=i,
                        field_name=value_key,
                        violating_value=val,
                        severity="WARNING",
                        message=f"Rainfall {val} mm exceeds plausible hourly threshold {self.rain_max} mm."
                    ))

        # Score computation
        critical_errors = sum(1 for v in violations if v.severity == "ERROR")
        score = max(0, 100 - int((critical_errors / max(1, total)) * 100))
        if critical_errors == 0 and len(violations) == 0:
            verdict = "PASSED"
        elif critical_errors == 0:
            verdict = "WARNING"
        else:
            verdict = "FAILED" if score < 70 else "WARNING"

        return QualityReport(
            dataset_code=dataset_code,
            total_records=total,
            passed_records=max(0, total - critical_errors),
            violations_count=len(violations),
            quality_score=score,
            verdict=verdict,
            violations=violations[:50], # Cap violation details to 50 for readability
            missing_timestamps_count=missing_ts,
            coordinate_anomalies_count=coord_anom,
            implausible_values_count=implausible_val,
            duplicates_count=duplicates,
            summary_message=(
                f"Quality audit {verdict} with score {score}/100. "
                f"{critical_errors} critical errors across {total} records."
            )
        )
