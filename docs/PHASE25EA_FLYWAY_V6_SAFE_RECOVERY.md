# JALMARG — PHASE 25E-A DATABASE AUDIT & FLYWAY RECOVERY REPORT
## Safe Flyway V6 Recovery, Database Backup Verification & Migration Integrity Restoration

**Audit & Change Execution Date:** 2026-10-11  
**Project Root:** `D:\jalmarg\jalmarg`  
**Target Cluster:** `127.0.0.1:5433` (`jalmarg_twin`, PostgreSQL 17.11 + PostGIS 3.6.2)  
**Strictly Prohibited Database:** `127.0.0.1:5432` (CarSync — strictly isolated; untouched, unmodified, unqueried)  
**Auditor & Change Engineers:** Senior PostgreSQL/PostGIS Database Reliability Engineer, Flyway 10 Migration Specialist, Spring Boot Architect, Production Change-Control Engineer  

---

## 1. USER APPROVAL & CONTROLLED CHANGE SCOPE

The user explicitly authorized a controlled, narrowly scoped single-row Flyway V6 checksum synchronization on `127.0.0.1:5433/jalmarg_twin`, strictly conditioned upon:
1. Target database identity verification (`jalmarg_twin` on port 5433).
2. Independent backup verification (existence, file size, SHA-256, and restorability).
3. Independent Flyway 10 migration resolver checksum verification.
4. Single-row transaction execution modifying exclusively `checksum = 16961246` on version `6`.
5. Zero modifications to CarSync (port 5432), other migrations, or application tables.

---

## 2. DATABASE IDENTITY & POSTGIS VERIFICATION

Before executing the change, the database connection identity was verified using strict runtime SQL assertions:

| Property | Configured / Expected | Actual Inspected Runtime Value | Audit Status |
| :--- | :--- | :--- | :--- |
| **Server Host** | `127.0.0.1` | `127.0.0.1` | `VERIFIED` |
| **Server Port** | `5433` | `5433` (`inet_server_port()`) | `VERIFIED` |
| **Database Name** | `jalmarg_twin` | `jalmarg_twin` (`current_database()`) | `VERIFIED` |
| **PostgreSQL Version** | PostgreSQL 17+ | `PostgreSQL 17.11 on x86_64-windows` | `VERIFIED` |
| **PostGIS Version** | PostGIS 3.6+ | `POSTGIS="3.6.2 3.6.2" [EXTENSION] PGSQL="170"` | `VERIFIED` |
| **CarSync Port 5432 Isolation** | Prohibited | Strictly unqueried and unmodified | `VERIFIED_ISOLATED` |

---

## 3. INDEPENDENT BACKUP VERIFICATION EVIDENCE

Prior to executing the repair, the pre-repair backup was verified independently:

- **Backup File:** [`D:\jalmarg\jalmarg\backups\jalmarg_twin_pre_v6_repair.dump`](file:///d:/jalmarg/jalmarg/backups/jalmarg_twin_pre_v6_repair.dump)
- **Format:** PostgreSQL Custom-Format (`pg_dump -F c -b -v`)
- **File Size:** `181,303 bytes` (Exact match)
- **SHA-256 Checksum:** `60bc902336a53da15f98c3a3ea7a2c01d6ec68f5d73c2204078bf27dd117b790` (Exact match)
- **Archive Inspection:** `pg_restore.exe --list` exited with code `0`. Found `286` total entries and `30` V6 table/sequence/index objects.
- **Scratch Restore Verification:** Freshly restored into isolated scratch database `jalmarg_twin_restore_scratch` on port 5433 with 100% row match (`ml_historical_source_registry`: 7, `ml_ground_truth_observations`: 24, `ml_verification_audit_log`: 0, `ml_cv_reliability_audit`: 1, `flyway_schema_history`: 6), followed by clean scratch drop. Production database `jalmarg_twin` was never overwritten.

---

## 4. FLYWAY-RESOLVED CHECKSUM VERIFICATION

The project's actual runtime Flyway library (Flyway 10.17.3 via `flyway-core` and `flyway-database-postgresql` managed by Spring Boot 3.3.4 parent POM) was utilized to resolve the migration file `backend/src/main/resources/db/migration/postgis/V6__ground_truth_and_ml_reliability.sql`:

- **Reported Stored Checksum in Database:** `1892837461`
- **Genuine Flyway-Resolved Checksum:** `16961246`
- **Migration Location:** `classpath:db/migration/postgis`
- **Duplicate Migrations:** `0` (Exactly one V6 file present on disk)
- **Pending Migrations:** `0` (V1–V6 fully deployed)

---

## 5. PRE-REPAIR V6 HISTORY & BASELINE

### `flyway_schema_history` Record Before Repair:
```
installed_rank: 6
version:        6
description:    ground truth and ml reliability
type:           SQL
script:         V6__ground_truth_and_ml_reliability.sql
checksum:       1892837461
installed_on:   2026-10-11 13:29:50.576607
execution_time: 45
success:        true
```

### Table Row Counts Before Repair:
- `ml_historical_source_registry`: 7
- `ml_ground_truth_observations`: 24
- `ml_verification_audit_log`: 0
- `ml_cv_reliability_audit`: 1
- `reports`: 199
- `twin_municipal_incidents`: 2
- `twin_municipal_users`: 3

---

## 6. EXACT SQL EXECUTED & ROWS AFFECTED

The controlled, single-row update was executed using the authorized guarded transaction pattern with `ON_ERROR_STOP=1`:

```sql
BEGIN;

DO $$
DECLARE
    matched_rows integer;
    changed_rows integer;
BEGIN
    IF current_database() <> 'jalmarg_twin'
       OR inet_server_port() <> 5433 THEN
        RAISE EXCEPTION 'JalMarg database identity check failed';
    END IF;

    SELECT COUNT(*)
      INTO matched_rows
      FROM flyway_schema_history
     WHERE version = '6'
       AND script = 'V6__ground_truth_and_ml_reliability.sql'
       AND checksum = 1892837461
       AND success = true;

    IF matched_rows <> 1 THEN
        RAISE EXCEPTION
          'Expected exactly one eligible V6 row; found %',
          matched_rows;
    END IF;

    UPDATE flyway_schema_history
       SET checksum = 16961246
     WHERE version = '6'
       AND script = 'V6__ground_truth_and_ml_reliability.sql'
       AND checksum = 1892837461
       AND success = true;

    GET DIAGNOSTICS changed_rows = ROW_COUNT;

    IF changed_rows <> 1 THEN
        RAISE EXCEPTION
          'Expected exactly one updated V6 row; updated %',
          changed_rows;
    END IF;
END
$$;

COMMIT;
```

- **Execution Exit Code:** `0` (`SUCCESS`)
- **Number of Rows Affected:** Exactly **`1`**

---

## 7. POST-REPAIR V6 HISTORY & FLYWAY VALIDATION

### `flyway_schema_history` Record After Repair:
```
installed_rank: 6
version:        6
description:    ground truth and ml reliability
type:           SQL
script:         V6__ground_truth_and_ml_reliability.sql
checksum:       16961246
installed_on:   2026-10-11 13:29:50.576607
execution_time: 45
success:        true
```

### Full Migration History Verification:
| Rank | Version | Description | Script | Checksum | Status |
| :---: | :---: | :--- | :--- | :---: | :---: |
| 1 | 1 | `<< Flyway Baseline >>` | `<< Flyway Baseline >>` | `NULL` | Unchanged |
| 2 | 2 | `verified delhi ncr geospatial data` | `V2__verified_delhi_ncr_geospatial_data.sql` | `211001484` | Unchanged |
| 3 | 3 | `municipal auth and report verification` | `V3__municipal_auth_and_report_verification.sql` | `829936967` | Unchanged |
| 4 | 4 | `ml data foundation` | `V4__ml_data_foundation.sql` | `1700100216` | Unchanged |
| 5 | 5 | `cv image inference enrichment` | `V5__cv_image_inference_enrichment.sql` | `1447552722` | Unchanged |
| 6 | 6 | `ground truth and ml reliability` | `V6__ground_truth_and_ml_reliability.sql` | **`16961246`** | **Synchronized** |

### Flyway Validation Output:
```
INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 7 migrations (execution time 00:00.012s)
Validation successful: true
Flyway validation PASSED cleanly without discrepancies.
```

---

## 8. SCHEMA & ROW-COUNT PRESERVATION COMPARISON

| Table Name | Pre-Repair Count | Post-Repair Count | Variance | Schema Integrity |
| :--- | :---: | :---: | :---: | :--- |
| `ml_historical_source_registry` | 7 | 7 | 0 | 100% Intact |
| `ml_ground_truth_observations` | 24 | 24 | 0 | 100% Intact |
| `ml_verification_audit_log` | 0 | 0 | 0 | 100% Intact |
| `ml_cv_reliability_audit` | 1 | 1 | 0 | 100% Intact |
| `reports` | 199 | 199 | 0 | 100% Intact |
| `twin_municipal_incidents` | 2 | 2 | 0 | 100% Intact |
| `twin_municipal_users` | 3 | 3 | 0 | 100% Intact |

**Data Preservation Verdict:** **`PASS (100% PRESERVED)`**. Zero user, incident, report, or ground-truth records were altered or lost.

---

## 9. FULL BACKEND REGRESSION TEST RESULTS

| Test Suite | Framework | Total Tests | Passed | Failed | Status |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `FlywayIntegrityAuditTest` | JUnit 5 | 1 | 1 | 0 | **PASSED** |
| `GroundTruthServiceTest` | JUnit 5 | 9 | 9 | 0 | **PASSED** |
| `AutonomousFloodMonitorTest` | JUnit 5 | 8 | 8 | 0 | **PASSED** |
| `CopernicusDemIndependentValidatorTest` | JUnit 5 | 4 | 4 | 0 | **PASSED** |
| `DigitalTwinEngineTest` | JUnit 5 | 5 | 5 | 0 | **PASSED** |
| `MunicipalAuthenticationAndRbacTest` | JUnit 5 | 6 | 6 | 0 | **PASSED** |
| `MunicipalReportVerificationAndTraceabilityTest` | JUnit 5 | 8 | 8 | 0 | **PASSED** |
| `GeospatialDataFoundationTest` | JUnit 5 | 6 | 6 | 0 | **PASSED** |
| *Full Backend Test Suite (All 20 classes)* | Maven Surefire | **111** | **111** | **0** | **BUILD SUCCESS** |
| *Python ML Audit & Models* | pytest | **37** | **37** | **0** | **100% PASS** |
| *Frontend Test Suite* | Node Test Runner | **34** | **34** | **0** | **100% PASS** |
| **Combined Project Tests** | — | **182** | **182** | **0** | **100% PASS** |

---

## 10. CARSYNC ISOLATION CONFIRMATION

- CarSync on port `5432` was **never accessed, queried, modified, or migrated**.
- All JDBC operations, `psql` transactions, and test suites strictly target port `5433` and database `jalmarg_twin`.
- Hard assertions in `FlywayIntegrityAuditTest.java` guarantee permanent port isolation (`assertNotEquals(5432, port)`).

---

## 11. PERMANENT SAFETY CHECKS IMPLEMENTED

1. **Strict Guard Rails:** Migration scripts must never be manually recorded with external checksum hashes; future migrations must be applied through Flyway's official lifecycle (`spring.flyway.enabled=true`).
2. **Automated Flyway Validation:** `FlywayIntegrityAuditTest` now includes hard assertion `assertTrue(validateResult.validationSuccessful)` preventing undetected checksum regressions in CI/CD.
3. **Authority Dashboard Visibility:** The React Authority Dashboard (`AuthorityPage.jsx`) dynamically queries `/api/v1/ml/evidence-audit/flyway-status` and displays the verified green banner for Flyway migration integrity.

---

## 12. FINAL CHANGE VERDICT

**`REPAIRED_AND_VALIDATED`**

The single-row checksum update to `16961246` succeeded, Flyway 10 validation passed cleanly (`validationSuccessful: true`), all 111 backend tests passed, and 100% of municipal and ground-truth data was preserved.
