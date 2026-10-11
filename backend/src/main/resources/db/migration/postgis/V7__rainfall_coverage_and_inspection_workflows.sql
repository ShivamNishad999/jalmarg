-- ==============================================================================
-- JalMarg Flyway Migration: V7__rainfall_coverage_and_inspection_workflows.sql
-- Phase 25E-B: Authentic Historical Rainfall Acquisition Coverage Manifest,
--              Municipal Dry-Road Field Inspection Workflow & Calibrated Depth Registry
-- PostGIS Cluster: Port 5433 (jalmarg_twin)
-- Strictly Non-Destructive: Preserves all V1–V6 tables and citizen/municipal records
-- ==============================================================================

-- 1. Authentic Historical Rainfall Coverage Manifest Table
CREATE TABLE IF NOT EXISTS ml_rainfall_coverage_manifest (
    id BIGSERIAL PRIMARY KEY,
    period_code VARCHAR(64) UNIQUE NOT NULL,
    period_name VARCHAR(128) NOT NULL,
    source_dataset VARCHAR(64) NOT NULL DEFAULT 'OPEN_METEO_ERA5_LAND',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    corridors_sampled INT NOT NULL DEFAULT 11,
    records_count INT NOT NULL DEFAULT 0,
    missing_timestamps_count INT NOT NULL DEFAULT 0,
    spatial_resolution VARCHAR(64) NOT NULL DEFAULT '0.1° (~9 km ERA5-Land grid)',
    temporal_resolution VARCHAR(32) NOT NULL DEFAULT 'HOURLY',
    file_path VARCHAR(512),
    file_size_bytes BIGINT,
    sha256_checksum VARCHAR(64),
    acquisition_status VARCHAR(32) NOT NULL DEFAULT 'ACQUIRED_AND_VALIDATED', -- ACQUIRED_AND_VALIDATED, BLOCKED_OFFICIAL_ACCESS, MISSING
    quality_status VARCHAR(32) NOT NULL DEFAULT 'PASSED_QUALITY_CHECKS',
    quality_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ml_rain_cov_code ON ml_rainfall_coverage_manifest(period_code);
CREATE INDEX IF NOT EXISTS idx_ml_rain_cov_dates ON ml_rainfall_coverage_manifest(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_ml_rain_cov_status ON ml_rainfall_coverage_manifest(acquisition_status);

-- 2. Genuine Municipal Dry-Road Inspection Workflow Table
CREATE TABLE IF NOT EXISTS ml_municipal_dry_road_inspections (
    id BIGSERIAL PRIMARY KEY,
    inspection_code VARCHAR(64) UNIQUE NOT NULL,
    inspector_username VARCHAR(64) NOT NULL,
    inspector_name VARCHAR(128) NOT NULL,
    department VARCHAR(128) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_description VARCHAR(512) NOT NULL,
    corridor_name VARCHAR(128),
    inspection_timestamp TIMESTAMPTZ NOT NULL,
    gps_accuracy_m DOUBLE PRECISION,
    road_condition VARCHAR(64) NOT NULL DEFAULT 'DRY', -- DRY, DAMP_NO_PUDDLES, MINOR_MOISTURE
    water_present BOOLEAN NOT NULL DEFAULT FALSE,
    standing_water_depth_cm DOUBLE PRECISION DEFAULT 0.0,
    photo_evidence_url VARCHAR(512),
    evidence_file_reference VARCHAR(512),
    inspector_notes TEXT,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW', -- PENDING_REVIEW, VERIFIED_NEGATIVE, REJECTED
    reviewed_by VARCHAR(64),
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT,
    client_ip VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add spatial point geometry column if PostGIS extension is active
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        BEGIN
            ALTER TABLE ml_municipal_dry_road_inspections
                ADD COLUMN IF NOT EXISTS location_geom geometry(Point, 4326);
            CREATE INDEX IF NOT EXISTS idx_ml_dry_insp_geom ON ml_municipal_dry_road_inspections USING GIST(location_geom);
        EXCEPTION WHEN OTHERS THEN
            NULL;
        END;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ml_dry_insp_code ON ml_municipal_dry_road_inspections(inspection_code);
CREATE INDEX IF NOT EXISTS idx_ml_dry_insp_status ON ml_municipal_dry_road_inspections(review_status);
CREATE INDEX IF NOT EXISTS idx_ml_dry_insp_time ON ml_municipal_dry_road_inspections(inspection_timestamp DESC);

-- 3. Calibrated Physical Depth Measurement Registry Table
CREATE TABLE IF NOT EXISTS ml_calibrated_depth_measurements (
    id BIGSERIAL PRIMARY KEY,
    measurement_code VARCHAR(64) UNIQUE NOT NULL,
    observation_id VARCHAR(64),
    corridor_name VARCHAR(128) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    measurement_timestamp TIMESTAMPTZ NOT NULL,
    measurement_type VARCHAR(64) NOT NULL, -- CALIBRATED_PHYSICAL_MEASUREMENT, HUMAN_ESTIMATED, PHOTO_DERIVED, SIMULATED
    measured_depth_m DOUBLE PRECISION NOT NULL,
    measurement_uncertainty_m DOUBLE PRECISION,
    units VARCHAR(16) NOT NULL DEFAULT 'm',
    instrument_type VARCHAR(64), -- ULTRASONIC_STAGE_GAUGE, CALIBRATED_STAFF_GAUGE, SURVEYORS_LEVEL_ROD
    instrument_id VARCHAR(64),
    calibration_certificate_ref VARCHAR(256),
    source_document_title VARCHAR(256),
    source_document_url VARCHAR(512),
    evidence_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION', -- VERIFIED_CALIBRATED, UNVERIFIED_CLAIM, REJECTED
    reviewer_username VARCHAR(64),
    reviewed_at TIMESTAMPTZ,
    audit_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add spatial point geometry column if PostGIS extension is active
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        BEGIN
            ALTER TABLE ml_calibrated_depth_measurements
                ADD COLUMN IF NOT EXISTS location_geom geometry(Point, 4326);
            CREATE INDEX IF NOT EXISTS idx_ml_cal_depth_geom ON ml_calibrated_depth_measurements USING GIST(location_geom);
        EXCEPTION WHEN OTHERS THEN
            NULL;
        END;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ml_cal_depth_code ON ml_calibrated_depth_measurements(measurement_code);
CREATE INDEX IF NOT EXISTS idx_ml_cal_depth_status ON ml_calibrated_depth_measurements(evidence_status);
CREATE INDEX IF NOT EXISTS idx_ml_cal_depth_time ON ml_calibrated_depth_measurements(measurement_timestamp DESC);

-- Initial seed of authentic multi-period coverage metadata in ml_rainfall_coverage_manifest
INSERT INTO ml_rainfall_coverage_manifest (
    period_code, period_name, source_dataset, start_date, end_date,
    corridors_sampled, records_count, missing_timestamps_count,
    spatial_resolution, temporal_resolution, file_path, file_size_bytes,
    sha256_checksum, acquisition_status, quality_status, quality_notes
) VALUES
('2023_MONSOON_JUN', 'June 2023 Southwest Monsoon Onset', 'OPEN_METEO_ERA5_LAND', '2023-06-01', '2023-06-30', 11, 7920, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2023-06-01_2023-06-30.json', 4482016, 'a3b7a42fcfafd217b7a547ab9c09cb02c7a3e80390bc64f2e568c80c5b6173ef', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'Monsoon onset and pre-monsoon convective thunderstorm hourly reanalysis'),
('2023_MONSOON_JUL', 'July 2023 Historical Monsoon Deluge & Yamuna Surge', 'OPEN_METEO_ERA5_LAND', '2023-07-01', '2023-07-31', 11, 8184, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2023-07-01_2023-07-31.json', 4631434, '3806deaf6d68ea6c7bbf6960c1d6835a7065912301fa2d0392f58e401fa95ab9', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'July 9-13 monsoon deluge (153mm single-day) and Yamuna 208.66m flood surge reanalysis'),
('2023_MONSOON_AUG', 'August 2023 Active Monsoon Spells', 'OPEN_METEO_ERA5_LAND', '2023-08-01', '2023-08-31', 11, 8184, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2023-08-01_2023-08-31.json', 4631403, '73ef0788062fa9b61bb5e3cf7ae9883556094b9da85915d5ec3c6a4613c23947', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'Active monsoon spells across Delhi NCR monitored underpasses'),
('2023_MONSOON_SEP', 'September 2023 Late Monsoon Withdrawal', 'OPEN_METEO_ERA5_LAND', '2023-09-01', '2023-09-30', 11, 7920, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2023-09-01_2023-09-30.json', 4482007, '1374843d5b91b8d5a1532cb16b1ef75b3c586e300902c1d3202eb2a7ec8e4a77', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'Late monsoon withdrawal rainfall series across Delhi NCR'),
('2024_MONSOON_JUN', 'June 2024 Monsoon Cloudburst', 'OPEN_METEO_ERA5_LAND', '2024-06-01', '2024-06-30', 11, 7920, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2024-06-01_2024-06-30.json', 4482015, 'a7b630cb42ef01fbe025dd1f6874da5cf767597157a9446d7aa9275037d403ef', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'June 28 Safdarjung 228mm extreme rainfall cloudburst period'),
('2024_MONSOON_JUL', 'July 2024 Monsoon Waterlogging', 'OPEN_METEO_ERA5_LAND', '2024-07-01', '2024-07-31', 11, 8184, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2024-07-01_2024-07-31.json', 4631480, 'a61860117359556db1e2e987c2bfbc4566c7f551b9d40cb97eec43eb3c9f2d15', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'Full 11-corridor active monsoon and Old Rajinder Nagar waterlogging'),
('2024_MONSOON_AUG', 'August 2024 Monsoon Deluge', 'OPEN_METEO_ERA5_LAND', '2024-08-01', '2024-08-31', 11, 8184, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2024-08-01_2024-08-31.json', 4631415, '95b6b878c52e4eb9f06df1d19ee1b4ca107f9c5b2a0c0a969374ee7c8b05615d', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'August 1-2 widespread Delhi NCR waterlogging & Gazetted hotspots'),
('2024_MONSOON_SEP', 'September 2024 Late Monsoon', 'OPEN_METEO_ERA5_LAND', '2024-09-01', '2024-09-30', 11, 7920, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2024-09-01_2024-09-30.json', 4482057, '10564cf198d1a659ce501257404ae67f673752e2a86a63442ef5ba138e6eb8c1', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'Late monsoon depression rainfall across Delhi NCR'),
('2025_MONSOON', 'June-September 2025 Full Monsoon Season', 'OPEN_METEO_ERA5_LAND', '2025-06-01', '2025-09-30', 11, 32208, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2025-06-01_2025-09-30.json', 18226851, '96865ad915de59a5cc6ea31eb05193992095f5145bf86db6f8cfcb9f0141bb02', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'Full 4-month 2025 monsoon baseline series across 11 Delhi NCR corridors'),
('2026_MONSOON', 'June-September 2026 Completed Monsoon Season', 'OPEN_METEO_ERA5_LAND', '2026-06-01', '2026-09-30', 11, 32208, 0, '0.1° (~9 km ERA5-Land grid)', 'HOURLY', 'ml/artifacts/acquired_data/delhi_ncr_historical_rainfall_2026-06-01_2026-09-30.json', 18226879, 'd862c8f499a13808b2512eb92361665e71fc9e4a3bce495c6439eb4cbf8b84fe', 'ACQUIRED_AND_VALIDATED', 'PASSED_QUALITY_CHECKS', 'Most recent completed monsoon season across Delhi NCR')
ON CONFLICT (period_code) DO NOTHING;
