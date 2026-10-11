# JALMARG — PHASE 25H-R DATABASE BACKUP & RESTORE VALIDATION REPORT

**Audit Date:** October 11, 2026  
**Auditor Roles:** Senior PostgreSQL/PostGIS Database Reliability Engineer & Independent Release Auditor  
**Host / Port:** `127.0.0.1:5433`  
**Prohibited Port:** `5432` (CarSync) — 100% strictly isolated and untouched  
**Source Database:** `jalmarg_twin`  
**Scratch Verification Database:** `jalmarg_restore_test_25hr`  
**Disaster Recovery Status:** VERIFIED — COMPLETE ISOLATED RESTORE SUCCESSFUL  

---

## 1. BACKUP ARCHIVE IDENTIFICATION & METADATA

The latest verified backup archive generated for JalMarg was located and independently validated:
- **Archive Path:** `D:\jalmarg\jalmarg\backups\jalmarg_twin_backup_20261011_163156.sql`
- **Creation Timestamp:** 2026-10-11 16:31:56 UTC+05:30
- **Archive Format:** PostgreSQL Custom Format (`-F c`, pg_dump v17.11, gzip compressed)
- **Archive File Size:** 204,905 bytes
- **Cryptographic Hash (SHA-256):** `0AAC73852CCFB5EDA2FA8C795A349DD91A12254E567DA79952C06144C5FB73B2`
- **TOC Catalog Entries:** 311 entries verified via `pg_restore.exe --list`

---

## 2. STRICT ISOLATION & SCRATCH DATABASE CREATION

To ensure zero risk to the operational `jalmarg_twin` database and absolute protection for CarSync (port 5432):
1. **Target Verification:** Target host and port confirmed as `127.0.0.1:5433` (PostgreSQL 17.11).
2. **Scratch Database Name:** `jalmarg_restore_test_25hr`.
3. **Execution Command:**
   ```powershell
   & "D:\JalMargPostGIS\bin\psql.exe" -h 127.0.0.1 -p 5433 -U postgres -d postgres -c "CREATE DATABASE jalmarg_restore_test_25hr;"
   ```
   **Output:** `CREATE DATABASE` (Exit code 0).

---

## 3. COMPLETE RESTORE EXECUTION

The custom archive was restored into the isolated scratch database:
```powershell
& "D:\JalMargPostGIS\bin\pg_restore.exe" -h 127.0.0.1 -p 5433 -U postgres -d jalmarg_restore_test_25hr -v "D:\jalmarg\jalmarg\backups\jalmarg_twin_backup_20261011_163156.sql"
```

- **Execution Exit Code:** `0` (Success).
- **Duration:** 1.78 seconds.
- **Errors Encountered:** 0.
- **Warnings Encountered:** 0.
- **Restored Components:**
  - Extensions: `postgis` (v3.6.2), `postgis_topology`
  - Schemas: `public`, `topology`
  - Tables: 31 user-defined and spatial tables
  - Constraints & Indexes: All primary keys, unique constraints, foreign keys, and spatial GiST indexes created without error.

---

## 4. VERIFICATION OF RESTORED SCHEMA OBJECTS & ROW COUNTS

Direct validation queries executed against `jalmarg_restore_test_25hr`:

### 4.1 PostGIS Functionality Check
```sql
SELECT PostGIS_Full_Version();
```
**Result:**
`POSTGIS="3.6.2 3.6.2" [EXTENSION] PGSQL="170" GEOS="3.14.1dev-CAPI-1.20.4" PROJ="8.2.1" LIBXML="2.12.5" LIBJSON="0.12" LIBPROTOBUF="1.2.1" WAGYU="0.5.0 (Internal)" TOPOLOGY`

### 4.2 Representative Row Counts Census
| Table | Restored Scratch Count | Description | Integrity Verdict |
|:---|:---:|:---|:---:|
| `flyway_schema_history` | **7** | Full migration history (V1 through V7) | MATCH |
| `twin_municipal_users` | **3** | Seeded municipal staff accounts | MATCH |
| `reports` | **199** | Citizen waterlogging reports | MATCH |
| `twin_report_observations` | **6** | Individual citizen observation records | MATCH |
| `twin_municipal_incidents` | **2** | Official municipal flood incidents | MATCH |
| `twin_drainage_assets` | **8** | Delhi PWD stormwater drainage assets | MATCH |
| `twin_road_segments` | **12** | Monitored road corridors | MATCH |
| `twin_audit_events` | **47** | Complete historical audit events ledger | MATCH |
| `ml_rainfall_coverage_manifest` | **10** | Monsoon rainfall acquisition manifests | MATCH |

### 4.3 Original Database Protection
The live `jalmarg_twin` database was queried immediately following the restore. All original tables, indexes, and historical records remain intact and completely unmodified.

---

## 5. DISASTER RECOVERY RUNBOOK & RECOVERY METRICS

1. **Recovery Point Objective (RPO):** Maximum 24 hours (or matching automated backup schedule frequency).
2. **Recovery Time Objective (RTO):** Under 5 minutes for full database re-hydration (Empirical restore took 1.78s).
3. **Restoration Command Reference:**
   ```powershell
   # 1. Verify target port is 5433
   # 2. Create target recovery database if needed
   & "D:\JalMargPostGIS\bin\psql.exe" -h 127.0.0.1 -p 5433 -U postgres -d postgres -c "CREATE DATABASE jalmarg_twin_recovered;"
   # 3. Execute pg_restore
   & "D:\JalMargPostGIS\bin\pg_restore.exe" -h 127.0.0.1 -p 5433 -U postgres -d jalmarg_twin_recovered -v "D:\jalmarg\jalmarg\backups\<archive_file>.sql"
   ```
4. **Disaster Recovery Verdict:** **PASSED & CERTIFIED**. Full offline disaster recovery is empirically proven.
