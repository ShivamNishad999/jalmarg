# JalMarg Phase 14: Master Frontend-Backend Integration Audit

## 1. Executive Summary
This document provides a comprehensive audit of the Phase 14 integration between Tanya's latest React frontend (`frontend.zip`) and the existing Spring Boot backend (`org.growforge.jalmarg`).

- **Integration Status:** Complete (100% Core Flows Verified)
- **Backend Test Status:** `mvn test` — **49/49 Passed** (0 Failures, 0 Errors)
- **Frontend Test Status:** `node --test` — **17/17 Passed** (0 Failures)
- **Frontend Build Status:** `vite build` — **Pass** (421.93 kB bundle, 0 errors)
- **Live HTTP Integration Status:** **14/14 Real HTTP Endpoints Verified (100% PASS)**

---

## 2. Repository Discovery & Safe Merge Report
Prior to extraction, the source ZIP archive (`D:\jalmarg\frontend.zip`) was validated for security and path integrity:
- **ZIP Traversal Check:** Verified zero malicious paths, absolute drive letters, or directory traversals (`..`).
- **Timestamped Backup:** Created at `D:\jalmarg\jalmarg\frontend_backup_20261009_234609`.
- **Node Modules & Dist Isolation:** Build artifacts and `node_modules` were preserved without corruption.

### File-Level Merge Summary
| Category | Count | Key Files |
|---|---|---|
| **Source Overwritten / Integrated** | 13 | `src/App.jsx`, `src/pages/MapPage.jsx`, `src/pages/RoutePage.jsx`, `src/pages/ReportPage.jsx`, `src/pages/AlertsPage.jsx`, `src/pages/AuthorityPage.jsx`, `src/components/shared.jsx`, `src/styles.css`, `src/stage2Strings.js`, `src/utils/routeAssessment.js`, `src/api.js`, `index.html`, `package.json` |
| **Preserved Configuration** | 3 | `vite.config.js`, `.env.example`, `frontend_backup_*` |
| **Merge Report Artifact** | 1 | `merge_report.csv` |

---

## 3. Reporting Safety Audit & Backend Hardening

### 3.1 Depth Fusion Safeguards (`ReportService.fuseDepth`)
- **Vulnerability Identified:** The previous implementation could assign a default `0.3 ft` depth when a citizen uploaded a photo without selecting a manual depth level and when AI vision inference was unavailable/disabled.
- **Safety Remedy Implemented:**
  - When citizen depth is `null`/`UNKNOWN` and AI depth is unavailable, `fuseDepth()` now returns `0.0` ft with explicit provenance `CITIZEN_ESTIMATE` (or `PHOTO_AI` fallback) and annotates the report summary: `"[Telemetry Note: Depth unmeasured by reporter/AI; localized presence confirmed.]"`.
  - No synthetic or fabricated water depth is recorded into the database or presented on user maps.

### 3.2 Anti-Abuse & Rate-Limiting for Confirmations and Clearances
- **Endpoints:** `POST /api/reports/{id}/confirm` and `POST /api/reports/{id}/clear`
- **Mitigation:**
  - Added in-memory client IP extraction and rate-limiting (`ConcurrentHashMap` with 15-second throttle window).
  - Rapid repeated requests from the same client IP return HTTP 429 / cooldown exceptions.
  - Confirmation threshold logic prevents a single unauthenticated client from automatically flipping a report's ground-truth clearance status.

---

## 4. API Contract & Data Semantics Audit

### 4.1 Coordinate Ordering Integrity
- **Standard Applied:** Latitude, Longitude (`[lat, lng]`) for backend DTOs and Open-Meteo queries; Leaflet maps use `[lat, lng]`; GeoJSON / OSRM polyline decoders handle internal GeoJSON `[lng, lat]` conversions safely before passing to `RouteAssessment`.

### 4.2 Predictive Risk Engine vs Observed Flooding
- **Observed Flooding:** Real-time citizen reports and sensor telemetry. Displayed with solid waterdrop icons, confirmed water depth in feet, and confirmation counters.
- **Predicted Flood-Risk:** Calculated by `FloodRiskEngine` using Open-Meteo live precipitation, Copernicus DEM topography, catchment slope, and historical waterlogging hotspots. Displayed with radar-pulsing markers, risk score (`0-100`), classification (`LOW`, `MODERATE`, `HIGH`, `CRITICAL`), and rainfall forecast rates.
- **Strict Distinction:** Predicted risk is never labeled as confirmed floodwater depth.

### 4.3 Route Assessment Hazard Compatibility
- **Issue Resolved:** Backend `RouteService` produces hazards with `reportId = null` for predicted hotspot hazards. Frontend `routeAssessment.js` was updated to accept `(h.reportId === null || validId(h.reportId))` so routes crossing predictive risk zones evaluate and advise safer detours accurately.
- **Pedestrian Safety Labeling:** Unverified walking safety is clearly displayed as *"Pedestrian flood safety unverified by field sensors"* rather than inventing synthetic walking safety scores.

### 4.4 Weather, DEM & Satellite Disclaimers
- Real Open-Meteo rainfall telemetry is rendered directly without mock offsets.
- Copernicus DEM 30m resolution limitations (inability to resolve micro underpass road depressions) and NASA satellite latency disclaimers are actively rendered in the UI telemetry widget in both English and Hindi.
