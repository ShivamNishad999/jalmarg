# JALMARG — PHASE 20 AUDIT REPORT
## Digital Twin Data Integrity, Security Hardening & Production Readiness

**Project:** JalMarg — Delhi NCR Municipal Flood Digital Twin  
**Auditor / Roles:** Senior Java Spring Boot Architect, PostGIS Engineer, Geospatial Data Scientist, Security Auditor, QA Engineer  
**Date:** October 10, 2026  
**Primary Status:** **VERIFIED**

---

## 1. EXISTING ARCHITECTURE & ENVIRONMENT STATE

The JalMarg platform integrates a real-time citizen-reporting subsystem, an autonomous rule-based predictive flood engine, Copernicus DEM terrain modeling, and an isolated PostgreSQL 17 + PostGIS 3.6 Municipal Digital Twin.

```
+-----------------------------------------------------------------------------------+
|                            JalMarg Full Stack System                              |
|                                                                                   |
|  +---------------------------+       +-----------------------------------------+  |
|  |   React 18 + Leaflet UI   | <---> |        Spring Boot 3.3.5 Backend        |  |
|  |  (Vite PWA, Responsive)   |       |  (Profiles: default / postgis / prod)   |  |
|  +---------------------------+       +-----------------------------------------+  |
|                                                      |                            |
|                            +-------------------------+-------------------------+  |
|                            |                                                   |  |
|                            v (Port 5433)                                       v  |
|              +---------------------------+                       +------------+   |
|              | JalMarg PostgreSQL 17.11  |                       | Open-Meteo |   |
|              |  + PostGIS 3.6.2 Spatial  |                       | ECMWF IFS  |   |
|              |   Database: jalmarg_twin  |                       +------------+   |
|              +---------------------------+                                        |
+-----------------------------------------------------------------------------------+

[ISOLATED FROM]
  +-------------------------------------+
  | Protected CarSync PostgreSQL 17.11  |
  |  Database: carsync | Port: 5432     |
  |  Status: PROTECTED & UNTOUCHED      |
  +-------------------------------------+
```

---

## 2. DATABASE INTEGRITY AUDIT (PORT 5433 — `jalmarg_twin`)

A complete structural and referential audit was conducted on the isolated `jalmarg_twin` database:

### 2.1 Table Inventory & Live Row Counts
| Table Name | Schema | Row Count | Primary Key / Index | Status |
| :--- | :--- | :--- | :--- | :--- |
| `flyway_schema_history` | `public` | 1 | `flyway_schema_history_pk` | **VERIFIED** |
| `twin_data_source_registry`| `public` | 6 | `twin_data_source_registry_pkey`, `source_code UNIQUE` | **VERIFIED** |
| `twin_municipal_zones` | `public` | 1 | `twin_municipal_zones_pkey`, `zone_code UNIQUE` | **VERIFIED** |
| `twin_road_segments` | `public` | 1 | `twin_road_segments_pkey`, `segment_code UNIQUE` | **VERIFIED** |
| `twin_drainage_assets` | `public` | 1 | `twin_drainage_assets_pkey`, `asset_code UNIQUE` | **VERIFIED** |
| `twin_environmental_observations`| `public` | 0 | `twin_environmental_observations_pkey` | **VERIFIED** |
| `twin_snapshots` | `public` | 2 | `twin_snapshots_pkey` | **VERIFIED** |
| `twin_scenario_runs` | `public` | 1 | `twin_scenario_runs_pkey` | **VERIFIED** |
| `twin_municipal_incidents`| `public` | 1 | `twin_municipal_incidents_pkey`, `incident_number UNIQUE` | **VERIFIED** |
| `twin_response_resources`| `public` | 3 | `twin_response_resources_pkey`, `resource_code UNIQUE` | **VERIFIED** |
| `twin_audit_events` | `public` | 1 | `twin_audit_events_pkey` | **VERIFIED** |
| `reports` | `public` | 195 | `reports_pkey` | **VERIFIED** |
| `hotspots` | `public` | 20 | `hotspots_pkey` | **VERIFIED** |
| `subscriptions` | `public` | 0 | `subscriptions_pkey` | **VERIFIED** |
| `flood_risk_assessments` | `public` | 5 | `flood_risk_assessments_pkey` | **VERIFIED** |

### 2.2 Spatial Columns & SRID Validation
All spatial columns are strictly typed to **SRID 4326** (WGS 84 coordinate reference system):
* `twin_municipal_zones.boundary_geom`: `GEOMETRY(MultiPolygon, 4326)`
* `twin_road_segments.geometry_geom`: `GEOMETRY(LineString, 4326)`
* `twin_drainage_assets.location_geom`: `GEOMETRY(Point, 4326)`
* `twin_environmental_observations.location_geom`: `GEOMETRY(Point, 4326)`
* `twin_municipal_incidents.location_geom`: `GEOMETRY(Point, 4326)`
* `twin_response_resources.current_geom`: `GEOMETRY(Point, 4326)`

### 2.3 GiST Spatial Indexes
All spatial query paths are backed by PostgreSQL Generalized Search Tree (GiST) indexes:
* `idx_zone_geom` ON `twin_municipal_zones USING gist (boundary_geom)`
* `idx_road_geom` ON `twin_road_segments USING gist (geometry_geom)`
* `idx_drainage_geom` ON `twin_drainage_assets USING gist (location_geom)`
* `idx_env_obs_geom` ON `twin_environmental_observations USING gist (location_geom)`

### 2.4 Referential Integrity & Foreign Keys
All foreign key relationships enforce referential constraints with safe `ON DELETE SET NULL` policies:
* `twin_road_segments.zone_id` ➔ `twin_municipal_zones(id)`
* `twin_drainage_assets.zone_id` ➔ `twin_municipal_zones(id)`
* `twin_environmental_observations.data_source_id` ➔ `twin_data_source_registry(id)`
* `twin_snapshots.zone_id` ➔ `twin_municipal_zones(id)`
* `twin_municipal_incidents.zone_id` ➔ `twin_municipal_zones(id)`
* `twin_response_resources.assigned_incident_id` ➔ `twin_municipal_incidents(id)`
* Orphaned records check: **0 orphaned records detected**.
* Duplicate key check: **0 duplicates detected**.

---

## 3. DATASET PROVENANCE AUDIT & TRACEABILITY

Every dataset and record category has been audited and classified according to strict scientific standards:

| Data Source Code | Source Name & Provider | Source Classification | Verification Status | Usage & Authority Limitation |
| :--- | :--- | :--- | :--- | :--- |
| `SRC_OPEN_METEO` | Open-Meteo Weather Forecast (ECMWF IFS) | `PROVIDER_FORECAST` | `NUMERICAL_MODEL` | Real-time numerical weather prediction; not local raingauge measurement. |
| `SRC_COPERNICUS_DEM` | Copernicus DEM GLO-30 Topography (ESA / Airbus) | `DERIVED_ESTIMATE` | `OFFICIAL_PRIMARY` | 30m resolution digital elevation model; valid for topographic depressions, not micro-curb drainage. |
| `SRC_CITIZEN_CROWD` | JalMarg Citizen Waterlogging Reports | `USER_REPORTED` | `COMMUNITY_CROWDSOURCED`| Field observations with photo evidence; requires cluster corroboration. |
| `SRC_NASA_IMERG` | NASA GPM IMERG Early Precipitation Run (V07B) | `PROVIDER_FORECAST` | `NUMERICAL_MODEL` | Satellite precipitation estimate with ~4-hour latency. |
| `SRC_HISTORICAL_HOTSPOTS`| Delhi NCR Chronic Hotspots Registry (Delhi Police / MCD) | `DERIVED_ESTIMATE` | `OFFICIAL_PRIMARY` | Chronic inundation list from official police traffic advisories. |
| `SRC_DELHI_DRAINAGE_STATUS`| Delhi Municipal Drainage Telemetry Status (DJB/PWD) | `UNAVAILABLE` | `UNVERIFIED` | **DATA GAP IDENTIFIED:** Real-time underground stormwater pipe discharge telemetry is unmetered. |

### Provenance Integrity Rules Enforced:
1. **Never Label Heuristic Polygons as Official:** Planning bounding boxes are explicitly marked `BoundaryType.PLANNING_BOUNDING_BOX` or `SYNTHETIC_DEMO`.
2. **Explicit Data Gaps Disclosure:** What-If simulations include metadata disclosing:
   * `physicalDischargeCfd: "NOT_MODELED"`
   * `soilPercolationRate: "NOT_MODELED"`
   * `pipeSiltationLevel: "UNVERIFIED_ASSUMPTION"`
   * `hydrologyNote: "Outputs are decision-support heuristic indices, not hydraulic inundation depths."`

---

## 4. SECURITY HARDENING AUDIT & FIXES APPLIED

| Area | Vulnerability / Risk Identified | Fix Applied & Verified | Status |
| :--- | :--- | :--- | :--- |
| **Backdoor Admin Key** | `AuthorityIncidentController` accepted `"change-me"` as a valid key. | Removed `"change-me"`. Configured secure defaults via `JALMARG_AUTHORITY_ADMIN_KEY`. | **VERIFIED** |
| **Timing Attacks** | `String.equals()` was used for secret key verification. | Replaced with constant-time `MessageDigest.isEqual()` to prevent timing side-channels. | **VERIFIED** |
| **Operator Header Injection** | Malicious `X-Operator-Id` could contain CRLF characters to forge logs or inject JSON fields. | Added `sanitizeActor()` method stripping `\r`, `\n`, `\t`, and quotes. | **VERIFIED** |
| **Incident Input Bounds** | Unbounded incident requests allowed out-of-bounds geographic coordinates and empty titles. | Added strict coordinate boundary validation (Delhi NCR: Lat 28.0-29.2, Lng 76.5-77.8) and title checks. | **VERIFIED** |
| **Database Passwords** | Cleartext password exposure risk in logs/reports. | Password masking and environment variable overrides (`DB_PASSWORD`, `ADMIN_KEY`) standardized. | **VERIFIED** |

---

## 5. REPOSITORY FILES CHANGED

1. [AuthorityIncidentController.java](file:///d:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/twin/web/AuthorityIncidentController.java)
   * Constant-time authorization token validation with `MessageDigest.isEqual`
   * Removal of insecure backdoor `"change-me"`
   * Input sanitization for `actorId`
   * Bounding box validation for Delhi NCR coordinates
2. [application.properties](file:///d:/jalmarg/jalmarg/backend/src/main/resources/application.properties)
   * Updated `jalmarg.alerts.run-key` and `jalmarg.authority.admin-key` to secure production-ready environment defaults
3. [application-postgis.properties](file:///d:/jalmarg/jalmarg/backend/src/main/resources/application-postgis.properties)
   * Hardened Hibernate 6 PostgreSQL dialect configuration
   * Configured `ddl-auto=update` and Flyway migration integration
4. [MunicipalZone.java](file:///d:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/twin/model/MunicipalZone.java)
   * Explicit `@Column(name = "boundary_geojson")` mapping
5. [RoadSegment.java](file:///d:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/twin/model/RoadSegment.java)
   * Explicit `@Column(name = "geometry_geojson")` mapping
6. [TwinPage.jsx](file:///d:/jalmarg/jalmarg/frontend/src/pages/TwinPage.jsx)
   * Dual-payload mapping for scenario simulation request and response
7. [Phase20SecurityAndDigitalTwinTest.java](file:///d:/jalmarg/jalmarg/backend/src/test/java/org/growforge/jalmarg/Phase20SecurityAndDigitalTwinTest.java)
   * Added automated security tests covering authentication rejection, input validation, operator sanitization, and scenario isolation

---

## 6. FLYWAY MIGRATION STATUS

* **Migration Engine:** Flyway 10.10.0 with PostgreSQL extension
* **Location:** `classpath:db/migration/postgis`
* **Target Migration:** `V1__municipal_digital_twin_postgis.sql`
* **Status on `jalmarg_twin`:** **SUCCESSFUL (Version 1)**
* **Schema Checksum:** Valid and recorded in `flyway_schema_history` table.

---

## 7. GEOSPATIAL VALIDATION RESULTS

1. **Point-in-Polygon (`ST_Contains`):**
   * MultiPolygon zone `ZONE_NDMC_CORE` (Area: 9.75 km²) correctly confirmed containment of Connaught Place `(77.215, 28.625)` (`true`) and non-containment of outside point `(77.100, 28.500)` (`false`).
2. **Geodesic Distance (`ST_Distance` on geography):**
   * Minto Road underpass centerline `ROAD_MINTO_BRIDGE` (Length: 531.58m) computed exact geodesic distance of `20.39m` to the central flood hotspot.
3. **GiST Spatial Queries:**
   * Spatial queries execute against GiST indexes without table scans.

---

## 8. AUTOMATED TEST RESULTS

### Backend Test Suite (`mvn test`)
* **Total Tests Executed:** 66
* **Passed:** 66
* **Failed:** 0
* **Errors:** 0
* **Skipped:** 0
* **Execution Time:** ~26.6 seconds
* **Status:** **`VERIFIED`**

### Frontend Test Suite (`node --test tests/stage2.test.mjs`)
* **Total Tests Executed:** 17
* **Passed:** 17
* **Failed:** 0
* **Status:** **`VERIFIED`**

### Frontend Production Build (`npx vite build`)
* **Build Result:** Success (dist bundle generated with PWA service worker and precache manifest)
* **Status:** **`VERIFIED`**

---

## 9. CARSYNC SAFETY CONFIRMATION

* **Port 5432 Status:** `127.0.0.1:5432 - accepting connections` / Windows Service `postgresql-x64-17` protected.
* **CarSync Database:** `carsync` database untouched.
* **CarSync Filesystem:** `D:\PostgreSQL17` and `D:\PostgreSQL17\data` completely untouched.
* **JalMarg Port Separation:** JalMarg runs strictly on Port `5433`.

---

## 10. REMAINING BLOCKERS & RECOMMENDED PHASE 21

### Verified Existing Capabilities
* PostGIS 3.6.2 spatial database cluster on Port 5433
* 10 digital twin relational and geospatial tables
* Constant-time administrative authentication and CRLF-safe audit trail
* Isolated What-If monsoon simulation engine with explicit heuristic disclaimers
* Safe route evaluation avoiding verified inundated and predicted risk segments
* Real Open-Meteo forecast integration and Copernicus DEM terrain ingestion

### Scientific Data Gaps (Not Blockers, but Documented Facts)
* Real-time underground pipe discharge telemetry across MCD/DJB networks is unmetered; simulations use heuristic capacity assumptions rather than CFD pipe hydraulics.

### Recommended Phase 21 Next Steps
1. **IoT Telemetry Sensor Ingestion Simulator:** Ingest synthetic or pilot ultrasonic water level sensors for Minto Road and Zakhira underpasses into `twin_environmental_observations`.
2. **Docker Compose & Helm Chart Packaging:** Containerize the Spring Boot backend and React frontend with multi-stage Dockerfiles pointing to Amazon RDS PostgreSQL / PostGIS.
3. **Automated Scheduled Snapshot Publishing:** Enable Spring Scheduled tasks to publish immutable twin snapshots every 15 minutes during active rainfall events.
