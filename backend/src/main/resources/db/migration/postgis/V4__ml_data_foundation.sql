-- ==============================================================================
-- JalMarg Flyway Migration: V4__ml_data_foundation.sql
-- Phase 25A: Machine Learning Data Foundation, Provenance Registry & Readiness Gates
-- PostGIS Cluster: Port 5433 (jalmarg_twin)
-- Strictly Non-Destructive: Preserves all V1, V2, V3 tables, citizen reports, and incidents
-- ==============================================================================

-- 1. Machine Learning Dataset Registry
CREATE TABLE IF NOT EXISTS ml_dataset_registry (
    id BIGSERIAL PRIMARY KEY,
    dataset_code VARCHAR(64) UNIQUE NOT NULL,
    dataset_name VARCHAR(128) NOT NULL,
    source_organization VARCHAR(128) NOT NULL,
    official_source_url VARCHAR(512),
    provenance_type VARCHAR(32) NOT NULL, -- OBSERVED, FORECAST, HISTORICAL_PRIOR, REANALYSIS, SIMULATED, UNKNOWN
    spatial_coverage VARCHAR(256),
    temporal_coverage VARCHAR(256),
    spatial_resolution VARCHAR(64),
    temporal_resolution VARCHAR(64),
    crs VARCHAR(64),
    units VARCHAR(64),
    license VARCHAR(128),
    local_storage_path VARCHAR(512),
    file_size_bytes BIGINT,
    sha256_checksum VARCHAR(64),
    validation_status VARCHAR(32) NOT NULL DEFAULT 'NOT_TESTED', -- VERIFIED, PARTIAL, BLOCKED, NOT_TESTED
    known_limitations TEXT,
    last_acquired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ml_dataset_code ON ml_dataset_registry(dataset_code);
CREATE INDEX IF NOT EXISTS idx_ml_dataset_status ON ml_dataset_registry(validation_status);

-- 2. Machine Learning Acquisition Runs
CREATE TABLE IF NOT EXISTS ml_acquisition_runs (
    id BIGSERIAL PRIMARY KEY,
    dataset_code VARCHAR(64) NOT NULL REFERENCES ml_dataset_registry(dataset_code) ON DELETE CASCADE,
    run_timestamp TIMESTAMPTZ DEFAULT now() NOT NULL,
    status VARCHAR(32) NOT NULL, -- SUCCESS, PARTIAL, BLOCKED, FAILED
    records_acquired INT DEFAULT 0 NOT NULL,
    http_status_code INT,
    error_message TEXT,
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_ml_runs_dataset_time ON ml_acquisition_runs(dataset_code, run_timestamp DESC);

-- 3. Machine Learning Data Quality Reports
CREATE TABLE IF NOT EXISTS ml_data_quality_reports (
    id BIGSERIAL PRIMARY KEY,
    dataset_code VARCHAR(64) NOT NULL REFERENCES ml_dataset_registry(dataset_code) ON DELETE CASCADE,
    report_timestamp TIMESTAMPTZ DEFAULT now() NOT NULL,
    total_records_checked INT NOT NULL,
    missing_timestamps_count INT DEFAULT 0 NOT NULL,
    coordinate_anomalies_count INT DEFAULT 0 NOT NULL,
    implausible_values_count INT DEFAULT 0 NOT NULL,
    duplicates_count INT DEFAULT 0 NOT NULL,
    nodata_contamination_count INT DEFAULT 0 NOT NULL,
    quality_score INT NOT NULL, -- 0 to 100
    quality_verdict VARCHAR(32) NOT NULL, -- PASSED, WARNING, FAILED
    details_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_ml_quality_dataset_time ON ml_data_quality_reports(dataset_code, report_timestamp DESC);

-- 4. Machine Learning Training Readiness Assessments
CREATE TABLE IF NOT EXISTS ml_training_readiness (
    id BIGSERIAL PRIMARY KEY,
    assessment_timestamp TIMESTAMPTZ DEFAULT now() NOT NULL,
    readiness_gate VARCHAR(64) NOT NULL, -- READY_FOR_SUPERVISED_TRAINING, PARTIALLY_READY, BLOCKED_INSUFFICIENT_GROUND_TRUTH, BLOCKED_DATA_ACCESS, BLOCKED_DATA_QUALITY
    usable_positive_events INT NOT NULL,
    usable_negative_observations INT NOT NULL,
    monsoon_seasons_covered INT NOT NULL,
    class_imbalance_ratio VARCHAR(64),
    depth_measurement_availability VARCHAR(64),
    target_lead_times_supported VARCHAR(64) DEFAULT '1h, 3h, 6h',
    target_leakage_risks_found INT DEFAULT 0 NOT NULL,
    summary_notes TEXT,
    missing_requirements_json TEXT,
    gap_report_json TEXT,
    assessed_by VARCHAR(64) DEFAULT 'SYSTEM_AUDITOR'
);

CREATE INDEX IF NOT EXISTS idx_ml_readiness_time ON ml_training_readiness(assessment_timestamp DESC);
