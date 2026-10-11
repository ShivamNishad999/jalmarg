# JALMARG PHASE 18 — DATA MODEL & DATABASE SPECIFICATION
## Entity Relational Architecture & PostGIS Schema

**Project:** JalMarg — Delhi NCR Urban Waterlogging Intelligence  
**Phase:** 18 — Municipal Flood Digital Twin Foundation  
**Spatial Reference System:** WGS 84 (EPSG:4326) / Cartesian Geography  

---

### 1. Entity-Relationship Overview

```
+--------------------------+          1:N         +--------------------------+
|      MunicipalZone       |--------------------->|       RoadSegment        |
|  - id                    |                      |  - id                    |
|  - zoneCode (UNIQUE)     |                      |  - segmentCode (UNIQUE)  |
|  - zoneName              |                      |  - roadName              |
|  - municipalityName      |                      |  - roadType              |
|  - baselineVulnScore     |                      |  - isUnderpass (BOOLEAN) |
|  - boundaryType          |                      |  - startLat, startLng    |
|  - centerLat, centerLng  |                      |  - endLat, endLng        |
+--------------------------+                      +--------------------------+
             |                                                 |
             | 1:N                                             | 1:N
             v                                                 v
+--------------------------+                      +--------------------------+
|       TwinSnapshot       |                      |    MunicipalIncident     |
|  - id                    |                      |  - id                    |
|  - snapshotType          |                      |  - incidentNumber (UNQ)  |
|  - snapshotTimestamp     |                      |  - status                |
|  - riskScore, riskLevel  |                      |  - severity              |
|  - observedRainfallMm    |                      |  - priorityScore         |
|  - payloadChecksum       |                      |  - assignedUnit          |
+--------------------------+                      +--------------------------+
                                                               |
                                                               | 1:N
                                                               v
                                                  +--------------------------+
                                                  |        AuditEvent        |
                                                  |  - id                    |
                                                  |  - actionType            |
                                                  |  - actorUsername         |
                                                  |  - targetEntity          |
                                                  |  - targetEntityId        |
                                                  |  - previousStateJson     |
                                                  |  - newStateJson          |
                                                  |  - eventTimestamp        |
                                                  +--------------------------+
```

---

### 2. Entity Descriptions & Field Specifications

#### 2.1 `MunicipalZone` (Table: `municipal_zones`)
Represents an administrative or planning municipal boundary within Delhi NCR.
- `id` (BIGINT, PK): Auto-increment primary key.
- `zone_code` (VARCHAR(50), UNIQUE): Unique zone identifier (e.g. `NZ-CIVIL-LINES`, `SZ-HAUZ-KHAS`).
- `zone_name` (VARCHAR(150)): Human-readable name.
- `municipality_name` (VARCHAR(150)): Municipal body (e.g. `MCD (North Delhi)`, `MCD (South Delhi)`).
- `baseline_vulnerability_score` (DOUBLE): Calibrated vulnerability factor ($0.0 \dots 1.0$).
- `boundary_type` (VARCHAR(50)): `VERIFIED_OFFICIAL`, `PLANNING_ESTIMATE`, `UNAVAILABLE`.
- `center_lat`, `center_lng` (DOUBLE): Centroid coordinates in EPSG:4326.
- `polygon_wkt` (TEXT, Nullable): Valid WKT Polygon string.
- `geom` (GEOMETRY(Polygon, 4326), PostGIS profile only): PostGIS spatial polygon geometry.

#### 2.2 `RoadSegment` (Table: `road_segments`)
Monitored transport corridors and drainage-sensitive road segments.
- `id` (BIGINT, PK): Auto-increment primary key.
- `segment_code` (VARCHAR(60), UNIQUE): Segment identifier (e.g. `ROAD-MINTO-BRIDGE-01`).
- `road_name` (VARCHAR(150)): Road name.
- `road_type` (VARCHAR(40)): `NATIONAL_HIGHWAY`, `ARTERIAL`, `COLLECTOR`, `LOCAL_ACCESS`.
- `zone_id` (BIGINT, FK): Reference to `municipal_zones(id)`.
- `is_underpass` (BOOLEAN): Flag indicating depressed topography / underpass sump hazard.
- `start_lat`, `start_lng`, `end_lat`, `end_lng` (DOUBLE): Coordinate endpoints.
- `length_meters` (DOUBLE, Nullable): Physical road length in meters.
- `elevation_meters` (DOUBLE, Nullable): Contextual Copernicus DEM elevation.
- `geom` (GEOMETRY(LineString, 4326), PostGIS profile only): PostGIS spatial linestring.

#### 2.3 `DrainageAsset` (Table: `drainage_assets`)
Physical or planned municipal drainage assets (pump stations, outfalls).
- `id` (BIGINT, PK): Primary key.
- `asset_code` (VARCHAR(60), UNIQUE): Asset identifier.
- `asset_type` (VARCHAR(40)): `PUMP_STATION`, `OUTFALL`, `STORM_DRAIN_TRUNK`, `CULVERT`.
- `operational_status` (VARCHAR(40)): `OPERATIONAL`, `DEGRADED`, `OFFLINE`, `UNVERIFIED`.
- `capacity_lpm` (DOUBLE, Nullable): Pumping capacity in Litres Per Minute (never defaulted to 0 if unknown).
- `latitude`, `longitude` (DOUBLE): Physical location.

#### 2.4 `DataSourceRegistry` (Table: `data_source_registry`)
Provenance ledger storing lineage and verification status for all ingested data.
- `id` (BIGINT, PK): Primary key.
- `source_code` (VARCHAR(60), UNIQUE): Machine code (e.g. `OPEN_METEO_FORECAST`).
- `source_name` (VARCHAR(150)): Display name.
- `provider` (VARCHAR(150)): Organization providing data.
- `classification` (VARCHAR(50)): `VERIFIED_OBSERVATION`, `PROVIDER_FORECAST`, `DERIVED_ESTIMATE`, `USER_REPORTED`, `SIMULATED`, `UNAVAILABLE`.
- `coordinate_reference_system` (VARCHAR(50)): Coordinate system (e.g. `EPSG:4326 (WGS84)`).
- `license_type` (VARCHAR(100)): Permitted license (e.g. `Open Database License (ODbL)`).
- `verification_status` (VARCHAR(50)): `VERIFIED`, `UNVERIFIED_THIRD_PARTY`, `DERIVED_CALCULATED`.

#### 2.5 `TwinSnapshot` (Table: `twin_snapshots`)
Immutable published state snapshots for auditability.
- `id` (BIGINT, PK): Primary key.
- `snapshot_type` (VARCHAR(30)): `CURRENT`, `HISTORICAL`, `SIMULATED`.
- `snapshot_timestamp` (TIMESTAMP): Moment of state capture.
- `zone_id` (BIGINT, Nullable): Associated zone or NULL for NCR regional scope.
- `risk_score` (DOUBLE): JalMarg risk index ($0.0 \dots 1.0$).
- `risk_level` (VARCHAR(30)): `LOW`, `MODERATE`, `HIGH`, `CRITICAL`, `UNKNOWN`.
- `risk_status` (VARCHAR(30)): `NORMAL`, `MONITORING`, `EVALUATING`, `ALERT`.
- `observed_rainfall_mm` (DOUBLE, Nullable): Ground-observed precipitation (NULL if unmeasured).
- `forecast_rainfall_mm` (DOUBLE, Nullable): Open-Meteo 6h forecast precipitation.
- `freshness_minutes` (BIGINT): Time elapsed since freshest contributing telemetry.
- `model_version` (VARCHAR(60)): Engine model tag.
- `payload_checksum` (VARCHAR(64)): SHA-256 integrity hash of snapshot state.

#### 2.6 `ScenarioRun` (Table: `scenario_runs`)
Isolated what-if simulation execution logs.
- `id` (BIGINT, PK): Primary key.
- `scenario_code` (VARCHAR(60), UNIQUE): Generated identifier (e.g. `SCEN-20261010-022439-01`).
- `scenario_name` (VARCHAR(150)): Title of scenario.
- `scenario_type` (VARCHAR(50)): `RAINFALL_STRESS`, `DRAINAGE_FAILURE`, `COMPOUND_EXTREME`.
- `rainfall_mm` (DOUBLE): Hypothetical total rainfall.
- `rainfall_intensity_mm_per_hour` (DOUBLE): Precipitation rate.
- `duration_hours` (DOUBLE): Scenario temporal window.
- `drainage_capacity_factor` (DOUBLE): Assumed drainage factor ($0.0 \dots 1.0$).
- `baseline_risk_score`, `simulated_risk_score`, `risk_delta_score` (DOUBLE): Computed risk comparison.
- `is_simulation` (BOOLEAN): Always `true` to guarantee non-contamination of live records.

#### 2.7 `MunicipalIncident` (Table: `municipal_incidents`)
Operational incident records managed through the municipal command dashboard.
- `id` (BIGINT, PK): Primary key.
- `incident_number` (VARCHAR(60), UNIQUE): Formatted code (e.g. `INC-20261010-022440-01`).
- `title`, `description` (TEXT): Incident summary and field report.
- `incident_type` (VARCHAR(50)): `SURFACE_FLOODING`, `UNDERPASS_INUNDATION`, `DRAIN_OVERFLOW`, `ROAD_COLLAPSE`.
- `status` (VARCHAR(40)): `NEW`, `TRIAGED`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `VERIFIED_CLOSED`, `REJECTED`.
- `severity` (VARCHAR(30)): `LOW`, `MODERATE`, `HIGH`, `SEVERE`.
- `priority_score` (DOUBLE): Evidence-ranked dispatch priority ($0.0 \dots 10.0$).
- `latitude`, `longitude` (DOUBLE): Incident location.
- `water_depth_meters` (DOUBLE, Nullable): Measured or estimated water depth (NULL if unknown).
- `road_blockage` (BOOLEAN): Traffic obstruction flag.
- `assigned_unit`, `equipment_details` (VARCHAR(150)): Dispatched team and asset description.
- `resolution_notes` (TEXT): Closing justification.

#### 2.8 `AuditEvent` (Table: `audit_events`)
Tamper-evident append-only ledger of municipal operations.
- `id` (BIGINT, PK): Primary key.
- `event_timestamp` (TIMESTAMP): Time of action.
- `actor_username` (VARCHAR(100)): Acting user or system daemon.
- `action_type` (VARCHAR(60)): `INCIDENT_CREATED`, `STATUS_CHANGE`, `RESOURCE_ASSIGNMENT`, `SCENARIO_RUN`.
- `target_entity` (VARCHAR(60)): Target table/model (e.g. `MunicipalIncident`).
- `target_entity_id` (VARCHAR(60)): Target record ID.
- `previous_state_json`, `new_state_json` (TEXT): State delta capture.

---

### 3. PostGIS DDL Migration

Located in `backend/src/main/resources/db/migration/postgis/V1__municipal_digital_twin_postgis.sql`.
Includes:
- PostGIS extension initialization (`CREATE EXTENSION IF NOT EXISTS postgis;`).
- Spatial index creation (`CREATE INDEX idx_zones_geom ON municipal_zones USING GIST (geom);`).
- Spatial index creation (`CREATE INDEX idx_roads_geom ON road_segments USING GIST (geom);`).
