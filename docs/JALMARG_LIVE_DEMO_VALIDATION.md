# JalMarg — Live Application Launch & End-to-End Demo Validation Report

**Phase:** Live Application Launch & End-to-End Demo Validation  
**Date:** 2026-10-11  
**Project:** JalMarg (`D:\jalmarg\jalmarg`)  
**Operating Environment:** Windows 11 (PowerShell 7.5.4, OpenJDK 21.0.8, Node v22.14.0, Vite 5.4.21, PostgreSQL 17.11, PostGIS 3.6.2)  
**Database Isolation Target:** `127.0.0.1:5433` (`jalmarg_twin`) — CarSync Port `5432` 100% Isolated  

---

## 1. Executive Summary & Demo Readiness Verdict

**Verdict:** `READY_FOR_LOCAL_LIVE_DEMO`

The JalMarg platform was launched locally from source, verified against live PostgreSQL/PostGIS database services on port `5433`, and validated across the full citizen-to-municipal end-to-end lifecycle. Headless Chrome browser automation captured visual DOM screenshots across 4 responsive viewports (360×800, 390×844, 768×1024, 1366×768), and the complete 4-step live workflow executed successfully:
1. **Citizen Observation Submission:** Successfully posted to `/api/reports` via multipart form data; persisted in PostgreSQL `reports` table with initial `UNVERIFIED` status.
2. **Municipal Authentication & Review:** Demo operator (`operator_pwd`) authenticated via `/api/v1/auth/login`, retrieved active review queue, approved report as `VERIFIED`, and wrote immutable audit trail entry to `twin_audit_events`.
3. **Heuristic Risk Engine:** Assessed Minto Bridge Underpass location via `/api/flood-risk`, returning a risk score of 91/100 (`CRITICAL`) incorporating corroborating citizen reports, Copernicus DEM elevation, and Open-Meteo rainfall.
4. **Hazard-Aware Route Evaluation:** Evaluated candidate routes via `/api/routes/evaluate`. The direct hazard route was scored at risk 20 (unsafe for two-wheelers), while the bypass route scored risk 0, with the engine recommending the safer corridor under the guidance: *"Estimated lower waterlogging exposure"*.
5. **Fixture Safety:** Disposable demo records were completely removed after verification without polluting ground-truth calibration data.

---

## 2. Runtime Environment & Startup Status

### A. Database Isolation (PostgreSQL 17.11 / PostGIS 3.6.2)
- **Host & Port:** `127.0.0.1:5433`
- **Database:** `jalmarg_twin`
- **PostGIS Extension Version:** `3.6.2`
- **Isolation Policy:** Port `5432` (CarSync) remained strictly untouched, unqueried, and isolated.
- **Flyway Migrations:** 4 migrations verified (V1, V2, V3, V4), all checksums valid.
- **Process Status:** Running via PostgreSQL daemon (`postgres.exe -p 5433`).

### B. Spring Boot Backend Daemon
- **Bound URL:** `http://127.0.0.1:8080`
- **Active Profile:** `postgis`
- **JVM Version:** OpenJDK 64-Bit Server VM (build 21.0.8+9-LTS)
- **Startup Artifact:** `backend/target/jalmarg.jar`
- **Public Health Endpoint:** `GET /api/health` -> `{"status":"UP","application":"jalmarg"}` (Sanitized: does not leak database port or Flyway version to unauthenticated callers).
- **Protected Diagnostics:** `GET /api/v1/health/diagnostics` -> Requires `X-Admin-Key` (returns HTTP 401 when unauthenticated).

### C. React / Vite Frontend Server
- **Bound URL:** `http://127.0.0.1:5173`
- **Dev Server:** Vite v5.4.21
- **API Proxy:** Configured to route `/api/*` directly to `http://127.0.0.1:8080`
- **Computer Vision Service:** `VISION_ENABLED=false` maintained for all demo executions per licensing compliance constraints.

---

## 3. Real Browser Verification & Visual Evidence

Local Google Chrome (`C:\Program Files\Google\Chrome\Application\chrome.exe`) was automated in headless mode using `--no-sandbox --disable-background-networking --disable-default-apps --user-data-dir=target/chrome-temp` to capture DOM states and screenshots across 4 target viewports:

| Viewport | Device Profile | Page | Screenshot Artifact | Dimensions | File Size |
|---|---|---|---|---|---|
| **1366 × 768** | Desktop Widescreen | Digital Twin Home | `docs/demo_screenshots/home_map_desktop_1366x768.png` | 1366 × 768 | 104.9 KB |
| **1366 × 768** | Desktop Widescreen | Route Planner | `docs/demo_screenshots/route_planner_desktop_1366x768.png` | 1366 × 768 | 95.6 KB |
| **1366 × 768** | Desktop Widescreen | Authority Console | `docs/demo_screenshots/authority_console_desktop_1366x768.png` | 1366 × 768 | 124.5 KB |
| **768 × 1024** | Tablet Portrait | Digital Twin Home | `docs/demo_screenshots/home_map_tablet_768x1024.png` | 768 × 1024 | 107.9 KB |
| **768 × 1024** | Tablet Portrait | Route Planner | `docs/demo_screenshots/route_planner_tablet_768x1024.png` | 768 × 1024 | 64.9 KB |
| **768 × 1024** | Tablet Portrait | Authority Console | `docs/demo_screenshots/authority_console_tablet_768x1024.png` | 768 × 1024 | 110.9 KB |
| **390 × 844** | Mobile (iPhone 14) | Digital Twin Home | `docs/demo_screenshots/home_map_mobile_390x844.png` | 390 × 844 | 58.1 KB |
| **390 × 844** | Mobile (iPhone 14) | Route Planner | `docs/demo_screenshots/route_planner_mobile_390x844.png` | 390 × 844 | 37.9 KB |
| **390 × 844** | Mobile (iPhone 14) | Authority Console | `docs/demo_screenshots/authority_console_mobile_390x844.png` | 390 × 844 | 84.6 KB |
| **360 × 800** | Mobile (Android Standard) | Digital Twin Home | `docs/demo_screenshots/home_map_mobile_360x800.png` | 360 × 800 | 67.9 KB |
| **360 × 800** | Mobile (Android Standard) | Route Planner | `docs/demo_screenshots/route_planner_mobile_360x800.png` | 360 × 800 | 34.9 KB |

*Manifest stored at:* `docs/demo_screenshots/demo_visual_validation_manifest.json`

---

## 4. End-to-End Workflow Verification Evidence

Automated validation script `scripts/execute_live_demo_workflow.mjs` was executed against the live application stack.

### Step 1: Citizen Waterlogging Report Submission
- **HTTP Method & URL:** `POST http://127.0.0.1:8080/api/reports`
- **Content-Type:** `multipart/form-data`
- **Payload:**
  - `latitude`: `28.6347`
  - `longitude`: `77.2263`
  - `locationName`: `Minto Bridge Underpass`
  - `area`: `Central Delhi`
  - `depthLevel`: `KNEE` (calibrated to ~1.5 ft water depth)
  - `note`: `[DEMO_PILOT_25H] Live hackathon validation observation at Minto Bridge Underpass`
- **HTTP Status Code:** `201 CREATED`
- **Created ID:** `#228`
- **Database Persistence Verified:**
  ```sql
  SELECT id, status, verification_status, depth_ft FROM reports WHERE id = 228;
  -- Result: 228|ACTIVE|UNVERIFIED|1.5
  ```

### Step 2: Municipal Authority Authentication & Review
- **Login Request:** `POST http://127.0.0.1:8080/api/v1/auth/login`
- **Credentials:** `operator_pwd` / `[REDACTED_MUNICIPAL_CREDENTIAL]`
- **Login Response:** HTTP 200, JWT token issued, user: `operator_pwd`, role: `MUNICIPAL_OPERATOR`, dept: `PWD Central Drainage`.
- **Review Queue:** `GET http://127.0.0.1:8080/api/v1/authority/reports?status=ACTIVE`
  - Confirmed Report `#228` visible in active municipal review queue.
- **Review Decision:** `POST http://127.0.0.1:8080/api/v1/authority/reports/228/review`
  - Decision: `VERIFIED`
  - Verified By: `operator_pwd`
  - HTTP Status: `200 OK`
- **Audit Trail Persistence Verified:**
  ```sql
  SELECT action_type, actor_id, target_entity_id FROM twin_audit_events WHERE target_entity_id = '228';
  -- Result: REPORT_VERIFY|operator_pwd|228
  ```

### Step 3: Heuristic Risk Engine Assessment
- **HTTP Method & URL:** `GET http://127.0.0.1:8080/api/flood-risk?lat=28.6347&lng=77.2263&area=Minto%20Bridge`
- **Response:**
  - `riskScore`: `91` / 100
  - `riskLevel`: `CRITICAL`
  - `activeReportCount`: `1` (Max depth: `1.5` ft)
  - `historicalProneness`: `5` / 100
  - `summaryEn`: *"OBSERVED WATERLOGGING: 1 active report(s) nearby. Peak water depth ~1.5 ft. Hazardous for two-wheelers and small cars."*
- **Scientific Labeling Compliance:** Does not claim hydrodynamic calibrated physics depth; explicitly labeled as an estimated heuristic risk score based on multi-source data.

### Step 4: Hazard-Aware Route Evaluation
- **HTTP Method & URL:** `POST http://127.0.0.1:8080/api/routes/evaluate`
- **Payload:** Two candidate polylines (Route 0 traversing Minto Bridge underpass; Route 1 bypassing via Outer Connaught Circus). Buffer: 60 meters.
- **Evaluation Results:**
  - **Route #0 (Direct through Minto Bridge):** Risk Score = `20.0`, Hazard Count = `1`, `safeForBike = false`.
  - **Route #1 (Outer Bypass):** Risk Score = `0.0`, Hazard Count = `0`, `safeForBike = true`.
- **Recommendation:** `recommendedIndex: 1`
- **Safety Label:** *"Estimated lower waterlogging exposure. Follow official Delhi Traffic Police advisories."*

### Step 5: Safe Fixture Cleanup
- Post-run cleanup executed:
  ```sql
  DELETE FROM twin_report_observations WHERE report_id = 228;
  DELETE FROM twin_audit_events WHERE target_entity_id = '228';
  DELETE FROM reports WHERE id = 228;
  ```
- Ground-truth scientific dataset and operational reports remain 100% uncorrupted.

---

## 5. Security and Privacy Audit

1. **Frontend Bundle Secrets:** Clean. No hardcoded API keys, JWT secrets, or administrative credentials exist in client source code.
2. **Credential Redaction:** Console logging, screenshot manifests, and test runs redact passwords and full session tokens.
3. **Public Endpoint Minimization:** `GET /api/health` returns only `{status:"UP", application:"jalmarg"}`. System details, database addresses, and Flyway migration lists are restricted to authenticated administrator calls.
4. **Role-Based Access Control:** Municipal operator endpoints (`/api/v1/authority/*`) enforce role checks. Unauthenticated requests return `401 Unauthorized`.
5. **File Upload Security:** Multipart uploads enforce image MIME type checks (`photo.getContentType().startsWith("image/")`). Non-image payloads are rejected with HTTP 400.
6. **Input Sanitization:** Geolocation boundaries are strictly validated; invalid coordinates (> 90° latitude) are rejected with HTTP 400.

---

## 6. Comprehensive Automated Regression Suite Results

| Test Suite | Framework / Runner | Total Tests | Passed | Failed | Skipped |
|---|---|---|---|---|---|
| **Spring Boot Backend** | Maven / JUnit 5 | 123 | 123 | 0 | 0 |
| **Python Data / ML Pipeline** | Pytest | 44 | 44 | 0 | 0 |
| **Frontend & Browser Journeys** | Node Test Runner (`node --test tests/*.mjs`) | 51 | 51 | 0 | 0 |
| **Total Automated Tests** | **Multi-Stack CI/CD** | **218** | **218** | **0** | **0** |

*All 218 automated tests passed with zero regressions.*

---

## 7. Confirmed Issues & Remediation

1. **Playwright CDN Dependency Block:**
   - *Issue:* Remote Playwright driver CDN returned HTTP 404 when downloading driver v1.57.0.
   - *Fix:* Automated local Google Chrome (`C:\Program Files\Google\Chrome\Application\chrome.exe`) in headless mode with `--no-sandbox --disable-background-networking --disable-default-apps --user-data-dir=target/chrome-temp`.
2. **Citizen Report Multipart Form Parsing:**
   - *Issue:* Test client attempted to submit `application/x-www-form-urlencoded` rather than `multipart/form-data`.
   - *Fix:* Updated script to use native `FormData` multipart boundary serialization; verified HTTP 201 response.
3. **Audit Trail Column Schema Alignment:**
   - *Issue:* Test query referenced legacy column names (`event_type`, `actor_username`).
   - *Fix:* Aligned query with `twin_audit_events` schema (`action_type`, `actor_id`, `target_entity_id`).

---

## 8. Reproducible Demo Launch Instructions

To launch and run the live demonstration on any workstation:

1. **Start PostGIS 3.6.2 (Port 5433):**
   ```powershell
   & "D:\JalMargPostGIS\bin\postgres.exe" -D "D:\JalMargPostGIS\data" -p 5433
   ```
2. **Start Spring Boot Backend:**
   ```powershell
   cd D:\jalmarg\jalmarg
   java -jar backend/target/jalmarg.jar --spring.profiles.active=postgis
   ```
3. **Start Vite Frontend:**
   ```powershell
   cd D:\jalmarg\jalmarg\frontend
   npx vite --port 5173 --host 127.0.0.1
   ```
4. **Open in Browser:**
   - Public Portal: `http://127.0.0.1:5173/`
   - Authority Console: `http://127.0.0.1:5173/authority` (Requires authenticated staff credentials)
   - Route Planner: `http://127.0.0.1:5173/route`
5. **Run Automated Demonstration Script:**
   ```powershell
   node scripts/execute_live_demo_workflow.mjs
   ```
