# JalMarg — Hackathon Demonstration Runbook

**Audience:** Hackathon Jury, Municipal Stakeholders & Technical Evaluators  
**Application URL:** `http://127.0.0.1:5173`  
**Authority Console:** `http://127.0.0.1:5173/authority`  
**API Health Status:** `http://127.0.0.1:8080/api/health`  
**Primary Language Support:** English / Hindi (हिन्दी)  

---

## 1. Problem Statement: Urban Monsoon Gridlock in Delhi NCR

During the Indian monsoon, Delhi NCR faces repeated paralysis from urban waterlogging:
- **Severe Underpass Inundation:** Locations like Minto Bridge, Pul Prahladpur, and Zakhira frequently accumulate 2 to 4 feet of stagnant storm runoff within 45 minutes of heavy convective showers.
- **Commuter Peril:** Two-wheelers and passenger vehicles stall or submerge, leading to catastrophic multi-hour traffic gridlocks across arterial corridors.
- **Municipal Information Delay:** PWD, MCD, and Traffic Police often rely on delayed phone complaints or social media reports, resulting in uncoordinated pump deployments and barricading.
- **Generic Navigation Blindness:** Commercial navigation applications typically measure only vehicular speed slowdowns rather than physical water accumulation depths, frequently routing unsuspecting drivers directly into flooded underpasses.

---

## 2. The JalMarg Solution

**JalMarg** ("Water Pathway") provides an integrated real-time geospatial intelligence system:
1. **Low-Friction Citizen Crowdsourcing:** Citizens report localized water depths using calibrated visual anatomical markers (*Ankle / Shin / Knee / Waist*) with optional photographic proof.
2. **Municipal Verification & Auditable Action:** PWD/MCD operators review crowdsourced reports, dispatch drainage pumps, and record immutable audit logs.
3. **Multi-Source Heuristic Digital Twin:** Integrates live rainfall (Open-Meteo ECMWF/ERA5), 30-meter terrain elevation (Copernicus DEM), historical drainage vulnerabilities, and active citizen ground truth onto an interactive PostGIS Leaflet map.
4. **Hazard-Aware Route Guidance:** Proactively evaluates travel corridors, alerts drivers to detected waterlogging hazards, and recommends safer bypass routes with honest probabilistic exposure ratings.

---

## 3. The 5-Minute Master Demonstration Flow

### [Minute 0:00 - 0:45] Act I: Setting the Scene & The Public Digital Twin
- **URL:** Open `http://127.0.0.1:5173/`
- **Script:**
  > *"Every monsoon, Delhi commuters face underwater underpasses like Minto Bridge. Welcome to JalMarg — an open geospatial digital twin for urban waterlogging resilience in Delhi NCR."*
- **Action:**
  - Pan across Delhi NCR map.
  - Point out known chronic hotspots (Minto Bridge, Pul Prahladpur, Kashmiri Gate).
  - Toggle the language switch (`हिन्दी` / `EN`) in the top navigation bar to showcase full bilingual accessibility for Indian citizens.
  - Highlight the heuristic risk badge: *"Real-time multi-source waterlogging risk calculation."*

---

### [Minute 0:45 - 2:00] Act II: Citizen Waterlogging Report Submission
- **URL:** Click **"Report Waterlogging"** (`http://127.0.0.1:5173/report`)
- **Script:**
  > *"Imagine a commuter stranded near Minto Bridge. In under 15 seconds, they can report ground-truth conditions."*
- **Action:**
  - Select location: **Minto Bridge Underpass** (lat: `28.6347`, lng: `77.2263`).
  - Select Depth Marker: Click **Knee Level (~1.5 ft)**. Explain why anatomical markers work better for non-technical citizens than raw metric measurements.
  - Add Note: *"Heavy waterlogging accumulating near the underpass entrance."*
  - Click **Submit Report**.
  - Show the confirmation screen: Report created with status `UNVERIFIED`.
  - Explain the safety rule: JalMarg *never* automatically assumes citizen reports are ground truth without municipal review or multi-report clustering.

---

### [Minute 2:00 - 3:15] Act III: Municipal Authority Console & Verification
- **URL:** Navigate to **"Authority Console"** (`http://127.0.0.1:5173/authority`)
- **Credentials:**
  - Username: `operator_pwd`
  - Password: `[REDACTED_MUNICIPAL_CREDENTIAL]` (Provided via secure local environment)
- **Script:**
  > *"Now we switch perspectives to the PWD Drainage Operations Control Room."*
- **Action:**
  - Show authenticated operator profile: `operator_pwd (PWD Central Drainage)`.
  - Point to the active review queue showing the pending Minto Bridge report.
  - Click **Review Report** and inspect reported depth (1.5 ft) and timestamp.
  - Click **Verify & Escalate**.
  - Show status update to `VERIFIED`.
  - Mention architectural integrity: Every verification action creates an immutable record in `twin_audit_events` with actor attribution, timestamp, and IP address.

---

### [Minute 3:15 - 4:15] Act IV: Hazard-Aware Route Evaluation
- **URL:** Navigate to **"Route Planner"** (`http://127.0.0.1:5173/route`)
- **Script:**
  > *"Now, how does this prevent commuters from entering flooded roads? Let's check our hazard-aware routing engine."*
- **Action:**
  - Set Origin: **Connaught Place** (`28.6320, 77.2190`).
  - Set Destination: **New Delhi Railway Station** (`28.6420, 77.2220`).
  - Travel Mode: Select **Two-Wheeler (Motorcycle)**.
  - Click **Evaluate Routes**.
  - **Show Route Comparison:**
    - **Direct Route (via Minto Bridge):** Highlighted with red hazard indicator. Flagged as *Unsafe for Two-Wheelers* due to 1.5 ft water depth.
    - **Alternative Route (via Outer Circus Bypass):** Highlighted in green with 0 detected water hazards.
  - Point to the engine recommendation:
    > *"Estimated lower waterlogging exposure. Follow official Delhi Traffic Police advisories."*

---

### [Minute 4:15 - 5:00] Act V: Scientific Integrity & Hackathon Verdict
- **Script:**
  > *"In JalMarg, we hold high standards of scientific honesty:
  > - We do not claim calibrated hydrodynamic flood predictions — our risk scores (0-100) are honest multi-source heuristics.
  > - We do not invent fake telemetry; our data combines Copernicus DEM, Open-Meteo ERA5 rainfall, and verified citizen observations.
  > - Our complete stack is live, backed by PostGIS on port 5433, Spring Boot, and React.
  > Thank you!"*

---

## 4. The 2-Minute Rapid Fallback Flow

If presentation time is constrained to 120 seconds:

| Time | Screen | Key Narration & Action |
|---|---|---|
| **0:00 - 0:30** | Public Map (`/`) | Pan Delhi map. Show Minto Bridge hotspot and bilingual toggle. |
| **0:30 - 1:00** | Citizen Report (`/report`) | Submit 1-click report at Minto Bridge with "Knee Level" depth. |
| **1:00 - 1:30** | Authority Console (`/authority`) | Login as `operator_pwd`. Show incoming report in queue and click "Verify". |
| **1:30 - 2:00** | Route Planner (`/route`) | Click "Evaluate Route". Show direct route flagged red (unsafe) and bypass route recommended in green. Conclude with impact statement. |

---

## 5. Live Demo Account Matrix

| Account Role | Username | Password Status | Department / Clearance | Permitted Actions |
|---|---|---|---|---|
| **Municipal Operator** | `operator_pwd` | `[PROVISIONED_BCRYPT]` | PWD Central Drainage | Review, Verify, Reject citizen reports, dispatch pumps |
| **Municipal Viewer** | `viewer_traffic` | `[PROVISIONED_BCRYPT]` | Delhi Traffic Police | View dashboards and audit logs; cannot mutate state |
| **Municipal Admin** | `admin_ndmc` | `[PROVISIONED_BCRYPT]` | NDMC Control Room | Full administrative & diagnostic access |

*Security Notice: Do not project passwords or JWT tokens during screen shares.*

---

## 6. Pre-Demo Checklist & Verification

Before presenting to the jury, run this single terminal command to verify end-to-end platform health:

```powershell
node scripts/execute_live_demo_workflow.mjs
```

Expected Output:
```
ALL DEMO WORKFLOW PHASES COMPLETED WITH 100% SUCCESS!
Disposable demo test report cleanly removed from database.
```

---

## 7. Key Talking Points & Scientific Disclaimers

1. **"Why not use AI computer vision directly?"**
   - JalMarg supports computer vision models, but maintains an explicit Dual-Threshold Abstention Policy: blurry, low-light, or ambiguous images are rejected rather than guessed.
2. **"Are routes guaranteed safe?"**
   - No route in extreme weather is guaranteed safe. JalMarg explicitly displays: *"Estimated lower waterlogging exposure"*, maintaining deference to official Delhi Traffic Police advisories.
3. **"Where does the rainfall data come from?"**
   - Authentic hourly data from Open-Meteo ECMWF/ERA5 reanalysis and IMD gridded datasets, providing verifiable meteorological provenance.
