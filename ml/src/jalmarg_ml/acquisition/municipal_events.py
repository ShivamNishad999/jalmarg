"""Municipal Waterlogging Events & Observations Adapter

Queries the authentic JalMarg PostGIS database on port 5433 to extract verified municipal incidents,
citizen reports, raw multi-observation ledgers, and ground-truth depth readings.
"""

from pathlib import Path
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

from .base import BaseSourceAdapter, AcquisitionResult
from ..provenance.registry import DatasetMetadata, DataSourceType, VerificationStatus


class MunicipalEventsAdapter(BaseSourceAdapter):
    """Adapter for JalMarg PostGIS municipal incidents and citizen reports."""

    def __init__(self, config: Dict[str, Any], db_engine=None):
        super().__init__(config)
        self.db_engine = db_engine

    def validate_connection(self) -> bool:
        """Verify connectivity to jalmarg_twin PostGIS database on port 5433."""
        if not self.db_engine:
            return False
        try:
            with self.db_engine.connect() as conn:
                from sqlalchemy import text
                res = conn.execute(text("SELECT 1")).scalar()
                return res == 1
        except Exception:
            return False

    def query_ground_truth_records(self) -> Dict[str, Any]:
        """Extract authentic historical records and categorize their ground-truth quality."""
        if not self.db_engine:
            raise RuntimeError("Database engine not configured for MunicipalEventsAdapter.")

        from sqlalchemy import text

        results = {
            "verified_incidents": [],
            "verified_reports": [],
            "unverified_reports": [],
            "rejected_reports": [],
            "raw_observations": []
        }

        with self.db_engine.connect() as conn:
            # 1. Official Municipal Incidents
            inc_rows = conn.execute(text(
                "SELECT id, incident_number, title, incident_type, severity, status, "
                "latitude, longitude, location_name, priority_score, related_report_id, created_at "
                "FROM twin_municipal_incidents ORDER BY id ASC"
            )).fetchall()

            for r in inc_rows:
                results["verified_incidents"].append({
                    "id": r[0],
                    "incident_number": r[1],
                    "title": r[2],
                    "type": r[3],
                    "severity": r[4],
                    "status": r[5],
                    "latitude": r[6],
                    "longitude": r[7],
                    "location_name": r[8],
                    "priority_score": r[9],
                    "related_report_id": r[10],
                    "created_at": r[11].isoformat() if r[11] else None,
                    "ground_truth_class": "VERIFIED_WATERLOGGING"
                })

            # 2. Citizen Reports by verification status
            rep_rows = conn.execute(text(
                "SELECT id, location_name, latitude, longitude, severity, depth_ft, "
                "citizen_depth_level, status, verification_status, verified_by, created_at "
                "FROM reports ORDER BY id ASC"
            )).fetchall()

            for r in rep_rows:
                rep_data = {
                    "id": r[0],
                    "location_name": r[1],
                    "latitude": r[2],
                    "longitude": r[3],
                    "severity": r[4],
                    "depth_ft": r[5],
                    "citizen_depth_level": r[6],
                    "status": r[7],
                    "verification_status": r[8],
                    "verified_by": r[9],
                    "created_at": r[10].isoformat() if r[10] else None
                }
                v_status = r[8]
                if v_status == "VERIFIED":
                    rep_data["ground_truth_class"] = "VERIFIED_WATERLOGGING"
                    results["verified_reports"].append(rep_data)
                elif v_status == "REJECTED":
                    rep_data["ground_truth_class"] = "VERIFIED_NO_WATERLOGGING"
                    results["rejected_reports"].append(rep_data)
                else:
                    rep_data["ground_truth_class"] = "REPORTED_UNVERIFIED"
                    results["unverified_reports"].append(rep_data)

            # 3. Raw Observation Ledger
            obs_rows = conn.execute(text(
                "SELECT id, report_id, latitude, longitude, citizen_depth_level, depth_ft, submitted_at "
                "FROM twin_report_observations ORDER BY id ASC"
            )).fetchall()

            for r in obs_rows:
                results["raw_observations"].append({
                    "id": r[0],
                    "report_id": r[1],
                    "latitude": r[2],
                    "longitude": r[3],
                    "depth_level": r[4],
                    "depth_ft": r[5],
                    "submitted_at": r[6].isoformat() if r[6] else None
                })

        return results

    def acquire(self) -> AcquisitionResult:
        """Acquire and audit authentic municipal records from PostGIS."""
        try:
            if not self.validate_connection():
                return AcquisitionResult(
                    success=False,
                    dataset_code=self.dataset_code,
                    status=VerificationStatus.BLOCKED,
                    error_message="Cannot connect to jalmarg_twin PostGIS database on port 5433.",
                    notes="Ensure JalMarg PostgreSQL 17 daemon is running on port 5433."
                )

            data = self.query_ground_truth_records()
            total_events = (
                len(data["verified_incidents"]) +
                len(data["verified_reports"]) +
                len(data["unverified_reports"]) +
                len(data["rejected_reports"])
            )

            verified_positives = len(data["verified_incidents"]) + len(data["verified_reports"])
            verified_negatives = len(data["rejected_reports"])

            metadata = DatasetMetadata(
                dataset_code=self.dataset_code,
                source_name=self.config.get("source_name", "JalMarg PostGIS Municipal Records"),
                source_organization=self.config.get("organization", "Delhi NCR Municipal Authorities / JalMarg"),
                official_source_url="postgresql://localhost:5433/jalmarg_twin",
                provenance_type=DataSourceType.OBSERVED,
                spatial_coverage="Delhi NCR Municipal Boundary & Waterlogging Corridors",
                temporal_coverage="Operational historical log",
                spatial_resolution="Point coordinate observations",
                temporal_resolution="Event timestamped",
                crs="EPSG:4326",
                units="Depth in ft / Categorical verification status",
                license="Municipal Operational Records",
                validation_status=VerificationStatus.VERIFIED,
                known_limitations="Citizen reports are subject to human reporting bias; only verified reports provide ground truth.",
                acquisition_timestamp=datetime.now(timezone.utc),
                extra_attributes={
                    "verified_incidents_count": len(data["verified_incidents"]),
                    "verified_reports_count": len(data["verified_reports"]),
                    "unverified_reports_count": len(data["unverified_reports"]),
                    "rejected_reports_count": len(data["rejected_reports"]),
                    "raw_observations_count": len(data["raw_observations"]),
                    "verified_positive_events": verified_positives,
                    "verified_negative_observations": verified_negatives
                }
            )

            return AcquisitionResult(
                success=True,
                dataset_code=self.dataset_code,
                status=VerificationStatus.VERIFIED,
                records_count=total_events,
                notes=(
                    f"Audited {total_events} total records from jalmarg_twin: "
                    f"{verified_positives} verified positive events, "
                    f"{verified_negatives} verified negative observations, "
                    f"{len(data['unverified_reports'])} unverified citizen submissions."
                ),
                metadata=metadata
            )

        except Exception as e:
            return AcquisitionResult(
                success=False,
                dataset_code=self.dataset_code,
                status=VerificationStatus.BLOCKED,
                error_message=str(e),
                notes="Error querying PostGIS municipal database"
            )
