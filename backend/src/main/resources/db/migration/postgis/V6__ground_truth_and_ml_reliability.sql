-- ==============================================================================
-- JalMarg Flyway Migration: V6__ground_truth_and_ml_reliability.sql
-- Phase 25C: Authentic Ground-Truth Observation System, Historical Event Reconstruction,
--            Municipal Negative Observation Workflow & Vision Reliability Registry
-- PostGIS Cluster: Port 5433 (jalmarg_twin)
-- Strictly Non-Destructive: Preserves all V1–V5 tables, citizen reports, and incidents
-- ==============================================================================

-- 1. Candidate & Acquired Historical Sources Registry
CREATE TABLE IF NOT EXISTS ml_historical_source_registry (
    id BIGSERIAL PRIMARY KEY,
    source_code VARCHAR(64) UNIQUE NOT NULL,
    source_title VARCHAR(256) NOT NULL,
    publisher VARCHAR(128) NOT NULL,
    official_url VARCHAR(512),
    date_coverage VARCHAR(128),
    geographic_coverage VARCHAR(256),
    licensing_status VARCHAR(64) NOT NULL DEFAULT 'GOI_OPEN_DATA',
    permitted_usage VARCHAR(128) NOT NULL DEFAULT 'RESEARCH_AND_MUNICIPAL_OPERATIONS',
    data_format VARCHAR(64) NOT NULL DEFAULT 'STRUCTURED_INCIDENT_RECORDS',
    access_method VARCHAR(64) NOT NULL DEFAULT 'DIRECT_AUTHENTICATED',
    verification_limitations TEXT,
    acquisition_status VARCHAR(32) NOT NULL DEFAULT 'ACQUIRED', -- ACQUIRED, BLOCKED, RESTRICTED, REVIEW_REQUIRED
    sha256_checksum VARCHAR(64),
    records_count INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ml_hist_source_code ON ml_historical_source_registry(source_code);
CREATE INDEX IF NOT EXISTS idx_ml_hist_source_status ON ml_historical_source_registry(acquisition_status);

-- 2. Structured Ground-Truth Observations Ledger
CREATE TABLE IF NOT EXISTS ml_ground_truth_observations (
    id BIGSERIAL PRIMARY KEY,
    observation_id VARCHAR(64) UNIQUE NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_description VARCHAR(512) NOT NULL,
    observation_timestamp TIMESTAMPTZ NOT NULL,
    observation_timezone VARCHAR(32) NOT NULL DEFAULT 'Asia/Kolkata',
    reported_at TIMESTAMPTZ,
    event_start_time TIMESTAMPTZ,
    event_end_time TIMESTAMPTZ,
    publication_timestamp TIMESTAMPTZ,
    waterlogging_status VARCHAR(32) NOT NULL, -- VERIFIED_WATERLOGGING, VERIFIED_NO_WATERLOGGING, REPORTED_UNVERIFIED, DISPUTED, UNKNOWN
    verification_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED', -- VERIFIED, UNVERIFIED, DISPUTED, REJECTED, UNDER_REVIEW
    verification_authority VARCHAR(128),
    verified_by_user VARCHAR(64),
    verified_at TIMESTAMPTZ,
    source_organization VARCHAR(128) NOT NULL,
    source_reference VARCHAR(512) NOT NULL,
    source_url VARCHAR(512),
    evidence_type VARCHAR(64) NOT NULL, -- FIELD_INSPECTION, OFFICIAL_TRAFFIC_BULLETIN, MUNICIPAL_DISASTER_LOG, CITIZEN_PHOTO_INSPECTED, UNVERIFIED_REPORT
    evidence_file_reference VARCHAR(512),
    license_code VARCHAR(64) NOT NULL DEFAULT 'GOI_OPEN_DATA',
    permitted_usage VARCHAR(128) NOT NULL DEFAULT 'RESEARCH_AND_MUNICIPAL_OPERATIONS',
    spatial_accuracy_m DOUBLE PRECISION NOT NULL DEFAULT 50.0,
    is_approximate_location BOOLEAN NOT NULL DEFAULT FALSE,
    temporal_accuracy_minutes INT NOT NULL DEFAULT 60,
    measured_water_depth_m DOUBLE PRECISION,
    depth_measurement_method VARCHAR(64), -- GAUGE_ROD, SENSOR_TELEMETRY, NONE
    verification_notes TEXT,
    provenance_classification VARCHAR(64) NOT NULL, -- OFFICIAL_MUNICIPAL, TRAFFIC_POLICE_BULLETIN, FIELD_INSPECTION, CITIZEN_VERIFIED, HISTORICAL_ARCHIVE
    alignment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- ALIGNED, PARTIAL, BLOCKED, PENDING
    rainfall_1h_mm DOUBLE PRECISION,
    rainfall_3h_mm DOUBLE PRECISION,
    rainfall_6h_mm DOUBLE PRECISION,
    rainfall_24h_mm DOUBLE PRECISION,
    antecedent_rainfall_index DOUBLE PRECISION,
    elevation_m DOUBLE PRECISION,
    slope_degrees DOUBLE PRECISION,
    relative_depression_m DOUBLE PRECISION,
    nearest_drain_distance_m DOUBLE PRECISION,
    corridor_name VARCHAR(128),
    related_report_id BIGINT,
    related_incident_id BIGINT,
    review_queue_status VARCHAR(32) NOT NULL DEFAULT 'RESOLVED', -- IN_QUEUE, UNDER_INVESTIGATION, RESOLVED
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add PostGIS spatial point geometry column if PostGIS extension is active
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        BEGIN
            ALTER TABLE ml_ground_truth_observations
                ADD COLUMN IF NOT EXISTS location_geom geometry(Point, 4326);
            CREATE INDEX IF NOT EXISTS idx_ml_gt_obs_geom ON ml_ground_truth_observations USING GIST(location_geom);
        EXCEPTION WHEN OTHERS THEN
            NULL;
        END;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ml_gt_obs_id ON ml_ground_truth_observations(observation_id);
CREATE INDEX IF NOT EXISTS idx_ml_gt_obs_status ON ml_ground_truth_observations(waterlogging_status);
CREATE INDEX IF NOT EXISTS idx_ml_gt_obs_vstatus ON ml_ground_truth_observations(verification_status);
CREATE INDEX IF NOT EXISTS idx_ml_gt_obs_timestamp ON ml_ground_truth_observations(observation_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_ml_gt_obs_queue ON ml_ground_truth_observations(review_queue_status);
CREATE INDEX IF NOT EXISTS idx_ml_gt_obs_corridor ON ml_ground_truth_observations(corridor_name);

-- 3. Municipal Ground-Truth Verification Audit Log
CREATE TABLE IF NOT EXISTS ml_verification_audit_log (
    id BIGSERIAL PRIMARY KEY,
    observation_id VARCHAR(64) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL, -- VERIFY, DISPUTE, REJECT, RECORD_NEGATIVE, QUEUE_FOR_REVIEW
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    client_ip VARCHAR(64),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ml_audit_obs_id ON ml_verification_audit_log(observation_id);
CREATE INDEX IF NOT EXISTS idx_ml_audit_actor ON ml_verification_audit_log(actor_username);
CREATE INDEX IF NOT EXISTS idx_ml_audit_decided_at ON ml_verification_audit_log(decided_at DESC);

-- 4. Computer Vision Reliability & Failure Audit Table
CREATE TABLE IF NOT EXISTS ml_cv_reliability_audit (
    id BIGSERIAL PRIMARY KEY,
    model_version VARCHAR(64) NOT NULL,
    audit_timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    total_images_audited INT NOT NULL,
    unique_images_count INT NOT NULL,
    exact_duplicates_count INT NOT NULL,
    cross_class_duplicates_count INT NOT NULL,
    folder_inferred_negatives_count INT NOT NULL,
    human_verified_negatives_count INT NOT NULL,
    license_status VARCHAR(64) NOT NULL,
    deployment_blocker_reason TEXT,
    abstention_lower_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.35,
    abstention_upper_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.65,
    abstention_policy_notes TEXT NOT NULL,
    failure_cases_json TEXT NOT NULL,
    reliability_verdict VARCHAR(64) NOT NULL,
    audited_by VARCHAR(64) NOT NULL DEFAULT 'SYSTEM_CV_AUDITOR'
);

CREATE INDEX IF NOT EXISTS idx_ml_cv_rel_model_time ON ml_cv_reliability_audit(model_version, audit_timestamp DESC);
