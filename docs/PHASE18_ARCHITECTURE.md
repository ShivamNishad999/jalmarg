# JALMARG PHASE 18 — ARCHITECTURE SPECIFICATION
## Municipal Flood Digital Twin Foundation

**Project:** JalMarg — Delhi NCR Urban Waterlogging Intelligence & Safe Routing  
**Phase:** 18 — Municipal Flood Digital Twin Foundation  
**Status:** IMPLEMENTED, TESTED, AND VERIFIED  
**Date:** 2026-10-10  

---

### 1. Architectural Vision & Scope

The Municipal Flood Digital Twin Foundation expands JalMarg from a citizen hazard-reporting platform into a comprehensive, municipal-grade decision-support twin without breaking existing citizen reporting, weather ingestion, autonomous predictive risk scoring, or route hazard evaluation.

```
+---------------------------------------------------------------------------------------+
|                                    REACT 18 FRONTEND                                  |
|   +-------------------+  +-------------------+  +-----------------+  +------------+   |
|   | / (Live Map View) |  | /twin (Twin View) |  | /route (Routing)|  | /authority |   |
|   +---------+---------+  +---------+---------+  +--------+--------+  +-----+------+   |
+-------------|----------------------|---------------------|-----------------|----------+
              |                      |                     |                 |
              v                      v                     v                 v
+---------------------------------------------------------------------------------------+
|                                SPRING BOOT 3.4.3 BACKEND                              |
|                                                                                       |
|   +-----------------------+   +-----------------------+   +-----------------------+   |
|   |  Phase 1-17 Legacy    |   | Digital Twin Engines  |   | Municipal Operations  |   |
|   |  REST Endpoints       |   | /api/v1/twin/*        |   | /api/v1/authority/*   |   |
|   |  /api/reports         |   | - Status & Health     |   | - Incident Lifecycle  |   |
|   |  /api/flood-risk      |   | - Zones & Roads       |   | - Resource Dispatch   |   |
|   |  /api/weather         |   | - Source Provenance   |   | - Audit Logging       |   |
|   |  /api/routes          |   | - Snapshots (Current/ |   |                       |   |
|   |  /api/alerts          |   |   Hist/Simulated)     |   |                       |   |
|   |                       |   | - What-If Scenarios   |   |                       |   |
|   +-----------+-----------+   +-----------+-----------+   +-----------+-----------+   |
|               |                           |                           |               |
|               +---------------------------+---------------------------+               |
|                                           |                                           |
|                                           v                                           |
|   +-------------------------------------------------------------------------------+   |
|   |                         CORE SERVICES & LOGIC LAYER                           |   |
|   |  * TwinStateService (Snapshot publisher & state isolation)                    |   |
|   |  * ScenarioService (Deterministic what-if sandbox & delta calculator)         |   |
|   |  * MunicipalIncidentService (State machine: NEW->TRIAGED->ASSIGNED->RESOLVED) |   |
|   |  * TwinSourceRegistryService (Provenance & data quality ledger)               |   |
|   |  * FloodRiskAssessmentService (Preserved calibrated multi-factor risk formula)|   |
|   |  * WeatherService (Open-Meteo hourly precipitation forecast ingestion)        |   |
|   |  * CopernicusDemService (Contextual GLO-30 DSM terrain height provider)       |   |
|   +---------------------------------------+---------------------------------------+   |
|                                           |                                           |
+-------------------------------------------|-------------------------------------------+
                                            v
+---------------------------------------------------------------------------------------+
|                                    PERSISTENCE LAYER                                  |
|                                                                                       |
|   Default Development:                               PostGIS Production:              |
|   +-----------------------------+                    +----------------------------+   |
|   | H2 Local Database           |                    | PostgreSQL 16 + PostGIS 3.4|   |
|   | - In-memory / file-based    |                    | - SRID 4326 Geography      |   |
|   | - Zero native DLL dependency|                    | - Spatial GIST Indexes     |   |
|   | - Seamless dev onboarding   |                    | - Flyway V1 Migration      |   |
|   +-----------------------------+                    +----------------------------+   |
+---------------------------------------------------------------------------------------+
```

---

### 2. Core Architectural Principles

#### Principle 1: Critical Scientific Rule (No Fabrication)
- **A digital twin is NOT automatically a calibrated hydrodynamic simulation.**
- JalMarg does not fabricate stormwater pipes, pumps, culverts, or road water depths where empirical data does not exist.
- If municipal stormwater drainage telemetry is absent, the system explicitly reports: `drainageNetworkAvailable: false` and displays: *"Drainage network data unavailable"*.

#### Principle 2: Strict Temporal & State Segregation
The platform manages three fundamentally separate state spaces:
1. **Current State:** Live telemetry, active citizen reports, real-time Open-Meteo weather forecasts.
2. **Historical State:** Timestamped, immutable snapshot records reflecting past audited risk distributions.
3. **Simulated State:** Sandbox what-if scenarios based on hypothetical rainfall totals, drainage failure factors, and road closures.
*State isolation invariant:* Scenario runs are persisted in `ScenarioRun` and NEVER alter live risk snapshots, production routing weights, or active citizen reports.

#### Principle 3: Provenance-First Ingestion
Every dataset, forecast, and observation is tagged with a `SourceClassification`:
- `VERIFIED_OBSERVATION`: Officially surveyed or measured data.
- `PROVIDER_FORECAST`: Numerical model forecasts (e.g. Open-Meteo).
- `DERIVED_ESTIMATE`: Rule-calculated or contextual data (e.g. Copernicus DEM).
- `USER_REPORTED`: Crowdsourced citizen observations.
- `SIMULATED`: Hypothetical sandbox calculations.
- `UNAVAILABLE`: Explicitly absent parameters (never defaulted to 0).

---

### 3. Subsystem Breakdown

#### 3.1 Digital Twin State Engine (`TwinStateService`)
- Builds periodic immutable snapshots (`TwinSnapshot`) containing zone-level vulnerability, risk index scores, weather conditions, active reports, and cryptographic SHA-256 payload checksums.
- Calculates data freshness index in minutes since last external telemetry sync.

#### 3.2 What-If Scenario Sandbox (`ScenarioService`)
- Provides deterministic simulation for municipal emergency preparedness.
- Takes inputs: `rainfallMm`, `rainfallIntensityMmPerHour`, `durationHours`, `drainageCapacityFactor`, `roadClosureSimulated`, `availableResponseTeams`, `zoneId`.
- Computes baseline risk, simulated risk, delta score, affected roads count, and response mitigation factors.
- Stamps all outputs with `isSimulation: true` and `modelVersion: JALMARG_WHATIF_V1.0_HEURISTIC`.

#### 3.3 Municipal Incident Lifecycle Engine (`MunicipalIncidentService`)
- Governs lifecycle transitions: `NEW` ➔ `TRIAGED` ➔ `ASSIGNED` ➔ `IN_PROGRESS` ➔ `RESOLVED` ➔ `VERIFIED_CLOSED` (with optional `REJECTED`).
- Priority scoring algorithm:
  $$\text{Priority} = \min(10.0, (\text{SeverityWeight} \times 2.0) + (\text{ZoneVuln} \times 2.0) + \text{RoadBlockageBonus} + \text{ReportCountBonus})$$
- Enforces role accountability and records append-only `AuditEvent` logs with timestamp, actor, entity, and delta state.

#### 3.4 Spatial & Persistence Strategy
- Hybrid profile support:
  - `dev` / default: H2 file database with numeric lat/lng bounding box indexing for immediate cross-platform local development.
  - `postgis`: PostgreSQL + PostGIS with `GEOMETRY(Polygon, 4326)` and `GEOMETRY(LineString, 4326)` and SP-GiST/GIST spatial indexes.
- Database migrations managed through Flyway scripts under `src/main/resources/db/migration/postgis/`.
