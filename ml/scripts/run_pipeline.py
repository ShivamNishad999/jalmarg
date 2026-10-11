#!/usr/bin/env python3
"""JalMarg Phase 25A Master Ingestion, Validation & Readiness Pipeline

Executes authentic dataset acquisition, data quality auditing, feature generation,
and training readiness evaluation without fabricating labels or model metrics.
"""

import sys
from pathlib import Path

# Initialize bootstrap paths
REPO_ROOT = Path(__file__).resolve().parents[2]
ML_ROOT = REPO_ROOT / "ml"
SRC_DIR = ML_ROOT / "src"
VENV_PACKAGES = ML_ROOT / ".venv" / "Lib" / "site-packages"

if str(VENV_PACKAGES) not in sys.path and VENV_PACKAGES.exists():
    sys.path.insert(0, str(VENV_PACKAGES))
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

import json
import yaml
from datetime import datetime, timezone

from jalmarg_ml.acquisition.copernicus_dem import CopernicusDemAdapter
from jalmarg_ml.acquisition.open_meteo_historical import OpenMeteoHistoricalAdapter
from jalmarg_ml.acquisition.imd_rainfall import ImdRainfallAdapter
from jalmarg_ml.acquisition.era5_land import Era5LandDirectAdapter
from jalmarg_ml.acquisition.municipal_events import MunicipalEventsAdapter
from jalmarg_ml.validation.quality import DataQualityValidator
from jalmarg_ml.validation.manifest import AcquisitionManifest, DatasetManifestEntry
from jalmarg_ml.features.feature_builder import FeatureBuilder
from jalmarg_ml.labeling.schema import GroundTruthSample
from jalmarg_ml.labeling.readiness_gate import TrainingReadinessEvaluator
from jalmarg_ml.storage.db import get_db_engine
from jalmarg_ml.integration.sync_metadata import MetadataSynchronizer


def load_yaml(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def main():
    print("=" * 80)
    print("JALMARG PHASE 25A: DATASET ACQUISITION & ML FOUNDATION PIPELINE")
    print("=" * 80)

    configs_dir = ML_ROOT / "configs"
    sources_cfg = load_yaml(configs_dir / "sources.yaml")["sources"]
    scope_cfg = load_yaml(configs_dir / "delhi_ncr.yaml")
    pipeline_cfg = load_yaml(configs_dir / "pipeline.yaml")

    artifacts_dir = ML_ROOT / "artifacts"
    data_dir = artifacts_dir / "acquired_data"
    manifests_dir = artifacts_dir / "manifests"
    data_dir.mkdir(parents=True, exist_ok=True)
    manifests_dir.mkdir(parents=True, exist_ok=True)

    db_engine = None
    try:
        db_engine = get_db_engine()
        print("Connected successfully to JalMarg PostGIS database (Port 5433).")
    except Exception as e:
        print(f"Warning: Database connection failed: {e}")

    validator = DataQualityValidator(pipeline_cfg)
    manifest_entries = []
    sync = MetadataSynchronizer(db_engine) if db_engine else None

    # -------------------------------------------------------------------------
    # 1. Copernicus Global DEM GLO-30 GeoTIFF Verification
    # -------------------------------------------------------------------------
    print("\n[1/5] Auditing Copernicus Global DEM (GLO-30 COG)...")
    dem_adapter = CopernicusDemAdapter(sources_cfg["copernicus_dem"], REPO_ROOT)
    dem_result = dem_adapter.acquire()
    print(f"  -> Status: {dem_result.status.value}")
    print(f"  -> Records/Pixels: {dem_result.records_count:,}")
    print(f"  -> File: {dem_result.file_path}")
    print(f"  -> SHA-256: {dem_result.sha256_checksum[:16]}...")
    if dem_result.metadata and sync:
        sync.sync_dataset_metadata(dem_result.metadata)
        sync.log_acquisition_run("COP_DEM_GLO30", "SUCCESS", dem_result.records_count, 200, notes=dem_result.notes)

    manifest_entries.append(DatasetManifestEntry(
        dataset_code="COP_DEM_GLO30",
        source_name="Copernicus DEM GLO-30 COG",
        provenance_type="OBSERVED",
        verification_status=dem_result.status,
        file_path=dem_result.file_path or "",
        file_size_bytes=dem_result.file_size_bytes or 0,
        sha256_checksum=dem_result.sha256_checksum or "",
        records_count=dem_result.records_count,
        notes=dem_result.notes or ""
    ))

    # -------------------------------------------------------------------------
    # 2. Open-Meteo Historical Weather Archive Acquisition
    # -------------------------------------------------------------------------
    print("\n[2/5] Acquiring Authentic Open-Meteo Historical Weather Reanalysis...")
    meteo_adapter = OpenMeteoHistoricalAdapter(sources_cfg["open_meteo_archive"], data_dir)
    corridors = scope_cfg["monitored_corridors"][:3]
    corridor_inputs = [{"name": c["name"], "lat": c["latitude"], "lng": c["longitude"]} for c in corridors]

    meteo_result = meteo_adapter.acquire(
        corridors=corridor_inputs,
        start_date="2024-07-01",
        end_date="2024-07-31"
    )
    print(f"  -> Status: {meteo_result.status.value}")
    print(f"  -> Records Acquired: {meteo_result.records_count}")
    print(f"  -> Output: {meteo_result.file_path}")
    print(f"  -> SHA-256: {meteo_result.sha256_checksum[:16]}...")

    # Validate acquired rainfall dataset
    acquired_rainfall = []
    if meteo_result.file_path and Path(meteo_result.file_path).exists():
        with open(meteo_result.file_path, "r", encoding="utf-8") as f:
            acquired_rainfall = json.load(f)

    rain_report = validator.validate_rainfall_timeseries(
        dataset_code="OPEN_METEO_ARCHIVE",
        records=acquired_rainfall
    )
    print(f"  -> Quality Audit Score: {rain_report.quality_score}/100 ({rain_report.verdict})")

    if meteo_result.metadata and sync:
        sync.sync_dataset_metadata(meteo_result.metadata)
        sync.log_acquisition_run("OPEN_METEO_ARCHIVE", "SUCCESS", meteo_result.records_count, 200, notes=meteo_result.notes)
        sync.record_quality_report(rain_report)

    manifest_entries.append(DatasetManifestEntry(
        dataset_code="OPEN_METEO_ARCHIVE",
        source_name="Open-Meteo Historical Weather Archive",
        provenance_type="REANALYSIS",
        verification_status=meteo_result.status,
        file_path=meteo_result.file_path or "",
        file_size_bytes=meteo_result.file_size_bytes or 0,
        sha256_checksum=meteo_result.sha256_checksum or "",
        records_count=meteo_result.records_count,
        notes=meteo_result.notes or ""
    ))

    # -------------------------------------------------------------------------
    # 3. IMD & ERA5-Land Blocked Source Auditing
    # -------------------------------------------------------------------------
    print("\n[3/5] Auditing IMD & Copernicus CDS ERA5-Land Direct Endpoints...")
    imd_adapter = ImdRainfallAdapter(sources_cfg["imd_ground_stations"])
    imd_result = imd_adapter.acquire()
    print(f"  -> IMD AWS Status: {imd_result.status.value} ({imd_result.notes[:75]}...)")
    if imd_result.metadata and sync:
        sync.sync_dataset_metadata(imd_result.metadata)
        sync.log_acquisition_run("IMD_OBSERVED_RAINFALL", "BLOCKED", 0, 401, error_message=imd_result.error_message, notes=imd_result.notes)

    manifest_entries.append(DatasetManifestEntry(
        dataset_code="IMD_OBSERVED_RAINFALL",
        source_name="IMD Ground Station Rainfall Observations",
        provenance_type="OBSERVED",
        verification_status=imd_result.status,
        notes=imd_result.notes or ""
    ))

    era5_adapter = Era5LandDirectAdapter(sources_cfg["era5_land_direct"])
    era5_result = era5_adapter.acquire()
    print(f"  -> ERA5-Land Direct CDS Status: {era5_result.status.value} ({era5_result.notes[:75]}...)")
    if era5_result.metadata and sync:
        sync.sync_dataset_metadata(era5_result.metadata)
        sync.log_acquisition_run("ERA5_LAND_CDS", "BLOCKED", 0, 401, error_message=era5_result.error_message, notes=era5_result.notes)

    manifest_entries.append(DatasetManifestEntry(
        dataset_code="ERA5_LAND_CDS",
        source_name="Copernicus ERA5-Land Direct CDS API",
        provenance_type="REANALYSIS",
        verification_status=era5_result.status,
        notes=era5_result.notes or ""
    ))

    # -------------------------------------------------------------------------
    # 4. JalMarg Municipal Ground-Truth Events Audit
    # -------------------------------------------------------------------------
    print("\n[4/5] Auditing PostGIS Municipal Ground-Truth Records (Port 5433)...")
    muni_adapter = MunicipalEventsAdapter(sources_cfg["jalmarg_twin_db"], db_engine)
    muni_result = muni_adapter.acquire()
    print(f"  -> Status: {muni_result.status.value}")
    print(f"  -> {muni_result.notes}")

    samples = []
    if db_engine:
        raw_gt = muni_adapter.query_ground_truth_records()
        for r in raw_gt["verified_reports"]:
            samples.append(GroundTruthSample.from_report(r))
        for r in raw_gt["rejected_reports"]:
            samples.append(GroundTruthSample.from_report(r))
        for r in raw_gt["unverified_reports"]:
            samples.append(GroundTruthSample.from_report(r))

    if muni_result.metadata and sync:
        sync.sync_dataset_metadata(muni_result.metadata)
        sync.log_acquisition_run("JALMARG_TWIN_MUNICIPAL", "SUCCESS", muni_result.records_count, 200, notes=muni_result.notes)

    manifest_entries.append(DatasetManifestEntry(
        dataset_code="JALMARG_TWIN_MUNICIPAL",
        source_name="JalMarg PostGIS Municipal Records",
        provenance_type="OBSERVED",
        verification_status=muni_result.status,
        records_count=muni_result.records_count,
        notes=muni_result.notes or ""
    ))

    # -------------------------------------------------------------------------
    # 5. Feature Engineering Demonstration & Leakage Guard Check
    # -------------------------------------------------------------------------
    print("\n[5/5] Computing Geospatial Features & Testing Leakage Guards...")
    builder = FeatureBuilder()
    demo_t0 = datetime(2024, 7, 15, 12, 0, 0, tzinfo=timezone.utc)
    sample_feat = builder.build_sample_features(
        latitude=28.6347,
        longitude=77.2263,
        prediction_timestamp=demo_t0,
        rainfall_observations=acquired_rainfall,
        elevation_m=214.2,
        neighborhood_elevations=[218.0, 219.5, 217.2, 216.8],
        slope_degrees=1.8
    )
    print(f"  -> Sample Feature Vector for Minto Bridge at {demo_t0.isoformat()}:")
    print(f"     * rainfall_1h_mm: {sample_feat['rainfall_1h_mm']}")
    print(f"     * rainfall_3h_mm: {sample_feat['rainfall_3h_mm']}")
    print(f"     * rainfall_6h_mm: {sample_feat['rainfall_6h_mm']}")
    print(f"     * antecedent_index: {sample_feat['antecedent_index']}")
    print(f"     * relative_elevation_m: {sample_feat['relative_elevation_m']}")
    print(f"     * depression_index: {sample_feat['topographic_depression_index']}")

    # -------------------------------------------------------------------------
    # Training Readiness Gate Evaluation
    # -------------------------------------------------------------------------
    print("\n" + "=" * 80)
    print("TRAINING READINESS DECISION GATE ASSESSMENT")
    print("=" * 80)
    evaluator = TrainingReadinessEvaluator(pipeline_cfg.get("training_readiness_gate_criteria"))
    verdict = evaluator.evaluate(
        samples=samples,
        distinct_monsoon_seasons=1,
        rainfall_data_accessible=True
    )

    print(f"Readiness Gate Verdict: {verdict.readiness_gate}")
    print(f"Usable Verified Positive Events: {verdict.usable_positive_events}")
    print(f"Usable Verified Negative Observations: {verdict.usable_negative_observations}")
    print(f"Monsoon Seasons Covered: {verdict.monsoon_seasons_covered}")
    print(f"Class Imbalance: {verdict.class_imbalance_ratio}")
    print(f"Measured Depth Status: {verdict.depth_measurement_availability}")
    print(f"\nSummary Notes:\n  {verdict.summary_notes}")

    if verdict.missing_requirements:
        print("\nMissing Requirements / Blockers:")
        for r in verdict.missing_requirements:
            print(f"  [!] {r}")

    if sync:
        sync.record_training_readiness(verdict)
        print("\nRecorded training readiness verdict into PostGIS ml_training_readiness.")

    # Save manifest
    manifest = AcquisitionManifest(
        total_datasets=len(manifest_entries),
        verified_datasets_count=sum(1 for e in manifest_entries if e.verification_status == "VERIFIED"),
        blocked_datasets_count=sum(1 for e in manifest_entries if e.verification_status == "BLOCKED"),
        entries=manifest_entries
    )
    manifest_path = manifests_dir / "acquisition_manifest.json"
    manifest.save(manifest_path)
    print(f"\nPersisted Acquisition Manifest -> {manifest_path}")
    print("=" * 80)
    print("PIPELINE EXECUTION COMPLETED SUCCESSFULLY")
    print("=" * 80)


if __name__ == "__main__":
    main()
