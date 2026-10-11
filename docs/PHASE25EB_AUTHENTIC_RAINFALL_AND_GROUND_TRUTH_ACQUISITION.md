# JALMARG — PHASE 25E-B MASTER EXECUTION REPORT
## Authentic Historical Rainfall Acquisition, Official Flood Evidence, Verified Ground Truth & ML Dataset Readiness

**Timestamp:** 2026-10-11T15:25:00+05:30  
**Phase Identifier:** PHASE 25E-B  
**Author:** JalMarg Hydrometeorological & Geospatial Data Engineering Team  
**System Status:** `BLOCKED_UNVERIFIED_GROUND_TRUTH` (Predictive Supervised ML Gated; Operational Heuristic Hydrodynamic Risk Engine Active & Preserved)  
**Database Target:** `127.0.0.1:5433/jalmarg_twin` (PostGIS 16 Enabled; PostGIS Daemon Running)  
**CarSync Isolation Status:** Port `5432` strictly isolated; untouched and inaccessible.

---

## 1. EXISTING DATA INVENTORY PRIOR TO PHASE 25E-B

Prior to Phase 25E-B, an independent forensic audit conducted during Phase 25D revealed severe temporal and empirical gaps across the JalMarg repository:
- **Historical Rainfall Availability:** Only a single 31-day period was acquired (`delhi_ncr_historical_rainfall_2024-07-01_2024-07-31.json`, 8,184 hourly records).
- **Temporal Missingness:** All requested historical periods from June 2023, July 2023, August 2023, September 2023, June 2024, August 2024, September 2024, and subsequent monsoons (2025–2026) were missing.
- **Rainfall Feature Alignment:** Only 5 out of 24 historical ground-truth observations were temporally aligned with rainfall data (20.8% completeness). 19 observations completely lacked corresponding precipitation data.
- **Ground Truth Verifications:** 16 positive flood claims were classified as `EVIDENCE_PARTIALLY_VERIFIED` (supported only by secondary public advisory bulletins, lacking primary government PDFs); 8 dry-road reports were classified as `UNVERIFIED_MUNICIPAL_CLAIM`; all 16 depth claims were classified as `UNVERIFIED_CLAIM` due to absence of physical metrology sensor certificates.
- **Database Migrations:** Flyway V6 checksum mismatch was safely resolved under Phase 25E-A (`16961246`).

---

## 2. HISTORICAL COVERAGE TARGETS

The acquisition targets defined for Delhi NCR were:

| Period Code | Target Window | Target Priority | Hydrometeorological Justification |
|---|---|---|---|
| `2023-06` | 2023-06-01 to 2023-06-30 | High | Southwest monsoon onset in Delhi NCR |
| `2023-07` | 2023-07-01 to 2023-07-31 | Critical | Historical Yamuna river flood level peak (208.66 m) & severe urban waterlogging |
| `2023-08` | 2023-08-01 to 2023-08-31 | High | Sustained mid-monsoon rainfall events across NCR |
| `2023-09` | 2023-09-01 to 2023-09-30 | Medium | Late-monsoon receding convective storm events |
| `2024-06` | 2024-06-01 to 2024-06-30 | Critical | Extreme record single-day monsoon onset event (228.1 mm in 24 hours on June 28) |
| `2024-07` | 2024-07-01 to 2024-07-31 | Verify Existing | Historical monsoon deluge (Previously acquired in Phase 25A) |
| `2024-08` | 2024-08-01 to 2024-08-31 | Critical | Recurrent underpass inundation events across Minto Bridge, Zakhira, and Pul Prahladpur |
| `2024-09` | 2024-09-01 to 2024-09-30 | Medium | Late-monsoon drainage saturation |
| `2025-JJAS`| 2025-06-01 to 2025-09-30 | High | Full Southwest Monsoon Season 2025 (122 calendar days) |
| `2026-JJAS`| 2026-06-01 to 2026-09-30 | High | Most recent completed Southwest Monsoon Season 2026 (122 calendar days) |

---

## 3. ACTUAL DOWNLOADED & ACQUIRED DATASETS

All 10 target monsoon periods were acquired and verified using the authentic Copernicus ERA5-Land reanalysis archive:

| Filename | Records | Size | SHA-256 Digest | Status |
|---|---|---|---|---|
| `delhi_ncr_historical_rainfall_2023-06-01_2023-06-30.json` | 7,920 | 4.48 MB | `a3b7a4c7f078eef050c5ae9d06bfcae3933e46ca7934bb613e5e40e69ffc8f35` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2023-07-01_2023-07-31.json` | 8,184 | 4.63 MB | `3806de7d544cebf7a531e846067b85552dbb03cf338870845a7d3a0e10b14732` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2023-08-01_2023-08-31.json` | 8,184 | 4.63 MB | `73ef07a3c72b22bba45fe028b17b6a12ee097f4fa11ceb63f684cf04db0c5a38` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2023-09-01_2023-09-30.json` | 7,920 | 4.48 MB | `137484dfeb3d837ce74a38af11b98b0a9965d6cff8a4369df91dfc1477ea505d` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2024-06-01_2024-06-30.json` | 7,920 | 4.48 MB | `a7b6301389aaec459e7f4153965561a7a0304a0808a2ffca4be4c194bb10022d` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2024-07-01_2024-07-31.json` | 8,184 | 4.63 MB | `a61860e65d83a1b3bebfb99a66ce9dc62a22f7787363486c3da98533b664d6db` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2024-08-01_2024-08-31.json` | 8,184 | 4.63 MB | `95b6b8d2cc68ffc6a2d9fc516cba06ae887642674e37da09f78328bfb4eb54a2` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2024-09-01_2024-09-30.json` | 7,920 | 4.48 MB | `10564cc2b6fa8fdf457d5910fa95a9446d790404c0ec28dfef35fe63db37ebf8` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2025-06-01_2025-09-30.json` | 32,208 | 18.23 MB | `96865a9db4f33100657c7c3c04d0ea2b91d29388147d34b2283cb0715efbf20e` | Ingested & Validated |
| `delhi_ncr_historical_rainfall_2026-06-01_2026-09-30.json` | 32,208 | 18.23 MB | `d862c8034db1b13824fba59560f734ee2623a49280d859fa42c43ee4a6210f4d` | Ingested & Validated |
| **Total Cumulative Records** | **128,832** | **72.89 MB** | — | **100% Complete** |

---

## 4. SOURCE URLS, ACCESS EVALUATIONS & DOCUMENTATION

### Priority A: India Meteorological Department (IMD) AWS Network
- **Source Description:** Automatic Weather Station (AWS) network operated by the Ministry of Earth Sciences (MoES), Government of India.
- **Evaluation:** Evaluated publicly accessible URLs and official portal endpoints (`aws.imd.gov.in`).
- **Access Status:** `BLOCKED_OFFICIAL_ACCESS`.
- **Finding:** Access to hourly time-series station records requires formal institutional/ministry authentication and non-disclosure agreements. Public web portal blocks automated scraping, and credentials are not provisioned in the repository. As per non-negotiable directive, no authenticated portal was scraped and no synthetic station observations were manufactured.

### Priority B: Copernicus ERA5-Land Direct Climate Data Store (CDS) API
- **Source Description:** ECMWF Climate Data Store direct programmatic API (`cds-api`).
- **Dataset Identifier:** `reanalysis-era5-land`.
- **Evaluation:** Inspected local configuration environment for CDS credentials (`~/.cdsapirc`).
- **Access Status:** `BLOCKED_OFFICIAL_ACCESS`.
- **Finding:** Headless automated fetching requires dedicated `CDS_API_KEY` and acceptance of Copernicus license terms via personal ECMWF account. Without provisioned CDS credentials, direct programmatic batching fails closed.

### Priority C: Open-Meteo Historical Weather Archive
- **Source Description:** Open-Meteo Historical Weather Archive API providing direct programmatic access to ECMWF ERA5-Land reanalysis models.
- **Endpoint:** `https://archive-api.open-meteo.com/v1/archive`
- **Model / Reanalysis Dataset:** ECMWF ERA5-Land (hourly 0.1° resolution / ~9 km spatial grid).
- **Access Status:** `ACQUIRED_AND_VALIDATED`.
- **License:** Creative Commons Attribution 4.0 International (CC-BY-4.0), permitting open academic, commercial, and operational processing with attribution.

---

## 5. GEOGRAPHIC COVERAGE & SPATIAL SAMPLING GRID

The spatial sampling grid spans the entire monitored Delhi NCR territory across 6 administrative zones and 11 critical corridors:

| Corridor Code | Corridor Description | Latitude | Longitude | Administrative Zone |
|---|---|---|---|---|
| `DEL-CP-MINTO` | Connaught Place - Minto Road Underpass | 28.6331 | 77.2208 | Central Delhi |
| `DEL-ROH-ZAKH` | Rohtak Road - Zakhira Underpass | 28.6652 | 77.1612 | West Delhi |
| `DEL-MBR-PRAH` | Mehrauli-Badarpur Road - Pul Prahladpur Underpass | 28.5085 | 77.2842 | South Delhi |
| `DEL-GTR-AZAD` | GT Road - Azadpur Underpass | 28.7073 | 77.1788 | North Delhi |
| `DEL-RING-DHK` | Ring Road - Dhaula Kuan Junction | 28.5921 | 77.1617 | New Delhi |
| `DEL-VIK-LAXM` | Vikas Marg - Laxmi Nagar Corridor | 28.6304 | 77.2776 | East Delhi |
| `GGN-NH48-HERO`| NH-48 - Hero Honda Chowk Underpass | 28.4357 | 77.0135 | Gurugram |
| `NOI-EXP-S18`  | Noida Expressway - Sector 18 Underpass | 28.5708 | 77.3261 | Noida |
| `GNO-EXP-PARI` | Greater Noida Expressway - Pari Chowk | 28.4639 | 77.5098 | Greater Noida |
| `GZB-NH09-INDR`| NH-09 - Indirapuram / CISF Cut | 28.6438 | 77.3712 | Ghaziabad |
| `FBD-MATH-BADR`| Mathura Road - Badarpur Border / NH-19 | 28.4982 | 77.3025 | Faridabad |

Spatial resolution is maintained at the native reanalysis grid cell level ($0.1^\circ \times 0.1^\circ$). Individual point locations within the same grid cell are not misrepresented as independent high-resolution observations.

---

## 6. RAINFALL DATA QUALITY AUDIT & VALIDATION

A strict automated quality audit was executed against all 128,832 records across 16 scientific validation checkpoints:

1. **File Readability:** 10 / 10 files parsed without JSON corruption.
2. **Actual Source Provenance:** Fully attributed to Open-Meteo ERA5-Land Reanalysis backend.
3. **Coordinate Reference System:** WGS 84 (EPSG:4326) across all points.
4. **Coordinate Bounds:** 100% within bounding box $[28.40^\circ\text{N}, 28.75^\circ\text{N}] \times [77.00^\circ\text{E}, 77.55^\circ\text{E}]$.
5. **Start and End Timestamps:** Exact hourly alignment with target monsoon period boundaries.
6. **Timezone:** UTC standard with ISO-8601 formatting.
7. **Units:** Precipitation in millimeters ($mm$).
8. **Temporal Frequency:** Strictly regular 1-hour intervals ($\Delta t = 3600\text{ s}$).
9. **Duplicate Timestamps:** 0 duplicates identified.
10. **Missing Timestamps:** 0 temporal gaps across all 10 periods.
11. **Missing Values (`null`):** 0 missing values.
12. **Physically Implausible Values:** Maximum hourly rate detected: 84.6 mm/h (physically plausible severe convective event).
13. **Negative Precipitation:** 0 negative values (`min = 0.00 mm`).
14. **Accumulation Semantics:** Point hourly reanalysis accumulation verified.
15. **Data Coverage Completeness:** 100.0%.
16. **Spatial Coverage Completeness:** 11 of 11 corridors represented.

**Quality Verdict:** `VALIDATED_NO_MISSING` across all 10 periods. No silent zero-filling was performed.

---

## 7. RAINFALL-TO-EVENT ALIGNMENT (BEFORE VS AFTER)

The historical feature aligner was executed across all 24 historical ground-truth observations using the expanded ERA5-Land reanalysis archive:

### Alignment Comparison Summary:
- **Phase 25D Baseline:**
  - Aligned Observations: 5 / 24 (20.8%)
  - Missing Rainfall Data: 19 / 24 (79.2%)
- **Phase 25E-B Current:**
  - Aligned Observations: **24 / 24 (100.0%)**
  - Missing Rainfall Data: **0 / 24 (0.0%)**

### Multi-Horizon Antecedent Precipitation Windows:
Features computed without future-data leakage ($t \le T_0$):
- $R_{1h}$: Precipitation in preceding 1 hour
- $R_{3h}$: Precipitation in preceding 3 hours
- $R_{6h}$: Precipitation in preceding 6 hours
- $R_{24h}$: Precipitation in preceding 24 hours
- $\text{API}$: Antecedent Precipitation Index ($\sum_{d=1}^{14} k^d \cdot P_d, k=0.85$)

All 24 records in `ml_ground_truth_observations` now store validated antecedent precipitation values.

---

## 8. GROUND-TRUTH TRAINING ELIGIBILITY CENSUS

Despite 100% rainfall alignment, the ground-truth training gate strictly prohibits supervised model training:

| Ground-Truth Label Type | Actual Count | Required Threshold | Deficit | Status |
|---|---|---|---|---|
| Evidence-Verified Positive Flood Events | **0** | 50 | 50 | 16 historical claims remain `PARTIALLY_VERIFIED` (public bulletins only; primary government PDFs pending) |
| Evidence-Verified Dry-Road Negatives | **0** | 50 | 50 | 8 municipal patrol claims remain `UNVERIFIED` (awaiting supervisory photo review) |
| Calibrated Physical Depth Labels | **0** | 30 | 30 | 16 depth claims remain `UNVERIFIED_CLAIM` (awaiting metrology sensor calibration logs) |
| Rainfall Aligned Observations | **24** | 24 | 0 | 100% complete |
| Monsoon Seasons Covered | **2** | 2 | 0 | Complete (2023 & 2024 ground truth event baseline seasons) |

**Overall Gate Verdict:** `BLOCKED_UNVERIFIED_GROUND_TRUTH`

---

## 9. MUNICIPAL DRY-ROAD INSPECTION WORKFLOW (GENUINE NEGATIVES)

Implemented an authenticated municipal dry-road inspection workflow:
- **Field Inspector Submission:** Records inspector name, designation, municipal agency, corridor, precise coordinates, timestamp, surface condition (`DRY`), and photographic evidence URL.
- **Pending Review State:** Newly submitted inspections are created in `PENDING_REVIEW` status and cannot enter the training census automatically.
- **Supervisory Audit Trail:** Only authorized municipal supervisors (`MUNICIPAL_ADMIN` or `MUNICIPAL_OPERATOR`) can verify inspections as `VERIFIED_NEGATIVE` after reviewing photographic proof.

---

## 10. CALIBRATED PHYSICAL DEPTH MEASUREMENT WORKFLOW

Implemented an evidence-supported physical depth measurement registry:
- **Physical Metrology Registration:** Supports staff gauges, ultrasonic stage sensors, surveyed level rods, and radar water level sensors.
- **Calibration Trail:** Enforces mandatory instrument identifier, certified metrology calibration document reference, and physical measurement uncertainty ($\pm \text{m}$).
- **Review Lifecycle:** Records enter as `PENDING_CALIBRATION_REVIEW` and transition to `CALIBRATED_GAUGE` only upon hydrological verification.

---

## 11. POSTGIS DATABASE & SPRING BOOT INTEGRATION

### Database Migration:
- Created and validated `V7__rainfall_coverage_and_inspection_workflows.sql` on `127.0.0.1:5433/jalmarg_twin`.
- Tables added:
  - `ml_rainfall_coverage_manifest`: Tracks ingested datasets, date boundaries, records, and SHA-256 hashes.
  - `ml_municipal_dry_road_inspections`: Stores field inspections with PostGIS spatial point geometries.
  - `ml_calibrated_depth_measurements`: Stores physical depth records with calibration metadata and point geometries.

### Spring Boot REST Endpoints:
- `GET /api/v1/ml/rainfall/coverage`: Returns all multi-period rainfall coverage manifests.
- `GET /api/v1/ml/rainfall/summary`: Returns aggregate coverage statistics.
- `GET /api/v1/ml/inspections/dry-road`: Lists dry-road field inspections.
- `POST /api/v1/ml/inspections/dry-road`: Logs a new municipal inspection (`PENDING_REVIEW`).
- `POST /api/v1/ml/inspections/dry-road/{id}/review`: Supervisory review endpoint.
- `GET /api/v1/ml/depth-measurements`: Lists calibrated depth measurements.
- `POST /api/v1/ml/depth-measurements`: Registers a new physical depth measurement.
- `POST /api/v1/ml/depth-measurements/{id}/review`: Metrology review endpoint.

---

## 12. REACT MUNICIPAL AUTHORITY DASHBOARD

Extended `frontend/src/pages/AuthorityPage.jsx` with a dedicated **📡 Authentic Data Acquisition & Verification** tab:
1. **Hydrometeorological Source Evaluations:** Transparently displays status of IMD AWS (`BLOCKED_OFFICIAL_ACCESS`), Copernicus CDS (`BLOCKED_OFFICIAL_ACCESS`), and Open-Meteo ERA5-Land (`ACQUIRED_AND_VALIDATED`).
2. **Alignment Before vs After Banner:** Highlights improvement from 20.8% (5/24) to 100.0% (24/24).
3. **Multi-Period Coverage Table:** Displays all 10 acquired monsoon datasets with records, resolution, SHA-256 digests, and quality statuses.
4. **Dry-Road Inspection Ledger & Form:** Interactive modal allowing field officers to log patrol inspections and supervisors to review photographic evidence.
5. **Calibrated Depth Registry & Form:** Metrology logging modal with instrument ID and calibration document validation.
6. **Supervised ML Readiness Blockers Card:** Displays explicit gate blockers (`BLOCKED_UNVERIFIED_GROUND_TRUTH`) and affirms the active heuristic risk engine.
7. **Full English / Hindi Localization:** Complete bilingual string parity in `stage2Strings.js`.

---

## 13. ACTUAL TEST RESULTS

### A. Python Pytest Test Suite:
- **Command:** `pytest -v ml/tests`
- **Result:** **41 passed, 0 failed** (including 4 dedicated tests in `test_phase25eb_rainfall_pipeline.py`).

### B. Java Backend Maven Test Suite:
- **Command:** `mvn test`
- **Result:** **111 passed, 0 failed** (`BUILD SUCCESS`, including 5 dedicated tests in `Phase25EbRainfallAndInspectionTest.java`).

### C. Frontend Node.js Test Suite:
- **Command:** `node --test tests/phase25*.mjs tests/stage2.test.mjs`
- **Result:** **41 passed, 0 failed** (including 8 dedicated tests in `phase25eb_rainfall_acquisition.mjs`).

---

## 14. REMAINING EXTERNAL DEPENDENCIES & BLOCKERS

1. **IMD Official Station Observations:** Blocked pending institutional MoES data-sharing authorization.
2. **Primary Government Flood Event PDFs:** 16 historical flood events require primary PWD/MCD document verification to transition from `PARTIALLY_VERIFIED` to `EVIDENCE_VERIFIED`.
3. **Field Photographic Verification:** Municipal dry-road inspections require field patrol execution and supervisory photo review to yield genuine verified negatives.
4. **Physical Sensor Calibration Certificates:** In-situ depth sensors require accredited metrology calibration certificates to yield certified depth labels.

---

## 15. RECOMMENDATIONS FOR PHASE 25F

1. **Formal Institutional Ministry Outreach:** Initiate institutional request to IMD/MoES for access to hourly AWS rainfall station feeds across Delhi NCR.
2. **Municipal Field Patrol Mobilization:** Mobilize PWD and MCD field teams to log photographic dry-road inspections during scheduled dry-weather monitoring routes.
3. **Underpass Staff Gauge Surveying:** Conduct an in-situ surveying campaign across all 11 monitored underpass corridors to calibrate physical staff gauges against national vertical datums.
4. **Maintain Gated Safety Invariant:** Keep supervised predictive machine learning models strictly gated until the ground-truth census achieves 50 verified positives, 50 verified negatives, and 30 calibrated depths.
