# JalMarg Phase 17: Zero-Report Flood Prediction Verification

**Track 02 — Heat and Water Hackathon Validation Report**

## 1. Executive Summary

This document certifies that **JalMarg reliably identifies potential waterlogging hazards and recommends lower-risk navigation routes even when ZERO citizen reports exist** in the target area.

- **Objective:** Validate autonomous predictive flood modeling and hazard avoidance without crowd observations.
- **Backend Tests:** `mvn test` — **54/54 Passed** (including 5 dedicated zero-report integration tests in `ZeroReportPredictionTest.java`).
- **Frontend Tests:** `node --test` — **17/17 Passed** (0 Failures).
- **Frontend Production Bundle:** `npm run build` — **Pass** (421.93 kB bundle, 0 errors).
- **Verification Status:** **100% PASS** for all Phase 17 requirements.

---

## 2. Source Files Inspected & Architecture Audit

| Component / Layer | Source File Path | Role in Zero-Report Prediction |
|---|---|---|
| **Flood Risk Engine** | [`backend/.../service/FloodRiskAssessmentService.java`](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/service/FloodRiskAssessmentService.java) | Calculates composite risk score (`0-100`) from Open-Meteo precipitation, historical susceptibility, and topography when `activeReports == 0`. |
| **Autonomous Monitor** | [`backend/.../service/AutonomousFloodMonitorService.java`](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/service/AutonomousFloodMonitorService.java) | Evaluates all 66 Delhi NCR recurring waterlogging hotspots periodically, persisting active forecast assessments. |
| **Weather Telemetry** | [`backend/.../service/WeatherService.java`](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/service/WeatherService.java) | Fetches real ECMWF IFS precipitation forecasts (6h sum, max hourly mm) from Open-Meteo with caching and missing-telemetry fallbacks. |
| **Terrain Profile** | [`backend/.../service/CopernicusDemService.java`](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/service/CopernicusDemService.java) | Retrieves Copernicus DEM GLO-30 elevation and depression profiles, providing contextual terrain provenance. |
| **Hotspot Intelligence** | [`backend/.../service/HotspotService.java`](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/service/HotspotService.java) | Maintains historical waterlogging records across Delhi, Noida, Gurugram, Ghaziabad, and Greater Noida. |
| **Route Evaluator** | [`backend/.../service/RouteService.java`](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/service/RouteService.java) | Evaluates polyline geometries against both observed reports and forecast hazard zones (`hazardType="PREDICTED"`), recommending safer detours. |
| **API Controller** | [`backend/.../web/ApiController.java`](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/web/ApiController.java) | Exposes `GET /api/flood-risk`, `GET /api/flood-risk/hotspots`, `POST /api/routes/evaluate`. |
| **Frontend Map Layer** | [`frontend/src/pages/MapPage.jsx`](file:///D:/jalmarg/jalmarg/frontend/src/pages/MapPage.jsx) | Renders distinct diamond-shaped pulsing radar markers for forecast risks alongside observed waterdrop icons. |
| **Frontend Routing** | [`frontend/src/pages/RoutePage.jsx`](file:///D:/jalmarg/jalmarg/frontend/src/pages/RoutePage.jsx) | Formats predicted hazards (`[Forecast Risk Spot]`), displays vehicle thresholds, and warns about unverified walking safety. |

---

## 3. Exact Risk Formula & Input Dependencies

### 3.1 Input Factors When Active Reports = 0
When `activeCount == 0`, JalMarg switches from `OBSERVED_FLOODING` to the autonomous predictive scoring pipeline:

$$\text{Composite Score} = \min(100, \text{Rain Points} + \text{Hotspot Points})$$

#### A. Rain Points (0 - 55 points)
Derived from Open-Meteo ECMWF IFS 6-hour precipitation sum ($\text{Rain}_{6h}$) and peak hourly intensity ($\text{Rain}_{\text{max}}$):
- **Very Heavy Rain** ($\text{Rain}_{6h} \ge 35\text{ mm}$ or $\text{Rain}_{\text{max}} \ge 15\text{ mm/h}$): **55 points**
- **Heavy Rain** ($\text{Rain}_{6h} \ge 15\text{ mm}$ or $\text{Rain}_{\text{max}} \ge 7.5\text{ mm/h}$): **40 points**
- **Moderate Rain** ($\text{Rain}_{6h} \ge 5\text{ mm}$ or $\text{Rain}_{\text{max}} \ge 2.5\text{ mm/h}$): **25 points**
- **Light Rain** ($\text{Rain}_{6h} > 0\text{ mm}$): **12 points**
- **Zero Rain** ($\text{Rain}_{6h} = 0\text{ mm}$): **0 points**

#### B. Historical Hotspot Susceptibility (0 - 30 points)
Calculated from historical occurrence frequency:
$$\text{Proneness Level } (1 \text{ to } 5) = \min(5, \max(1, \lfloor\text{Total Historical Reports} / 3\rfloor))$$
$$\text{Hotspot Points} = \min(30, \text{Proneness} \times 6) \quad (\text{Default } 5 \text{ points for unindexed points})$$

### 3.2 Risk Classification & Status
- **Score $\ge 76$:** `RiskLevel.CRITICAL` (Status: `PREDICTED_RISK`)
- **Score $51 - 75$:** `RiskLevel.HIGH` (Status: `PREDICTED_RISK`)
- **Score $26 - 50$:** `RiskLevel.MODERATE` (Status: `PREDICTED_RISK`)
- **Score $< 26$:** `RiskLevel.LOW` (Status: `NO_IMMEDIATE_RISK` if score $< 20$, else `PREDICTED_RISK`)

### 3.3 Copernicus DEM & Satellite Role (Scientific Integrity)
- **Role:** Copernicus DEM GLO-30 (30m resolution) elevation, depression depth, and NASA IMERG satellite availability are serialized in `factorBreakdown` under `provenance` as **verifiable contextual terrain telemetry**.
- **Audit Finding:** Copernicus DEM is **not** abused as an arbitrary mathematical multiplier for water depth, correctly preserving physical reality (a 30m DSM cannot measure centimeter-scale roadway puddle depth).

---

## 4. Evidence of Zero-Report Predicted-Risk Behavior

### Scenario A: High Forecast Rainfall at Recurring Hotspot (Minto Bridge Underpass)
- **Active Citizen Reports:** `0`
- **Simulated Forecast:** $45.0\text{ mm}$ in next 6h ($\text{Rain}_{\text{max}} = 20.0\text{ mm/h}$)
- **Hotspot Proneness:** Level $5/5$ ($18$ historical reports $\rightarrow 30$ hotspot points)
- **Result:**
  - `status`: `"PREDICTED_RISK"` (NOT `OBSERVED_FLOODING`)
  - `riskLevel`: `"CRITICAL"`
  - `riskScore`: `85` ($55 + 30$)
  - `confidence`: `0.70`
  - `summaryEn`: `"PREDICTED FLOOD RISK: Forecast of 45.0 mm rain next 6h at recurring waterlogging spot (proneness 5/5). Avoid low underpasses."`
  - `dataQuality`: `"FRESH"`

### Scenario B: Dry Weather at Recurring Hotspot (Nehru Place Outer Ring Road)
- **Active Citizen Reports:** `0`
- **Simulated Forecast:** $0.0\text{ mm}$ rain
- **Hotspot Proneness:** Level $1/5$ ($3$ historical reports $\rightarrow 6$ hotspot points)
- **Result:**
  - `status`: `"NO_IMMEDIATE_RISK"`
  - `riskLevel`: `"LOW"`
  - `riskScore`: `6`
  - `summaryEn`: `"Low flood risk (forecast 0.0 mm rain next 6h, no standing water reported)."`

### Scenario C: Missing Telemetry & No Reports
- **Active Citizen Reports:** `0`
- **Weather Feed:** Unreachable / Timed Out
- **Result:**
  - `status`: `"UNKNOWN"`
  - `riskLevel`: `"UNKNOWN"`
  - `riskScore`: `0`
  - `confidence`: `0.0`
  - `dataQuality`: `"MISSING_TELEMETRY"`
  - `summaryEn`: `"Telemetry unavailable: No current citizen reports and live weather feed unreachable."`

---

## 5. Route Comparison & Hazard Avoidance Results

When navigating across the city during high forecast precipitation without citizen reports:

| Route Option | Geometry Trajectory | Hazards Detected | Route Risk Score | Recommendation |
|---|---|---|---|---|
| **Route 0 (Primary)** | Direct route through Minto Bridge `[28.6340, 77.2260] -> [28.6350, 77.2265]` | **1 Hazard** (`hazardType="PREDICTED"`, `reportId=null`, `location="[Forecast Risk] Minto Bridge Underpass"`, `severity="HIGH"`) | **4.0** | **AVOID / HIGH HAZARD** |
| **Route 1 (Alternative Detour)** | Ring Road detour bypassing underpass `[28.6100, 77.2000] -> [28.6200, 77.2100]` | **0 Hazards** | **0.0** | **RECOMMENDED (Index: 1)** |

`RouteService.evaluate()` automatically ranks Route 1 as the recommended index (`recommendedIndex = 1`), successfully steering traffic away from predicted flood zones.

---

## 6. Frontend Verification Evidence

1. **Distinct Marker Styles:**
   - **Observed Reports:** Solid circular marker with waterdrop icon, displaying measured depth in feet.
   - **Predicted Risk:** Diamond-shaped pulsing radar marker with risk score badge (`🔮 Predicted Flood Risk`).
2. **Layer Controls:**
   - Toggling the `Predicted Flood Risk` layer on the map displays all unexpired autonomous hotspot predictions across Delhi NCR without requiring active reports.
3. **Telemetry Widget:**
   - Live Open-Meteo telemetry widget displays forecast rainfall, data freshness indicator, Copernicus DEM elevation, and satellite telemetry latency disclaimers.
4. **Mobile Responsiveness:**
   - Detour cards, hazard alerts, and pedestrian disclaimers (*"Pedestrian flood safety unverified by field sensors"*) adapt cleanly on mobile viewports.

---

## 7. Requirement Verification Scorecard

| Requirement | Test Method | Status | Details |
|---|---|---|---|
| **Zero-Report Risk Scoring** | Unit & Integration Test (`ZeroReportPredictionTest`) | **PASS** | Evaluates rain points + hotspot susceptibility when reports = 0 |
| **Strict Non-Observed Status** | Integration Test | **PASS** | Returns `PREDICTED_RISK`, never claims `OBSERVED_FLOODING` |
| **Zero-Report Route Avoidance** | Route Evaluation Integration Test | **PASS** | Flags forecast hazards (`reportId=null`) and recommends detour |
| **Missing Data Safety** | Telemetry Fallback Test | **PASS** | Returns `UNKNOWN` with zero synthetic data invention |
| **Demo Isolation Safety** | Demo Isolation Filter Test | **PASS** | `DEMO_ISOLATED_MODE=true` isolates mock data from real risk |
| **Backend Test Suite** | `mvn clean test` | **PASS** | **54/54 tests passed** (0 failures, 0 errors) |
| **Frontend Test Suite** | `node --test tests/stage2.test.mjs` | **PASS** | **17/17 tests passed** (0 failures) |
| **Frontend Production Build** | `npm run build` | **PASS** | Clean 421.93 kB Vite production bundle |

---

## 8. Remaining Scientific Limitations

1. **30m DSM Topography Resolution:** Copernicus DEM 30m grid cannot resolve micro-scale street curb height or stormwater drain inlet blockages.
2. **Heuristic Confidence:** Risk confidence ($70\%$) represents multi-factor environmental heuristic certainty, not frequentist statistical probability.
3. **Walking Safety:** Flood water velocity and submerged manhole covers cannot be determined without municipal IoT flow sensors; pedestrian navigation is labeled unverified.

---

## 9. Hackathon Live Demonstration Scenario

### "Zero-Report Monsoon Proactive Routing" Demo Flow
1. **Context:** A sudden heavy rainstorm (e.g., $40\text{ mm}$ forecast) is approaching Central Delhi, but the cloudburst just started and **zero commuters have submitted flood reports yet**.
2. **Step 1 — Open Live Map (`/`):**
   - Click the **"Predicted Flood Risk"** layer toggle.
   - Diamond radar markers appear over recurring waterlogging zones (e.g. Minto Bridge, Pul Prahladpur).
   - Click the marker: The **Predicted Flood Risk Card** shows **CRITICAL (Score: 85)**, citing $45\text{ mm}$ rainfall forecast and proneness level $5/5$.
3. **Step 2 — Safe Navigation (`/route`):**
   - Enter Origin: *Connaught Place* $\rightarrow$ Destination: *New Delhi Railway Station*.
   - Route 1 (fastest OSRM route through Minto Bridge underpass) is flagged with **[Forecast Risk Spot] Hazard Warning**.
   - JalMarg automatically selects and recommends **Alternative Route 2** (via Barakhamba / flyover detour), keeping commuters safe before cars stall in floodwater.
