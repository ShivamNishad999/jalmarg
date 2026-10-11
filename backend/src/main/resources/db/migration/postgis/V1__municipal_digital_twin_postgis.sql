-- ==================================================================================
-- JALMARG MUNICIPAL FLOOD DIGITAL TWIN — POSTGRESQL + POSTGIS SCHEMA (SRID 4326)
-- Profile: prod-postgis / staging-postgis
-- ==================================================================================

-- 1. Enable PostGIS extension (PostgreSQL Superuser required)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. Data Source Registry
CREATE TABLE IF NOT EXISTS twin_data_source_registry (
    id BIGSERIAL PRIMARY KEY,
    source_code VARCHAR(64) NOT NULL UNIQUE,
    source_name VARCHAR(128) NOT NULL,
    provider VARCHAR(128) NOT NULL,
    original_url VARCHAR(256),
    geographic_coverage VARCHAR(128),
    crs VARCHAR(64),
    license VARCHAR(64),
    permitted_usage VARCHAR(128),
    source_classification VARCHAR(64) NOT NULL,
    verification_status VARCHAR(64) NOT NULL,
    update_frequency VARCHAR(64),
    notes VARCHAR(500),
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 3. Municipal Zones (Administrative & Drainage Catchments)
CREATE TABLE IF NOT EXISTS twin_municipal_zones (
    id BIGSERIAL PRIMARY KEY,
    zone_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    municipality_name VARCHAR(128) NOT NULL,
    boundary_type VARCHAR(64) NOT NULL,
    boundary_geojson TEXT,
    boundary_geom GEOMETRY(MultiPolygon, 4326),
    centroid_lat DOUBLE PRECISION NOT NULL,
    centroid_lng DOUBLE PRECISION NOT NULL,
    area_sq_km DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    population_estimate DOUBLE PRECISION,
    drainage_network_status VARCHAR(64) NOT NULL DEFAULT 'UNAVAILABLE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_delhi_zone_lat CHECK (centroid_lat BETWEEN 28.0 AND 29.2),
    CONSTRAINT chk_delhi_zone_lng CHECK (centroid_lng BETWEEN 76.5 AND 77.8)
);
CREATE INDEX IF NOT EXISTS idx_zone_geom ON twin_municipal_zones USING GIST (boundary_geom);

-- 4. Road Segments & Key Underpass Corridors
CREATE TABLE IF NOT EXISTS twin_road_segments (
    id BIGSERIAL PRIMARY KEY,
    segment_code VARCHAR(64) NOT NULL UNIQUE,
    road_name VARCHAR(128) NOT NULL,
    road_type VARCHAR(64) NOT NULL,
    start_lat DOUBLE PRECISION NOT NULL,
    start_lng DOUBLE PRECISION NOT NULL,
    end_lat DOUBLE PRECISION NOT NULL,
    end_lng DOUBLE PRECISION NOT NULL,
    geometry_geojson TEXT,
    geometry_geom GEOMETRY(LineString, 4326),
    is_underpass BOOLEAN NOT NULL DEFAULT FALSE,
    length_meters DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    elevation_min_meters DOUBLE PRECISION,
    elevation_max_meters DOUBLE PRECISION,
    zone_id BIGINT REFERENCES twin_municipal_zones(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_road_geom ON twin_road_segments USING GIST (geometry_geom);

-- 5. Stormwater Drainage Infrastructure
CREATE TABLE IF NOT EXISTS twin_drainage_assets (
    id BIGSERIAL PRIMARY KEY,
    asset_code VARCHAR(64) NOT NULL UNIQUE,
    asset_type VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    location_name VARCHAR(256),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_geom GEOMETRY(Point, 4326),
    zone_id BIGINT REFERENCES twin_municipal_zones(id) ON DELETE SET NULL,
    design_capacity_cusecs DOUBLE PRECISION,
    status VARCHAR(64) NOT NULL DEFAULT 'UNVERIFIED',
    last_inspected_at TIMESTAMP WITH TIME ZONE,
    is_telemetry_connected BOOLEAN NOT NULL DEFAULT FALSE,
    operational_notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_drainage_geom ON twin_drainage_assets USING GIST (location_geom);

-- 6. Environmental Observations & Telemetry
CREATE TABLE IF NOT EXISTS twin_environmental_observations (
    id BIGSERIAL PRIMARY KEY,
    observation_type VARCHAR(64) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_geom GEOMETRY(Point, 4326),
    measured_value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(32) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    data_source_id BIGINT REFERENCES twin_data_source_registry(id) ON DELETE SET NULL,
    source_classification VARCHAR(64) NOT NULL,
    data_quality VARCHAR(64) NOT NULL,
    raw_payload_json TEXT
);
CREATE INDEX IF NOT EXISTS idx_env_obs_timestamp ON twin_environmental_observations(observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_env_obs_geom ON twin_environmental_observations USING GIST (location_geom);

-- 7. Digital Twin Immutable State Snapshots
CREATE TABLE IF NOT EXISTS twin_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_type VARCHAR(64) NOT NULL,
    zone_id BIGINT REFERENCES twin_municipal_zones(id) ON DELETE SET NULL,
    zone_name VARCHAR(128),
    snapshot_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    overall_risk_score INTEGER NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    risk_status VARCHAR(64) NOT NULL,
    active_report_count INTEGER NOT NULL DEFAULT 0,
    active_incident_count INTEGER NOT NULL DEFAULT 0,
    rainfall_6h_mm DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    model_version VARCHAR(64) NOT NULL,
    contributing_evidence_json TEXT,
    missing_data_indicators_json TEXT,
    provenance_summary VARCHAR(500),
    uncertainty_disclaimer VARCHAR(500),
    immutable_hash VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_snapshot_type_ts ON twin_snapshots(snapshot_type, snapshot_timestamp DESC);

-- 8. What-If Scenario Simulations (Isolated)
CREATE TABLE IF NOT EXISTS twin_scenario_runs (
    id BIGSERIAL PRIMARY KEY,
    scenario_name VARCHAR(128) NOT NULL,
    run_type VARCHAR(64) NOT NULL DEFAULT 'WHAT_IF_SIMULATION',
    created_by VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    rainfall_total_mm DOUBLE PRECISION NOT NULL,
    rainfall_intensity_mm_per_hour DOUBLE PRECISION NOT NULL,
    duration_hours INTEGER NOT NULL DEFAULT 1,
    target_zone_id BIGINT,
    target_zone_name VARCHAR(128),
    assumed_drainage_capacity_percent DOUBLE PRECISION,
    road_closure_assumptions_json TEXT,
    resource_availability_assumptions_json TEXT,
    simulation_label VARCHAR(64) NOT NULL DEFAULT 'SIMULATION / TEST DATA',
    affected_roads_count INTEGER NOT NULL DEFAULT 0,
    baseline_risk_score INTEGER NOT NULL,
    simulated_risk_score INTEGER NOT NULL,
    delta_risk_score INTEGER NOT NULL,
    factor_breakdown_json TEXT,
    data_gaps_json TEXT,
    model_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED'
);

-- 9. Municipal Flood Incidents & Dispatch
CREATE TABLE IF NOT EXISTS twin_municipal_incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_number VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    incident_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'NEW',
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location_geom GEOMETRY(Point, 4326),
    location_name VARCHAR(256) NOT NULL,
    zone_id BIGINT REFERENCES twin_municipal_zones(id) ON DELETE SET NULL,
    assigned_department VARCHAR(128),
    assigned_team VARCHAR(128),
    priority_score INTEGER NOT NULL DEFAULT 0,
    related_report_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes VARCHAR(1000)
);
CREATE INDEX IF NOT EXISTS idx_incident_status_prio ON twin_municipal_incidents(status, priority_score DESC);

-- 10. Emergency Field Response Resources
CREATE TABLE IF NOT EXISTS twin_response_resources (
    id BIGSERIAL PRIMARY KEY,
    resource_code VARCHAR(64) NOT NULL UNIQUE,
    resource_type VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    current_latitude DOUBLE PRECISION NOT NULL,
    current_longitude DOUBLE PRECISION NOT NULL,
    current_geom GEOMETRY(Point, 4326),
    status VARCHAR(64) NOT NULL DEFAULT 'AVAILABLE',
    assigned_incident_id BIGINT REFERENCES twin_municipal_incidents(id) ON DELETE SET NULL,
    contact_info VARCHAR(128),
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 11. Immutable Audit Trail
CREATE TABLE IF NOT EXISTS twin_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    actor_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_entity VARCHAR(64) NOT NULL,
    target_entity_id VARCHAR(64),
    ip_address VARCHAR(64),
    details_json TEXT
);
CREATE INDEX IF NOT EXISTS idx_audit_event_ts ON twin_audit_events(event_timestamp DESC);
