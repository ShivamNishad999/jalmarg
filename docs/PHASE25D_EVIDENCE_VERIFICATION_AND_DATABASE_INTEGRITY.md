# JALMARG — PHASE 25D COMPREHENSIVE AUDIT REPORT
## Ground-Truth Evidence Verification, Historical Data Forensics, Flyway Integrity Repair & ML Trustworthiness Audit

**Audit Date:** 2026-10-11  
**Project Root:** `D:\jalmarg\jalmarg`  
**Target Database:** `127.0.0.1:5433/jalmarg_twin` (PostgreSQL 17.11 + PostGIS 3.6.2)  
**Isolated Database:** `127.0.0.1:5432` (CarSync — strictly isolated; untouched, unmodified, unqueried)  
**Auditor Roles:** Senior Data Provenance Auditor, Geospatial Data Scientist, Hydrology Data Verification Specialist, PostgreSQL/PostGIS Database Engineer, Spring Boot Security Architect, Computer Vision Validation Engineer, Independent QA Lead  

---

## 1. BASELINE REPOSITORY & DATABASE AUDIT

Prior to executing any remediation or code modifications, a full baseline inspection of the repository and database was performed:

| Component | Status | Actual State |
| :--- | :--- | :--- |
| `docs/PHASE25A_AUTHENTIC_DATASET_AND_ML_FOUNDATION.md` | Present | Documents preliminary climate data foundation & gate logic. |
| `docs/PHASE25B_IRDID_COMPUTER_VISION.md` | Present | Documents 1,049 IRDID images and LightRoadWaterNet architecture. |
| `docs/PHASE25C_GROUND_TRUTH_AND_ML_RELIABILITY.md` | Present | Reported 16 positive / 8 negative / 16 depths / 2 seasons; unverified claims. |
| `backend/pom.xml` | Present | Java 21, Spring Boot 3.3.4, Flyway 10.17.3, PostGIS JDBC, test dependencies. |
| Flyway Migrations V1–V5 | Present | V1 Baseline, V2 Geospatial, V3 Auth, V4 ML Foundation, V5 CV Enrichment. |
| Flyway Migration V6 | Present | `V6__ground_truth_and_ml_reliability.sql` on disk; checksum mismatch in DB. |
| Target PostGIS Database | Verified Active | Port 5433, `jalmarg_twin`. PostGIS 3.6.2 extension active. |
| CarSync Database | Strict Isolation | Port 5432 was strictly untouched and unqueried. |
| `ml/src/jalmarg_ml/acquisition/` | Present | Authentic fetchers (Copernicus DEM, IMD, ERA5, Open-Meteo). |
| `ml/src/jalmarg_ml/labeling/` | Present | Spatiotemporal deduplication, heuristic negative inspection tools. |
| `ml/src/jalmarg_ml/features/` | Present | Strict antecedent window aggregations with $t \le T_0$ leakage prevention. |
| `ml/src/jalmarg_ml/vision/` | Present | PyTorch LightRoadWaterNet, dual-threshold abstention $[0.35, 0.65]$. |
| `ml/scripts/sync_ground_truth_and_reliability.py` | Present | Synchronized 24 observations into PostgreSQL 17. |
| `ml/artifacts/acquired_data/` | Inspected | **Only July 2024 rainfall data exists on disk (`2024-07-01_2024-07-31`). No 2023 or June 2024 rainfall data present.** |
| Municipal RBAC & Audit Trail | Present | BCrypt passwords, role hierarchy (`ADMIN`, `OPERATOR`, `ANALYST`). |
| React Authority Dashboard | Present | Bilingual English/Hindi UI with tabs for Queue, Incidents, Twin, ML, Ground Truth. |

---

## 2. HISTORICAL SOURCE VERIFICATION METHODOLOGY

Claims of historical waterlogging cannot be verified by citations or report narratives alone. A rigorous 13-point forensic protocol was applied to every claimed observation:

1. **Original Publisher / Authority:** Legal public body or agency issuing the statement.
2. **Exact Original URL or Document Identifier:** Official bulletin number, gazette entry, or permanent public archive URL.
3. **Accessible Source Document:** Physical PDF, scan, or authorized local file on disk.
4. **Publication / Issuance Timestamp:** Formal release date and time of the publication.
5. **Actual Event Timestamp:** Physical occurrence time of waterlogging or dry inspection.
6. **Location & Geographic Precision:** Street name, underpass name, and GPS coordinates with uncertainty radius.
7. **Exact Text Passage:** Verbatim excerpt from the authority document reporting standing water or dry conditions.
8. **Measurement Type:** Distinction between traffic congestion mention, flood report, and calibrated depth measurement.
9. **Direct vs. Secondary:** Primary direct field observation vs secondary media broadcast.
10. **Data License:** Permitted use, public record terms, or copyright constraints.
11. **File Checksum:** SHA-256 hash of the authoritative source file.
12. **Acquisition Timestamp:** ISO-8601 acquisition log entry.
13. **Verification Outcome:** Standardized state classification.

### Standardized Evidence Verification States
- `EVIDENCE_VERIFIED`: Primary source document or calibrated instrument file verified on disk with authority signature/hash.
- `EVIDENCE_PARTIALLY_VERIFIED`: Real public deluge corroborated by official news bulletins, but raw PDF/sensor files are missing from local repository.
- `EVIDENCE_UNVERIFIED`: Municipal assertion or internal claim lacking external corroborating records or auditor signatures.
- `EVIDENCE_CONTRADICTED`: Record contradicted by physical elevation, gauge, or photographic counter-evidence.
- `EVIDENCE_ACCESS_BLOCKED`: Source document exists behind inaccessible municipal intranet or firewall.
- `EVIDENCE_SOURCE_MISSING`: Source entity or reference completely unknown or unrecorded.

---

## 3. EXACT SOURCE URLS & DOCUMENT IDENTIFIERS AUDIT

| Authority Entity | Claimed Reference | Genuinely Available on Disk / Public Web | Audit Finding |
| :--- | :--- | :--- | :--- |
| Delhi PWD Flood Control Room | `PWD-FCR-2023-07-10-BULLETIN-04` | **No raw PDF on disk**; reference is a citation claim. | `EVIDENCE_PARTIALLY_VERIFIED` |
| Delhi Traffic Police (DTP) | `DTP-TRAFFIC-ADV-2023-07-13-99` | **No raw advisory PDF/tweet archive on disk**. | `EVIDENCE_PARTIALLY_VERIFIED` |
| Municipal Corporation of Delhi | `MCD-HQ-DISASTER-CELL-2024-06-28` | **No raw bulletin PDF on disk**. | `EVIDENCE_PARTIALLY_VERIFIED` |
| NDMC Command Centre | `NDMC-CCC-LOG-2024-08-01-WATERLOG` | **No raw telemetry log file on disk**. | `EVIDENCE_PARTIALLY_VERIFIED` |
| JalMarg Municipal Inspection | `INSP-NEG-2024-07-15-01` to `08` | Internal assertion in database; **zero field photos or GPS trace files**. | `EVIDENCE_UNVERIFIED` |

**Forensic Rule Enforced:** Never manufacture source URLs, document IDs, or checksums. Missing primary source files remain recorded as missing.

---

## 4. OBSERVATION-BY-OBSERVATION EVIDENCE AUDIT RESULTS

All 24 database observations in `ml_ground_truth_observations` were audited against repository evidence files:

| Observation ID | Corridor | Status | Claimed Depth | Evidence Status | Depth Status | Rainfall Status | Training Eligibility | Audit Rationale |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `HIST-2023-DEL-01` | Minto Road | Waterlogged | 1.80 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Public deluge corroborated by news; raw PWD PDF missing; 2023 rainfall missing; uncalibrated depth. |
| `HIST-2023-DEL-02` | Tilak Bridge | Waterlogged | 0.90 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Corroborated by DTP bulletin citation; raw PDF missing; 2023 rainfall missing; uncalibrated depth. |
| `HIST-2023-DEL-03` | Ring Road / Yamuna | Waterlogged | 1.20 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Yamuna overflow well documented in press; raw gazette missing; 2023 rainfall missing. |
| `HIST-2023-DEL-04` | Pul Prahladpur | Waterlogged | 1.50 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Underpass inundation corroborated; raw PWD record missing; 2023 rainfall missing. |
| `HIST-2023-DEL-05` | Zakhira Underpass | Waterlogged | 1.10 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Rail-under-bridge waterlogging corroborated; primary file missing; 2023 rainfall missing. |
| `HIST-2023-DEL-06` | Azadpur Underpass | Waterlogged | 0.85 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Corroborated by DTP advisory; raw file missing; 2023 rainfall missing. |
| `HIST-2023-DEL-07` | Kashmere Gate ISBT | Waterlogged | 1.40 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | ISBT flooding corroborated; raw PWD log missing; 2023 rainfall missing. |
| `HIST-2023-DEL-08` | Pragati Maidan Tunnel | Waterlogged | 0.75 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Tunnel closure corroborated; raw ITPO/PWD log missing; 2023 rainfall missing. |
| `HIST-2024-DEL-01` | Minto Road | Waterlogged | 1.25 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | June 28 extreme rainfall event corroborated; raw PDF missing; June 2024 rainfall missing. |
| `HIST-2024-DEL-02` | AIIMS Flyover | Waterlogged | 0.60 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Corroborated by traffic advisory; raw PDF missing; June 2024 rainfall missing. |
| `HIST-2024-DEL-03` | Dhaula Kuan | Waterlogged | 0.70 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Corroborated by traffic advisory; raw PDF missing; June 2024 rainfall missing. |
| `HIST-2024-DEL-04` | ITO Junction | Waterlogged | 0.80 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | ITO drainage backflow corroborated; raw MCD log missing; June 2024 rainfall missing. |
| `HIST-2024-DEL-05` | Connaught Place | Waterlogged | 0.50 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | August 1 deluge corroborated; raw NDMC log missing; August 2024 rainfall missing. |
| `HIST-2024-DEL-06` | Lajpat Nagar | Waterlogged | 0.65 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Corroborated by civic reports; raw file missing; August 2024 rainfall missing. |
| `HIST-2024-DEL-07` | South Ext Ring Road | Waterlogged | 0.75 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | Corroborated by civic reports; raw file missing; August 2024 rainfall missing. |
| `HIST-2024-DEL-08` | Delhi Airport T1 | Waterlogged | 1.10 m | `PARTIALLY_VERIFIED` | `DEPTH_UNVERIFIED` | `MISSING_DATA` | `EXCLUDED` | June 28 airport inundation corroborated; raw DIAL/AAI log missing; June rainfall missing. |
| `INSP-NEG-2024-01` | Minto Road | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `ALIGNED` | `EXCLUDED` | Municipal dry assertion; lacks field photo, GPS breadcrumb, or auditor counter-signature. |
| `INSP-NEG-2024-02` | Tilak Bridge | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `ALIGNED` | `EXCLUDED` | Municipal dry assertion; lacks field photo, GPS breadcrumb, or auditor counter-signature. |
| `INSP-NEG-2024-03` | Pragati Maidan Tunnel | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `ALIGNED` | `EXCLUDED` | Municipal dry assertion; lacks field photo, GPS breadcrumb, or auditor counter-signature. |
| `INSP-NEG-2024-04` | AIIMS Flyover | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `ALIGNED` | `EXCLUDED` | Municipal dry assertion; lacks field photo, GPS breadcrumb, or auditor counter-signature. |
| `INSP-NEG-2024-05` | Dhaula Kuan | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `ALIGNED` | `EXCLUDED` | Municipal dry assertion; lacks field photo, GPS breadcrumb, or auditor counter-signature. |
| `INSP-NEG-2024-06` | ITO Junction | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `MISSING_DATA` | `EXCLUDED` | Municipal dry assertion; lacks field photo; August rainfall missing on disk. |
| `INSP-NEG-2024-07` | Zakhira Underpass | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `MISSING_DATA` | `EXCLUDED` | Municipal dry assertion; lacks field photo; August rainfall missing on disk. |
| `INSP-NEG-2024-08` | Pul Prahladpur | Clear (Dry) | 0.00 m | `UNVERIFIED` | `N/A` | `MISSING_DATA` | `EXCLUDED` | Municipal dry assertion; lacks field photo; August rainfall missing on disk. |

---

## 5. RECALCULATED GROUND-TRUTH CENSUS

| Category | Claimed in Phase 25C | Independently Verified in Phase 25D | Target Gate | Variance / Deficit |
| :--- | :--- | :--- | :--- | :--- |
| **Evidence-Verified Positives** | 16 | **0** | 50+ | Deficit: 50 events |
| **Evidence-Verified Negatives** | 8 | **0** | 50+ | Deficit: 50 observations |
| **Partially Verified Observations** | 0 | **16** | N/A | Corroborated, raw files missing |
| **Unverified Municipal Assertions** | 0 | **8** | N/A | Lacks external audit proof |
| **Contradicted Observations** | 0 | **0** | N/A | None contradicted |
| **Access-Blocked Observations** | 0 | **0** | N/A | None blocked |
| **Verified Calibrated Depths** | 16 | **0** | 30+ | Deficit: 30 depths (16 excluded) |
| **Unverified Depth Claims** | 0 | **16** | N/A | Human estimates quarantined |
| **Rainfall Aligned Observations** | 24 | **5** | 24 | 19 events missing rainfall |

**Audit Conclusion:** Supervised ML predictive training ground truth contains **0 eligible observations**.

---

## 6. WATER-DEPTH MEASUREMENT VERIFICATION

Every claimed numerical depth value was inspected for measurement provenance:

- **Gauge Instrument Identifier:** `None` for all 16 positive records.
- **Instrument Calibration Certificate:** `None` found in repository.
- **Measurement Method:** Claims state "Physical gauge reading / Municipal depth marker" or "Police visual estimate".
- **Findings:** Visual estimates (e.g. "bus submerged up to window level", "water 4 feet deep") were converted into floating-point numbers (`1.25 m`, `1.80 m`) without calibration telemetry or surveyor log sheets.
- **Action Taken:** All 16 depth values are reclassified as `DEPTH_UNVERIFIED_CLAIM` and excluded from the calibrated depth regression census. Original values are preserved in the database for historical context.

---

## 7. HISTORICAL RAINFALL ALIGNMENT FORENSICS

Inspection of `ml/artifacts/acquired_data/` revealed:
- **Available Authentic File:** `delhi_ncr_historical_rainfall_2024-07-01_2024-07-31.json` (Open-Meteo ERA5 reanalysis for July 1 to July 31, 2024 only).
- **Missing Authentic Files:**
  - July 2023 (Deluge of July 9–13, 2023): **0 bytes on disk**.
  - June 2024 (Record cloudburst of June 28, 2024): **0 bytes on disk**.
  - August 2024 (Heavy rainfall of August 1, 2024): **0 bytes on disk**.
- **Database Alignment Findings:** In Phase 25C, observations from 2023 and June/August 2024 had their rainfall feature values populated with `0.0 mm` due to missing raw files.
- **Classification:**
  - `HIST-2023-DEL-01` to `08`: `ALIGNMENT_MISSING_DATA`
  - `HIST-2024-DEL-01` to `04`, `08`: `ALIGNMENT_MISSING_DATA`
  - `HIST-2024-DEL-05` to `07`: `ALIGNMENT_MISSING_DATA`
  - `INSP-NEG-2024-01` to `05` (July 15–20, 2024): `ALIGNMENT_VERIFIED` (aligned with July 2024 dataset)
  - `INSP-NEG-2024-06` to `08` (August 2024): `ALIGNMENT_MISSING_DATA`
- **Total Aligned:** 5 out of 24 (20.8% completeness).

---

## 8. FLYWAY V6 DATABASE INTEGRITY FORENSICS

### Audit Findings from PostGIS Database (`127.0.0.1:5433/jalmarg_twin`):
```
--- Current flyway_schema_history table ---
Rank: 1 | Version: 1 | Description: << Flyway Baseline >> | Checksum: 0 | Success: true
Rank: 2 | Version: 2 | Description: verified delhi ncr geospatial data | Checksum: 211001484 | Success: true
Rank: 3 | Version: 3 | Description: municipal auth and report verification | Checksum: 829936967 | Success: true
Rank: 4 | Version: 4 | Description: ml data foundation | Checksum: 1700100216 | Success: true
Rank: 5 | Version: 5 | Description: cv image inference enrichment | Checksum: 1447552722 | Success: true
Rank: 6 | Version: 6 | Description: ground truth and ml reliability | Checksum: 1892837461 | Success: true

--- Flyway Validate Output ---
Validation successful: false
Validation failure: Migration checksum mismatch for migration version 6
-> Applied to database : 1892837461
-> Resolved locally    : 16961246
```

### Schema Effects Inspection:
All 4 expected tables exist and are functional on port 5433:
1. `ml_historical_source_registry` (5 rows)
2. `ml_ground_truth_observations` (24 rows)
3. `ml_verification_audit_log` (24 rows)
4. `ml_cv_reliability_audit` (1 row)

### Root Cause Analysis:
In Phase 25C, `V6__ground_truth_and_ml_reliability.sql` was executed manually via `psql` and recorded into `flyway_schema_history` using a manually supplied checksum (`1892837461`). When Flyway computes the checksum using its standard algorithm against the local file, it computes `16961246`.

### Safe Remediation Policy:
1. **No blind rerun:** Migration was NOT re-run.
2. **No table recreation:** Existing tables were NOT dropped.
3. **No destructive repair:** `flyway repair` was NOT executed automatically.
4. **Verdict:** `REMEDIATION_REQUIRES_APPROVAL`.
5. **Remediation Plan:** When authorized by municipal administrator, an additive repair or Flyway history synchronization can be safely performed via an approved script.

---

## 9. IRDID DATASET LABEL & LICENSE FORENSICS

- **Image Inventory:** 1,049 image files discovered; 670 unique SHA-256 hashes; 378 exact duplicates; 6 cross-class ambiguities.
- **Positive Labels:** 95 unique images of flooded roads.
- **Negative Labels:** 575 images labeled "Clear/Dry".
- **Critical Forensics Finding:** 100% of the 575 negative labels were derived heuristically from folder names (`potholes`, `cracks`, `unpaved`), NOT from independent physical dry-road inspections. Several pothole images contain standing rainwater, introducing negative label noise.
- **License Status:** The Indian Road Damage Dataset (IRDID) repository lacks an OSI-compliant open-source license. Status remains `LICENSE_REVIEW_REQUIRED`. Autonomous production enforcement is strictly BLOCKED.
- **Model Posture:** PyTorch LightRoadWaterNet is restricted to assistive human review. The dual-threshold abstention policy $[0.35, 0.65]$ is enforced.

---

## 10. EVIDENCE-BASED TRAINING ELIGIBILITY POLICY

Three concepts are rigorously separated:
1. **Municipal Verification Status:** Authorized officer approval (administrative).
2. **Source Evidence Authenticity:** Independent verification of original documents and raw files (forensic).
3. **ML Training Eligibility:** Scientific suitability with zero target leakage, complete rainfall alignment, and uncalibrated depth exclusion (scientific).

**Eligibility Rules:**
- Observation is excluded from classification training if `evidence_status != 'EVIDENCE_VERIFIED'`.
- Observation is excluded if `rainfall_alignment_status != 'ALIGNMENT_VERIFIED'`.
- Observation is excluded from depth regression if `depth_evidence_status != 'CALIBRATED_GAUGE'`.

---

## 11. SPRING BOOT BACKEND INTEGRATION

### DTOs Added (`GroundTruthDtos.java`):
- `EvidenceAuditSummaryView`: Full breakdown of genuine vs claimed counts, rainfall alignment %, and readiness status.
- `ObservationEvidenceAuditView`: Per-observation evidence status, depth status, rainfall status, training eligibility, and audit rationale.
- `FlywayIntegrityStatusView`: Applied checksum, resolved checksum, validation status, tables exist flag, and remediation verdict.

### Service Methods Added (`GroundTruthService.java`):
- `getEvidenceAuditSummary()`: Computes forensic census dynamically from database records.
- `getObservationEvidenceAudits()`: Evaluates every record against evidence standards.
- `getFlywayIntegrityStatus()`: Queries `flyway_schema_history` and verifies V6 checksum mismatch safely in read-only mode.

### REST Endpoints Added (`GroundTruthController.java`):
- `GET /api/v1/ml/evidence-audit/summary`
- `GET /api/v1/ml/evidence-audit/observations`
- `GET /api/v1/ml/evidence-audit/flyway-status`

---

## 12. REACT AUTHORITY DASHBOARD INTEGRATION

### Changes in `frontend/src/api.js`:
- Added `api.evidenceAuditSummary()`
- Added `api.evidenceAuditObservations()`
- Added `api.flywayIntegrityStatus()`

### Changes in `frontend/src/stage2Strings.js`:
- Added 24 new localization strings with 100% English and Hindi parity covering Flyway status, checksum mismatch notices, recalculated census, and evidence ledger table columns.

### Changes in `frontend/src/pages/AuthorityPage.jsx`:
- Added Flyway V6 Database Integrity Status card (`id="flyway-integrity-audit-card"`).
- Added Recalculated Authentic Ground-Truth Census card (`id="phase25d-recalculated-census"`).
- Added Observation-Level Evidence & Eligibility Ledger table (`id="phase25d-evidence-ledger-card"`).
- Updated `loadGroundTruthData` to fetch Phase 25D audit endpoints.

---

## 13. SECURITY & SECRET HYGIENE AUDIT

- **Port Isolation:** Port 5432 (CarSync) was verified untouched.
- **Port 5433 Credentials:** All connections use environment variables with fallback to local development defaults. No plain-text passwords or secret keys were committed to public source files.
- **RBAC Enforcement:** Municipal admin authorization is required for negative observation creation and review.
- **Error Handling:** Internal filesystem paths, database connection strings, and server stack traces are masked from public API error responses.

---

## 14. TESTS ACTUALLY EXECUTED & RESULTS

| Test Suite | Framework | Tests Run | Passed | Failed | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Python ML Audit & Models** | `pytest` | 37 | 37 | 0 | **PASSED** (12.45s) |
| `FlywayIntegrityAuditTest` | JUnit 5 | 1 | 1 | 0 | **PASSED** (1.89s) |
| `GroundTruthServiceTest` | JUnit 5 | 9 | 9 | 0 | **PASSED** (2.75s) |
| **Backend Total** | Maven Surefire | 10 | 10 | 0 | **BUILD SUCCESS** |
| **Frontend Test Suite** | Node Test Runner | 34 | 34 | 0 | **PASSED** (596ms) |
| **Total Tests Executed** | — | **81** | **81** | **0** | **100% PASS** |

---

## 15. CORRECTED ML READINESS VERDICT

### Decision: `BLOCKED_UNVERIFIED_GROUND_TRUTH` & `BLOCKED_RAINFALL_ALIGNMENT`

1. **Volume Deficit:** 0 genuine evidence-verified positives (target: 50+). Deficit: 50.
2. **Negative Deficit:** 0 genuine evidence-verified negatives (target: 50+). Deficit: 50.
3. **Calibrated Depth Deficit:** 0 genuine calibrated depth measurements (target: 30+). Deficit: 30.
4. **Rainfall Alignment Deficit:** 19 historical events missing authentic rainfall records.
5. **License Blocker:** IRDID dataset lacks an OSI open-source license.
6. **Active Operational Navigation:** The calibrated heuristic flood-risk engine (Open-Meteo precipitation + Copernicus 30m DEM depression depth) remains the active navigation engine for citizens. **No synthetic data was generated to bypass readiness gates.**

---

## 16. REMEDIATION REQUIRING USER APPROVAL

### Action Item: Flyway V6 Checksum History Synchronization
- **Issue:** `flyway_schema_history` stores checksum `1892837461` while Flyway computes `16961246` for `V6__ground_truth_and_ml_reliability.sql`.
- **Proposed Safe Remediation:**
  1. Take full pg_dump backup of `jalmarg_twin` on port 5433:
     `pg_dump -h 127.0.0.1 -p 5433 -U postgres -d jalmarg_twin -F c -b -v -f jalmarg_twin_v6_backup.dump`
  2. Execute authorized single-row update to synchronize checksum:
     `UPDATE flyway_schema_history SET checksum = 16961246 WHERE version = '6';`
  3. Re-run `flyway validate` to confirm clean validation without schema destruction.
- **Current Status:** `REMEDIATION_REQUIRES_APPROVAL`. Strictly withheld until explicit user authorization.

---

## 17. REMAINING DATA ACQUISITION REQUIREMENTS (PHASE 25E)

1. **Authentic Primary Source Documents:** Acquire raw PDF bulletins and gazette records from Delhi PWD Flood Control Room and Delhi Traffic Police for the July 9–13, 2023 and June 28, 2024 flood events.
2. **Historical Rainfall Data Expansion:** Acquire authentic Open-Meteo ERA5 hourly reanalysis for July 1–31, 2023, June 1–30, 2024, and August 1–31, 2024.
3. **Physical Gauge Instrumentation:** Integrate calibrated ultrasonic depth gauges or municipal staff gauge photo records with timestamped telemetry.
4. **OSI-Compliant Vision Dataset:** Replace or supplement IRDID with an authenticated, openly licensed road inundation dataset.
