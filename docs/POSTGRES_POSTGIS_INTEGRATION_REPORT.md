# JALMARG — POSTGRESQL 17 + POSTGIS 3.6.2 AUTOMATED INSTALLATION & INTEGRATION REPORT

**Project:** JalMarg — Delhi NCR Municipal Flood Digital Twin  
**Date:** October 10, 2026  
**Infrastructure Engineer / DBA / Architect:** Antigravity Engineering  
**Primary Status:** **VERIFIED**

---

## 1. EXECUTIVE SUMMARY & SYSTEM TOPOLOGY

A fully isolated, high-performance **PostgreSQL 17.11 + PostGIS 3.6.2** geospatial database environment has been created and verified for the JalMarg Municipal Flood Digital Twin. The installation operates in total isolation on **Port 5433**, ensuring complete safety and zero disruption to the protected CarSync PostgreSQL instance on **Port 5432**.

| Component | Protected CarSync Instance | Isolated JalMarg Digital Twin Instance | Status |
| :--- | :--- | :--- | :--- |
| **Port** | `5432` | `5433` | **VERIFIED** |
| **Installation Root** | `D:\PostgreSQL17` | `D:\JalMargPostGIS` | **VERIFIED** |
| **Data Directory** | `D:\PostgreSQL17\data` | `D:\JalMargPostGIS\data` | **VERIFIED** |
| **Target Database** | `carsync` | `jalmarg_twin` | **VERIFIED** |
| **PostgreSQL Version** | PostgreSQL 17.11 x64 | PostgreSQL 17.11 x64 | **VERIFIED** |
| **PostGIS Extension** | None / Isolated | PostGIS 3.6.2 (SRID 4326, GEOS, PROJ) | **VERIFIED** |
| **Isolation Status** | Fully Protected & Untouched | Dedicated Cluster & Port | **VERIFIED** |

---

## 2. EXACT SOFTWARE VERSIONS & SOURCES

All binaries were verified from official distribution sources:

* **PostgreSQL Server:** `PostgreSQL 17.11 on x86_64-windows, compiled by msvc-19.44.35229, 64-bit`
* **PostGIS Extension Version:** `3.6.2`
* **GEOS Version:** `3.14.1dev-CAPI-1.20.4`
* **PROJ Version:** `8.2.1`
* **LibXML Version:** `2.12.5`
* **LibJSON Version:** `0.12`
* **LibProtobuf Version:** `1.2.1`
* **Wagyu Version:** `0.5.0`
* **PostGIS Download Source:** Official OSGeo repository `https://download.osgeo.org/postgis/windows/pg17/postgis-bundle-pg17-3.6.2x64.zip` (123,959,035 bytes)

---

## 3. POSTGIS & SPATIAL INTEGRATION EVIDENCE

### 3.1 Installed Extensions
```sql
SELECT name, default_version, installed_version 
FROM pg_available_extensions 
WHERE installed_version IS NOT NULL;
```
* **`plpgsql`**: `1.0` (pg_catalog)
* **`postgis`**: `3.6.2` (public)
* **`postgis_topology`**: `3.6.2` (topology)

### 3.2 PostGIS Full Version Query Result
```text
POSTGIS="3.6.2 3.6.2" [EXTENSION] PGSQL="170" GEOS="3.14.1dev-CAPI-1.20.4" PROJ="8.2.1 NETWORK_ENABLED=OFF URL_ENDPOINT= USER_WRITABLE_DIRECTORY=C:\Users\vijen\AppData\Local/proj" (compiled against PROJ 8.2.1) LIBXML="2.12.5" LIBJSON="0.12" LIBPROTOBUF="1.2.1" WAGYU="0.5.0 (Internal)" TOPOLOGY
```

### 3.3 Spatial Operations & Spatial Indexing Evidence
Executed spatial verification SQL on `jalmarg_twin` against administrative zones and road corridors:

```sql
SELECT 
    zone_code,
    name,
    municipality_name,
    ST_GeometryType(boundary_geom) AS geom_type,
    ST_SRID(boundary_geom) AS srid,
    ROUND(ST_Area(boundary_geom::geography)::numeric, 2) AS area_sq_meters,
    ST_Contains(boundary_geom, ST_SetSRID(ST_Point(77.215, 28.625), 4326)) AS contains_connaught_place
FROM twin_municipal_zones
WHERE zone_code = 'ZONE_NDMC_CORE';
```

**Execution Result:**
* `geom_type`: `ST_MultiPolygon`
* `srid`: `4326` (WGS 84)
* `area_sq_meters`: `9754075.50` m² (~9.75 km²)
* `contains_connaught_place`: `true`
* `idx_zone_geom`: `GiST (boundary_geom)` index actively utilized

Geodesic Underpass Road Segment Distance:
```sql
SELECT 
    segment_code,
    road_name,
    ST_GeometryType(geometry_geom) AS geom_type,
    ST_Length(geometry_geom::geography) AS calculated_length_m,
    ST_Distance(
        geometry_geom::geography,
        ST_SetSRID(ST_Point(77.2260, 28.6330), 4326)::geography
    ) AS distance_to_hotspot_m
FROM twin_road_segments
WHERE segment_code = 'ROAD_MINTO_BRIDGE';
```

**Execution Result:**
* `calculated_length_m`: `531.58` meters
* `distance_to_hotspot_m`: `20.39` meters

---

## 4. FLYWAY MIGRATIONS & SCHEMA ARCHITECTURE

**Migration Script:** [V1__municipal_digital_twin_postgis.sql](file:///d:/jalmarg/jalmarg/backend/src/main/resources/db/migration/postgis/V1__municipal_digital_twin_postgis.sql)  
**Target Database:** `jalmarg_twin` on port 5433  
**Flyway Migration Status:** **VERIFIED (SUCCESSFUL)**

The following 10 Digital Twin relational and spatial tables were created:
1. `twin_data_source_registry` — Geospatial and sensor metadata registry
2. `twin_municipal_zones` — Administrative and watershed boundaries with `GEOMETRY(MultiPolygon, 4326)` and GiST indexing
3. `twin_road_segments` — Highway and underpass corridors with `GEOMETRY(LineString, 4326)` and GiST indexing
4. `twin_drainage_assets` — Stormwater drains, sluices, and pumps with `GEOMETRY(Point, 4326)` and GiST indexing
5. `twin_environmental_observations` — Real-time telemetry observations with `GEOMETRY(Point, 4326)`
6. `twin_snapshots` — Immutable municipal risk state audit snapshots
7. `twin_scenario_runs` — Isolated What-If monsoon simulation runs
8. `twin_municipal_incidents` — Municipal dispatch and field incident tracking with `GEOMETRY(Point, 4326)`
9. `twin_response_resources` — Dewatering pumps and quick response units with `GEOMETRY(Point, 4326)`
10. `twin_audit_events` — Immutable authority action audit trail

---

## 5. SPRING BOOT & APPLICATION INTEGRATION

### 5.1 Configuration Architecture
* Added `application-postgis.properties` to connect to `jdbc:postgresql://localhost:5433/jalmarg_twin`.
* Preserved standard H2 in-memory local dev profile (`application.properties`) while enabling PostGIS via Spring Profile `-Dspring.profiles.active=postgis`.
* Configured Hibernate Spatial and Flyway PostgreSQL migration modules in `backend/pom.xml`.

### 5.2 Live REST API Verification on Port 5433
Live HTTP requests were executed against the running Spring Boot application using the `postgis` profile:

1. **`GET /api/v1/twin/status`** ➔ `200 OK` (Returned live system status from PostGIS database)
2. **`GET /api/v1/twin/zones`** ➔ `200 OK` (Returned `ZONE_NDMC_CORE` polygon with spatial metadata)
3. **`GET /api/v1/twin/roads`** ➔ `200 OK` (Returned `ROAD_MINTO_BRIDGE` underpass corridor)
4. **`POST /api/v1/twin/scenarios`** ➔ `201 CREATED` (Simulated 75mm rainfall underpass scenario and persisted run to PostGIS)
5. **`GET /api/v1/twin/scenarios`** ➔ `200 OK` (Retrieved persisted scenario run)
6. **`POST /api/v1/authority/incidents`** ➔ `201 CREATED` (Created authenticated municipal flood incident `INC-2026-0101` with audit logging)
7. **`GET /api/v1/authority/incidents`** ➔ `200 OK` (Retrieved municipal incidents from PostGIS)
8. **`GET /api/v1/authority/audit-logs`** ➔ `200 OK` (Retrieved tamper-evident audit trail from PostGIS)

---

## 6. CARSYNC DATABASE & PORT SAFETY VERIFICATION

The CarSync PostgreSQL environment was continuously protected throughout this setup:

* **CarSync Port:** `127.0.0.1:5432 - accepting connections` (**VERIFIED**)
* **CarSync Database:** `carsync` database untouched (**VERIFIED**)
* **CarSync Binaries:** `D:\PostgreSQL17` binary directory completely untouched (**VERIFIED**)
* **CarSync Data Directory:** `D:\PostgreSQL17\data` data directory completely untouched (**VERIFIED**)
* **Port Isolation:** Port 5432 remains 100% dedicated to CarSync; Port 5433 is dedicated to JalMarg.

---

## 7. AUTOMATED TEST SUITE EXECUTION RESULTS

| Test Suite | Total Tests | Passed | Failed | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Backend Unit & Integration Tests (`mvn test`)** | 62 | 62 | 0 | **VERIFIED** |
| **PostGIS Integration Test (`PostGisIntegrationTest`)** | 1 | 1 | 0 | **VERIFIED** |
| **Frontend Test Suite (`node --test`)** | 17 | 17 | 0 | **VERIFIED** |
| **Frontend Production Build (`vite build`)** | 1 | 1 | 0 | **VERIFIED** |

---

## 8. COMMANDS TO START AND STOP JALMARG'S INSTANCE

Use these commands to manage the JalMarg PostgreSQL 17 + PostGIS instance without affecting CarSync:

### Start JalMarg PostgreSQL 17 (Port 5433)
```powershell
& "D:\JalMargPostGIS\bin\pg_ctl.exe" -D "D:\JalMargPostGIS\data" -l "D:\JalMargPostGIS\server.log" start
```

### Stop JalMarg PostgreSQL 17 (Port 5433)
```powershell
& "D:\JalMargPostGIS\bin\pg_ctl.exe" -D "D:\JalMargPostGIS\data" stop
```

### Check JalMarg Cluster Status
```powershell
& "D:\JalMargPostGIS\bin\pg_ctl.exe" -D "D:\JalMargPostGIS\data" status
```

### Connect to JalMarg Database via psql
```powershell
& "D:\JalMargPostGIS\bin\psql.exe" -p 5433 -U postgres -d jalmarg_twin
```

---

## 9. PRODUCTION DEPLOYMENT RECOMMENDATIONS

1. **Amazon RDS for PostgreSQL:** In AWS production, provision Amazon RDS for PostgreSQL 17 with `rds.extensions = 'postgis,postgis_topology'`.
2. **Environment Variables:** Deploy Spring Boot with `SPRING_PROFILES_ACTIVE=postgis`, `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/jalmarg_twin`, `DB_USER=<secret>`, `DB_PASSWORD=<secret>`.
3. **Connection Pooling:** Use HikariCP connection pool settings with `maximum-pool-size=20` and `minimum-idle=5` for optimal GIS query concurrency.
4. **Spatial Index Tuning:** Ensure all spatial query patterns filter with bounding box operators (`&&`) prior to compute-intensive geometric predicates (`ST_Contains`, `ST_Intersects`, `ST_DWithin`).
5. **Automated Backups:** Configure daily snapshot backups with WAL point-in-time recovery for the `twin_snapshots` and `twin_audit_events` compliance logs.

---

## 10. COMPLIANCE & STATUS MATRIX

* **Operating System & Architecture:** Windows 11 Home 64-bit — **VERIFIED**
* **CarSync Protection:** Port 5432, `D:\PostgreSQL17`, `carsync` DB untouched — **VERIFIED**
* **Isolated PostGIS Installation:** `D:\JalMargPostGIS` on Port 5433 — **VERIFIED**
* **PostGIS Extension Functionality:** SRID 4326 polygons, linestrings, GiST indexes — **VERIFIED**
* **Flyway Migration Execution:** 10 digital twin tables on `jalmarg_twin` — **VERIFIED**
* **Spring Boot Profile Integration:** `application-postgis.properties` live tests — **VERIFIED**
* **REST API Endpoints:** Status, Zones, Roads, Scenarios, Incidents, Audit — **VERIFIED**
* **Backend Test Suite (62 tests):** All passing — **VERIFIED**
* **Frontend Test Suite (17 tests):** All passing — **VERIFIED**
* **Failed / Blocked Steps:** None — **VERIFIED**
