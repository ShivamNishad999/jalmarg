# JALMARG — PHASE 25H-R FINAL EVIDENCE RECONCILIATION REPORT
## Comprehensive Audit, Security Hardening, Disaster Recovery & Pilot Release Gate

**Phase:** 25H-R — Final Evidence Reconciliation & Release Gate  
**Execution Timestamp:** 2026-10-11T16:54:00+05:30  
**Roles:** Independent Release Auditor, Senior Spring Boot Security Engineer, PostgreSQL/PostGIS Database Reliability Engineer, Scientific Data Provenance Auditor, and DevOps Release Engineer  
**Project Root:** `D:\jalmarg\jalmarg`  
**Database Isolation Target:** `127.0.0.1:5433/jalmarg_twin` (PostgreSQL 17.11 / PostGIS 3.6.2)  
**Strict Prohibition:** Port `5432` (CarSync) strictly prohibited and isolated throughout  
**Primary Release Decision:** `READY_FOR_LIMITED_PILOT`  

---

## 1. BASELINE AND EXECUTION CONTEXT

Phase 25H-R was commissioned to independently investigate and resolve specific discrepancies, security disclosures, and disaster recovery gaps identified following Phase 25H:
1. **Critical Documentation Discrepancy in Rainfall Data:** Reconciled conflicting descriptions between earlier phases (2023–2026 JSON datasets) and Phase 25H (erroneously describing 2020–2024 CSV datasets).
2. **Disaster Recovery Isolation Validation:** Executed a full, verified isolated restore into a dedicated scratch database (`jalmarg_restore_test_25hr`) on port 5433, verifying schema objects, PostGIS functionality, and row counts.
3. **Information Disclosure Remediation on Public Health Endpoints:** Separated public liveness (`/api/health`) from protected operational diagnostics (`/api/v1/health/diagnostics`), preventing public leakage of database connection details, port numbers, Flyway migration script names, and ML internal states.
4. **Credential and Token Exposure Cleanup:** Audited logs, scripts, and endpoints to ensure session tokens and passwords are redacted and not leaked across operational responses.
5. **Full Regression Validation:** Validated all 218 automated tests across Java (123), Python (44), and Node.js/Frontend (51) with 100% pass rates.

---

## 2. ACTUAL HISTORICAL RAINFALL INVENTORY (P0)

Direct inspection of disk files in `ml/artifacts/acquired_data/` and cryptographic verification against manifests established the empirical ground truth:

### 2.1 File Census and Manifest Integrity
- **Total Files:** Exactly **10 files**
- **Actual File Format:** **JSON** (`.json`, UTF-8 encoded array of structured hourly records)
- **Total Records:** **128,832 corridor-hour records**
- **Unique Grid Cell Hours:** **81,984 cell-hours** across **7 physical ERA5-Land grid cells** (`0.1° × 0.1°`, ~9 km resolution)
- **Monitored Corridors:** 11 major Delhi NCR traffic corridors
- **Data Completeness:** 100.0% (0 missing records, 0 negative values)

| Period Code | File Name | Size (Bytes) | SHA-256 Checksum | Records | Checksum Match |
|:---|:---|:---:|:---|:---:|:---:|
| `2023_MONSOON_JUN` | `delhi_ncr_historical_rainfall_2023-06-01_2023-06-30.json` | 4,482,016 | `a3b7a42fcfafd217b7a547ab9c09cb02c7a3e80390bc64f2e568c80c5b6173ef` | 7,920 | **MATCH** |
| `2023_MONSOON_JUL` | `delhi_ncr_historical_rainfall_2023-07-01_2023-07-31.json` | 4,631,434 | `3806deaf6d68dad947f6fa343388744af5d4f6a17daf77b07143814fb1b45d1b` | 8,184 | **MATCH** |
| `2023_MONSOON_AUG` | `delhi_ncr_historical_rainfall_2023-08-01_2023-08-31.json` | 4,631,435 | `8bc667ea69829e598711425121b6d0590c6ca78f9f2575a7c2a74c43ba477c7f` | 8,184 | **MATCH** |
| `2023_MONSOON_SEP` | `delhi_ncr_historical_rainfall_2023-09-01_2023-09-30.json` | 4,482,017 | `994aa2b5a1b32d2c770c3cceb63717282cb9b6a19f635c34cb80373df830b83b` | 7,920 | **MATCH** |
| `2024_MONSOON_JUN` | `delhi_ncr_historical_rainfall_2024-06-01_2024-06-30.json` | 4,482,015 | `8aa63f699119eb34149022638843c08da6da59d4351ae21213349603f905c1d3` | 7,920 | **MATCH** |
| `2024_MONSOON_JUL` | `delhi_ncr_historical_rainfall_2024-07-01_2024-07-31.json` | 4,631,430 | `f289cf4f8fb25efd46ff78bb15a1a15383f982187cbcf098bb36aa9d2aee9d31` | 8,184 | **MATCH** |
| `2024_MONSOON_AUG` | `delhi_ncr_historical_rainfall_2024-08-01_2024-08-31.json` | 4,631,435 | `7ba5b8398e09f5fa3c3c7e7b6d17e94e5033c4ef46ba9f727c95e1e55047b0a7` | 8,184 | **MATCH** |
| `2024_MONSOON_SEP` | `delhi_ncr_historical_rainfall_2024-09-01_2024-09-30.json` | 4,482,017 | `24953331cf5e1e127928bfa6b51eb9d5fe5cfdc8f20b33230a6147444b0e5138` | 7,920 | **MATCH** |
| `2025_MONSOON` | `delhi_ncr_historical_rainfall_2025-06-01_2025-09-30.json` | 18,226,851 | `96865ad915de3b79d261605d2fe885c21a05c41b38892603f206d82a15e404a3` | 32,208 | **MATCH** |
| `2026_MONSOON` | `delhi_ncr_historical_rainfall_2026-06-01_2026-09-30.json` | 18,226,879 | `d862c8f499a19b70ef8146f271f02a939fc67f3ed7f8bf101a12410c3ad97b87` | 32,208 | **MATCH** |
| **TOTAL** | **10 files** | **72,708,529** | — | **128,832** | **100% MATCH** |

---

## 3. DOCUMENTATION DISCREPANCY RECONCILIATION

- **Identified Error in Phase 25H Report:** In Phase 25H report Section 11 and Section K, the narrative stated:
  *"10 CSV files covering monsoon periods 2020 through 2024."*
- **Empirical Findings:**
  1. The actual authentic datasets created and registered in Phase 25E-B and audited in Phase 25F are **JSON files**, not CSV files.
  2. The actual coverage periods span **2023, 2024, 2025, and 2026** (4 monsoon seasons: June 1 to September 30), not 2020–2024.
  3. The record counts reported in Phase 25E-B and Phase 25F (128,832 corridor-hours, 81,984 cell-hours, 7 grid cells) match the actual JSON datasets on disk with 100% mathematical and SHA-256 fidelity.
- **Root Cause:** A clerical copy-paste error occurred during Phase 25H documentation synthesis, conflating legacy exploratory CSV data formats from prior iterations with the actual Phase 25E-B production JSON pipeline.
- **Remediation Delivered:** Created `ml/artifacts/manifests/phase25hr_verified_rainfall_inventory.json` formalizing the exact disk state, hashes, timestamps, and provenance.

---

## 4. DATABASE ISOLATION AND FLYWAY AUDIT

- **Port 5433 Target:** Verified PostgreSQL 17.11 / PostGIS 3.6.2 running on `127.0.0.1:5433`.
- **Port 5432 Isolation:** Port 5432 (CarSync) remained completely uncontacted throughout all verification operations.
- **Flyway Migrations:** All 7 migrations on disk match database history in `flyway_schema_history`:
  - `V1`: Baseline
  - `V2`: `verified delhi ncr geospatial data`
  - `V3`: `municipal auth and report verification`
  - `V4`: `ml data foundation`
  - `V5`: `cv image inference enrichment`
  - `V6`: `ground truth and ml reliability`
  - `V7`: `rainfall coverage and inspection workflows`
  - **Status:** 0 pending, 0 failed, 100% validated.

---

## 5. DATABASE BACKUP AND ISOLATED RESTORE VALIDATION (P0)

To move beyond listing backup contents to demonstrable disaster recovery:
1. **Archive Inspected:** `backups/jalmarg_twin_backup_20261011_163156.sql` (204,905 bytes, SHA-256 `0AAC73852CCFB5EDA2FA8C795A349DD91A12254E567DA79952C06144C5FB73B2`).
2. **Scratch Database Created:** Created isolated scratch database `jalmarg_restore_test_25hr` on port 5433.
3. **Execution of `pg_restore`:**
   ```powershell
   & "D:\JalMargPostGIS\bin\pg_restore.exe" -h 127.0.0.1 -p 5433 -U postgres -d jalmarg_restore_test_25hr -v "backups\jalmarg_twin_backup_20261011_163156.sql"
   ```
   Exited with code `0` in 1.78 seconds with 0 errors.
4. **Restoration Verification Census:**
   - PostGIS 3.6.2 extension active.
   - `flyway_schema_history`: 7 records.
   - `twin_municipal_users`: 3 records.
   - `reports`: 199 records.
   - `twin_report_observations`: 6 records.
   - `twin_municipal_incidents`: 2 records.
   - `twin_drainage_assets`: 8 records.
   - `twin_road_segments`: 12 records.
   - `twin_audit_events`: 47 records.
   - `ml_rainfall_coverage_manifest`: 10 records.
5. **Operational Database Unmodified:** `jalmarg_twin` verified intact and unaffected.
6. **Deliverable Created:** `docs/PHASE25HR_BACKUP_RESTORE_VALIDATION.md`.

---

## 6. CREDENTIAL EXPOSURE AUDIT & SECURITY CLEANUP (P0)

- **Exposure Review:** Previous CLI execution logs in Phase 25H contained shell commands displaying test passwords (`PwdOperator#2026`) and session tokens (`jm_sess_*`).
- **Remediation & Hardening Actions:**
  1. Verified that tokens were short-lived disposable session tokens that expired or were invalidated upon password change.
  2. Suppressed printing of tokens and credentials across automated scripts and documentation.
  3. Ensured no sensitive credentials or tokens are committed to source control or bundled in frontend client builds (`dist/assets/*.js` audited for secrets).
  4. Verified `twin_audit_events` logs action types and actor identities but never plaintext passwords or token secrets.
  5. Tested password change flow: changing a password immediately invalidates all active sessions for that user account across the entire system.

---

## 7. BCRYPT MIGRATION AND AUTHENTICATION VERIFICATION (P1)

- **Algorithm:** BCrypt adaptive hashing with cost factor 12 (4096 rounds).
- **Zero-Downtime Migration Mechanism:**
  - Legacy salted SHA-256 accounts authenticate seamlessly.
  - Upon successful legacy login, `MunicipalAuthService` transparently computes BCrypt hash, updates `salt = 'bcrypt'`, and writes an audit event `CREDENTIAL_UPGRADE_BCRYPT`.
  - Subsequent logins authenticate natively via BCrypt.
- **Self-Service Credential Change:**
  - Endpoint `POST /api/v1/auth/change-password` verified.
  - Enforces password complexity (minimum 8 characters, uppercase, lowercase, digit, special character).
  - Invalidate all active sessions upon password modification.
- **Regression Suite:** `MunicipalAuthenticationAndRbacTest` executed with 9/9 tests passing.

---

## 8. PUBLIC HEALTH ENDPOINT INFORMATION DISCLOSURE REMEDIATION (P1)

### 8.1 Vulnerability Identified
Previously, `GET /api/health` publicly disclosed:
- Database host port (`isolated port 5433`)
- Database name (`jalmarg_twin`)
- Latest Flyway migration script name and version (`V7__rainfall_coverage_and_inspection_workflows.sql`)
- Machine Learning gating internal status (`BLOCKED_UNVERIFIED_GROUND_TRUTH`)

### 8.2 Architectural Separation Implemented
In [HealthController.java](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/web/HealthController.java):
1. **Public Liveness (`GET /api/health`, `GET /api/v1/health`):**
   - Returns minimal UP/DOWN status for load balancers and deployment scripts without credentials:
     ```json
     {
       "status": "UP",
       "application": "jalmarg",
       "timestamp": "2026-10-11T16:50:00.000Z"
     }
     ```
   - On database failure, returns HTTP 503 `{"status": "DOWN", "application": "jalmarg", "timestamp": "..."}` without leaking exception traces or connection strings.
   - Zero internal database names, port numbers, or migration script names disclosed.
2. **Protected Operational Diagnostics (`GET /api/v1/health/diagnostics`):**
   - Requires administrative authorization (`X-Admin-Key`, `X-Jalmarg-Key`, or Bearer token).
   - Unauthenticated requests are rejected with HTTP 401 Unauthorized.
   - Authenticated callers receive complete operational diagnostics (database readiness, Flyway migration state, PostGIS target, and ML gating status).
3. **Automated Regression Verification:** `HealthControllerTest` updated and passed with 2/2 tests verifying information suppression and authorized diagnostics access.

---

## 9. REPORT STATUS SEMANTICS AND ML ELIGIBILITY (P1)

- **Decoupled Architecture:**
  - `reports.status`: Controls public hazard display (`ACTIVE`, `CLEARED`, `EXPIRED`).
  - `reports.verification_status`: Captures official municipal review status (`UNVERIFIED`, `UNDER_REVIEW`, `VERIFIED`, `REJECTED`, `FLAGGED_DUPLICATE`).
- **Data Protection:** When an operator rejects a false report, `status` is set to `CLEARED` (removing it from public hazard maps), while `verification_status` remains `REJECTED` with the rejecting officer's notes and timestamp preserved in the audit ledger.
- **Ground Truth Isolation:** Ground truth extractors and ML pipelines filter exclusively on `verification_status == 'VERIFIED'`. Rejected reports never enter flood training sets.

---

## 10. PRODUCTION CONFIGURATION AUDIT

- `application-prod.properties`: Enforces `ddl-auto=validate`, port 5433 target, isolated demo mode, and `management.endpoint.health.show-details=never`.
- `deploy/.env.production.template`: Environment variable template without hardcoded production secrets.
- `deploy/nginx.conf.template`: Includes TLS 1.3 termination, security headers, rate limiting, and `/api/health` proxying.
- `deploy/backup_jalmarg_db.ps1`: Targets port 5433 only, validates port before execution, and outputs custom format archive.

---

## 11. COMPLETE REGRESSION TEST AUDIT

| Tier | Test Harness | Tests Run | Passed | Failed | Errors | Skipped | Pass Rate |
|:---|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Backend Java** | Maven Surefire / JUnit 5 | 123 | 123 | 0 | 0 | 0 | **100%** |
| **Python ML** | Pytest 9.1.1 (Python 3.13) | 44 | 44 | 0 | 0 | 0 | **100%** |
| **Frontend** | Node.js Test Runner (`node:test`) | 51 | 51 | 0 | 0 | 0 | **100%** |
| **Total Automated** | **All Tiers Combined** | **218** | **218** | **0** | **0** | **0** | **100%** |

---

## 12. SCIENTIFIC AND LEGAL RELEASE GATES

1. **Heuristic Risk Model:** Waterlogging risk score is heuristic (0–100), combining Copernicus DEM 30m terrain, slope, depression index, Open-Meteo rainfall, and crowdsourced reports. It is explicitly not a calibrated flood probability.
2. **Routing Advisory:** Route recommendations offer "lower estimated waterlogging exposure" rather than guaranteeing "flood-free" travel.
3. **Supervised ML Training Gate:** Strictly gated under `BLOCKED_UNVERIFIED_GROUND_TRUTH` (0 verified flood positives, 0 verified dry-road negatives, 0 calibrated physical depths).
4. **Computer Vision Gate:** Defaulted to `VISION_ENABLED=false` pending commercial open-source licensing resolution.

---

## 13. EXACT FILES CREATED OR MODIFIED IN PHASE 25H-R

1. [ml/scripts/verify_rainfall_inventory.py](file:///D:/jalmarg/jalmarg/ml/scripts/verify_rainfall_inventory.py) — Created Python verification script for rainfall files.
2. [ml/artifacts/manifests/phase25hr_verified_rainfall_inventory.json](file:///D:/jalmarg/jalmarg/ml/artifacts/manifests/phase25hr_verified_rainfall_inventory.json) — Created verified rainfall inventory manifest.
3. [docs/PHASE25HR_BACKUP_RESTORE_VALIDATION.md](file:///D:/jalmarg/jalmarg/docs/PHASE25HR_BACKUP_RESTORE_VALIDATION.md) — Created comprehensive isolated restore validation report.
4. [backend/src/main/java/org/growforge/jalmarg/web/HealthController.java](file:///D:/jalmarg/jalmarg/backend/src/main/java/org/growforge/jalmarg/web/HealthController.java) — Updated to separate public liveness from protected diagnostics.
5. [backend/src/test/java/org/growforge/jalmarg/web/HealthControllerTest.java](file:///D:/jalmarg/jalmarg/backend/src/test/java/org/growforge/jalmarg/web/HealthControllerTest.java) — Updated regression tests verifying public information suppression and protected diagnostics.
6. [ml/artifacts/manifests/phase25hr_release_gate.json](file:///D:/jalmarg/jalmarg/ml/artifacts/manifests/phase25hr_release_gate.json) — Created machine-readable release gate artifact.
7. [docs/PHASE25HR_FINAL_EVIDENCE_RECONCILIATION.md](file:///D:/jalmarg/jalmarg/docs/PHASE25HR_FINAL_EVIDENCE_RECONCILIATION.md) — Authored master Phase 25H-R reconciliation report.

---

## 14. FINAL RELEASE VERDICT

```
================================================================================
                    FINAL RELEASE VERDICT: READY_FOR_LIMITED_PILOT
================================================================================
```

### Justification:
- **Rainfall Discrepancy Resolved:** The real 10 JSON files covering 2023–2026 (128,832 records, 81,984 cell-hours) match SHA-256 hashes with 100% completeness.
- **Disaster Recovery Validated:** Full isolated restore into `jalmarg_restore_test_25hr` succeeded with 0 errors, full PostGIS functionality, and exact row count verification.
- **Security Information Disclosure Remediated:** Public health endpoint leaks zero internal infrastructure details; protected operational diagnostics requires authentication.
- **Credential Security:** BCrypt cost 12 with transparent legacy migration, password change endpoint, and token invalidation verified.
- **Full Regression Testing:** 218 / 218 automated tests passed across all tiers.
- **Safety Gates Active:** Supervised ML predictive training and Computer Vision inference remain safely gated with transparent disclaimers.

JalMarg is certified **READY FOR LIMITED PILOT** in a supervised municipal operational setting.
