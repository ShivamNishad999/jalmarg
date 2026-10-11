"""Python to PostGIS Metadata Synchronization

Synchronizes evaluated dataset manifests, quality audit reports, and training readiness
verdicts directly into the dedicated JalMarg PostGIS database on port 5433.
"""

import json
from typing import List, Dict, Any, Optional
from datetime import datetime, timezone
from sqlalchemy import text
from sqlalchemy.engine import Engine

from ..provenance.registry import DatasetMetadata, VerificationStatus
from ..validation.quality import QualityReport
from ..labeling.readiness_gate import TrainingReadinessVerdict


class MetadataSynchronizer:
    """Synchronizes ML metadata models into PostGIS ml_* tables."""

    def __init__(self, engine: Engine):
        self.engine = engine

    def sync_dataset_metadata(self, metadata: DatasetMetadata) -> None:
        """Upsert a dataset metadata record into ml_dataset_registry."""
        sql = text("""
            INSERT INTO ml_dataset_registry (
                dataset_code, dataset_name, source_organization, official_source_url,
                provenance_type, spatial_coverage, temporal_coverage, spatial_resolution,
                temporal_resolution, crs, units, license, local_storage_path,
                file_size_bytes, sha256_checksum, validation_status, known_limitations,
                last_acquired_at, updated_at
            ) VALUES (
                :code, :name, :org, :url,
                :prov, :spatial, :temporal, :sp_res,
                :temp_res, :crs, :units, :license, :path,
                :size, :sha256, :status, :limits,
                :acquired, now()
            )
            ON CONFLICT (dataset_code) DO UPDATE SET
                dataset_name = EXCLUDED.dataset_name,
                source_organization = EXCLUDED.source_organization,
                official_source_url = EXCLUDED.official_source_url,
                provenance_type = EXCLUDED.provenance_type,
                spatial_coverage = EXCLUDED.spatial_coverage,
                temporal_coverage = EXCLUDED.temporal_coverage,
                spatial_resolution = EXCLUDED.spatial_resolution,
                temporal_resolution = EXCLUDED.temporal_resolution,
                crs = EXCLUDED.crs,
                units = EXCLUDED.units,
                license = EXCLUDED.license,
                local_storage_path = EXCLUDED.local_storage_path,
                file_size_bytes = EXCLUDED.file_size_bytes,
                sha256_checksum = EXCLUDED.sha256_checksum,
                validation_status = EXCLUDED.validation_status,
                known_limitations = EXCLUDED.known_limitations,
                last_acquired_at = EXCLUDED.last_acquired_at,
                updated_at = now();
        """)

        params = {
            "code": metadata.dataset_code,
            "name": metadata.source_name,
            "org": metadata.source_organization,
            "url": metadata.official_source_url,
            "prov": metadata.provenance_type.value,
            "spatial": metadata.spatial_coverage,
            "temporal": metadata.temporal_coverage,
            "sp_res": metadata.spatial_resolution,
            "temp_res": metadata.temporal_resolution,
            "crs": metadata.crs,
            "units": metadata.units,
            "license": metadata.license,
            "path": metadata.local_storage_path,
            "size": metadata.file_size_bytes,
            "sha256": metadata.sha256_checksum,
            "status": metadata.validation_status.value,
            "limits": metadata.known_limitations,
            "acquired": metadata.acquisition_timestamp or datetime.now(timezone.utc)
        }

        with self.engine.begin() as conn:
            conn.execute(sql, params)

    def log_acquisition_run(
        self,
        dataset_code: str,
        status: str,
        records_acquired: int = 0,
        http_status_code: Optional[int] = None,
        error_message: Optional[str] = None,
        notes: Optional[str] = None
    ) -> None:
        """Insert an execution log entry into ml_acquisition_runs."""
        sql = text("""
            INSERT INTO ml_acquisition_runs (
                dataset_code, run_timestamp, status, records_acquired,
                http_status_code, error_message, notes
            ) VALUES (
                :code, now(), :status, :records,
                :http_code, :error, :notes
            );
        """)
        params = {
            "code": dataset_code,
            "status": status,
            "records": records_acquired,
            "http_code": http_status_code,
            "error": error_message,
            "notes": notes
        }
        with self.engine.begin() as conn:
            conn.execute(sql, params)

    def record_quality_report(self, report: QualityReport) -> None:
        """Insert a quality audit report into ml_data_quality_reports."""
        sql = text("""
            INSERT INTO ml_data_quality_reports (
                dataset_code, report_timestamp, total_records_checked,
                missing_timestamps_count, coordinate_anomalies_count,
                implausible_values_count, duplicates_count, nodata_contamination_count,
                quality_score, quality_verdict, details_json
            ) VALUES (
                :code, now(), :total,
                :missing_ts, :coord_anom,
                :implausible, :dups, :nodata,
                :score, :verdict, :details
            );
        """)
        details = {
            "summary_message": report.summary_message,
            "violations_sample": [v.model_dump() for v in report.violations[:10]]
        }
        params = {
            "code": report.dataset_code,
            "total": report.total_records,
            "missing_ts": report.missing_timestamps_count,
            "coord_anom": report.coordinate_anomalies_count,
            "implausible": report.implausible_values_count,
            "dups": report.duplicates_count,
            "nodata": report.nodata_contamination_count,
            "score": report.quality_score,
            "verdict": report.verdict,
            "details": json.dumps(details)
        }
        with self.engine.begin() as conn:
            conn.execute(sql, params)

    def record_training_readiness(self, verdict: TrainingReadinessVerdict) -> None:
        """Insert training readiness assessment into ml_training_readiness."""
        sql = text("""
            INSERT INTO ml_training_readiness (
                assessment_timestamp, readiness_gate, usable_positive_events,
                usable_negative_observations, monsoon_seasons_covered,
                class_imbalance_ratio, depth_measurement_availability,
                target_lead_times_supported, target_leakage_risks_found,
                summary_notes, missing_requirements_json, gap_report_json,
                assessed_by
            ) VALUES (
                now(), :gate, :pos,
                :neg, :seasons,
                :imbalance, :depth_avail,
                :lead_times, :leakage,
                :notes, :missing, :gap,
                'PHASE25A_AUTOMATED_AUDITOR'
            );
        """)
        params = {
            "gate": verdict.readiness_gate,
            "pos": verdict.usable_positive_events,
            "neg": verdict.usable_negative_observations,
            "seasons": verdict.monsoon_seasons_covered,
            "imbalance": verdict.class_imbalance_ratio,
            "depth_avail": verdict.depth_measurement_availability,
            "lead_times": ", ".join(verdict.target_lead_times_supported),
            "leakage": verdict.target_leakage_risks_found,
            "notes": verdict.summary_notes,
            "missing": json.dumps(verdict.missing_requirements),
            "gap": json.dumps(verdict.actionable_gap_report)
        }
        with self.engine.begin() as conn:
            conn.execute(sql, params)
