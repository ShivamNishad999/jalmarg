# PHASE 26A: GITHUB ACTIONS CI/CD, REPOSITORY SECURITY & AUTOMATED TESTING REPORT

**Project Root:** `D:\jalmarg\jalmarg`  
**GitHub Repository:** `https://github.com/Vijendra-Pratap/JalMargWeb.git`  
**Target Branch:** `main`  
**Phase Date:** October 11, 2026  
**Final Phase Verdict:** **`CI_PUSHED_AWAITING_GITHUB_RUN`**

---

## 1. EXECUTIVE SUMMARY

In Phase 26A, an institutional-grade, multi-job continuous integration pipeline was created and verified for the JalMarg project. The workflow runs on every push and pull request targeting `main`. It incorporates automated security scanning, PostGIS-backed Spring Boot 3.3 regression testing with surefire report artifacting, Node.js 20 LTS frontend testing & production bundling, and scientific Python ML regression testing with lightweight CPU PyTorch wheels.

All commands were executed and validated locally prior to remote deployment. The workflow adheres to least-privilege permissions, concurrency controls, timeout limits, and strict secret protection.

---

## 2. WORKFLOW SPECIFICATION

**Workflow File:** `.github/workflows/ci.yml`  
**Trigger Events:** `push` on `main`, `pull_request` on `main`  
**Permissions:** `contents: read` (Repository read-only)  
**Concurrency Control:** `group: ${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true`

### 2.1 Job Breakdown & Execution Matrix

| Job ID | Job Name | Runner Environment | Services / Dependencies | Test & Build Command | Artifacts & Outputs |
|---|---|---|---|---|---|
| `security-audit` | **Security & Secret Scan** | `ubuntu-latest` | Node.js 20 | `node scripts/audit_git_publication.mjs` | Fails pipeline if credentials or files > 50MB detected |
| `backend` | **Backend Build & Test** | `ubuntu-latest` | Java 21 (Temurin), Maven Cache, PostGIS 16-3.4 Service Container | `mvn -B clean test`<br/>`mvn -B package -DskipTests` | `backend/target/surefire-reports/` uploaded on failure |
| `frontend` | **Frontend Build & Test** | `ubuntu-latest` | Node 20 LTS, npm Cache | `npm ci`<br/>`npm test`<br/>`npm run build` | Verified PWA production bundle (`dist/`) |
| `python-ml` | **Python ML Subsystem** | `ubuntu-latest` | Python 3.12, pip Cache, PyTorch CPU Wheels | `pip install -e ".[dev]"`<br/>`pytest -v` | 44 scientific regression assertions |

---

## 3. INTEGRATION TEST ISOLATION & TEST DATABASE RECREATION

To guarantee that integration tests never touch developer local environments:
1. **Isolated Service Container:** The backend CI job starts an ephemeral PostgreSQL + PostGIS service container (`postgis/postgis:16-3.4`) on port 5432 with isolated test credentials:
   - `POSTGRES_DB: jalmarg_ci_test`
   - `POSTGRES_USER: jalmarg_ci_user`
   - `POSTGRES_PASSWORD: jalmarg_ci_test_password`
2. **Flyway Migration Application:** On Spring Boot test execution, Flyway applies all 7 migrations (`V1` through `V7`) against the clean service container, testing schema migration integrity in CI.
3. **Developer Machine Protection:** The developer's local database port `5433` and CarSync port `5432` are never reachable or referenced in CI.

---

## 4. LOCAL CI EQUIVALENT VALIDATION MATRIX

Prior to committing the workflow, all test commands were executed locally in the development environment:

| Subsystem | Exact Local Command | Scope | Result | Local Duration |
|---|---|---|---|---|
| **Security Audit** | `node scripts/audit_git_publication.mjs` | Candidate & staged secret scanning | **0 Secrets, 0 Blockers** | 3.2s |
| **Backend Suite** | `mvn test` in `backend/` | 123 JUnit 5 / Spring Boot test cases | **123 / 123 PASSED (100%)** | 43.7s |
| **Frontend Suite** | `npm test` in `frontend/` | 51 unit, contract & journey tests | **51 / 51 PASSED (100%)** | 59.9s |
| **Frontend Build** | `npm run build` in `frontend/` | Vite 5 production bundle & PWA sw | **BUILT IN 3.11s (0 errors)** | 3.1s |
| **Python ML Suite** | `pytest -v` in `ml/` | 44 scientific & geospatial assertions | **44 / 44 PASSED (100%)** | 16.3s |
| **Workflow Syntax** | Python `yaml.safe_load` on `ci.yml` | GitHub Actions YAML schema validation | **SYNTAX VALID** | 0.2s |

---

## 5. REPOSITORY SECURITY & BRANCH PROTECTION RECOMMENDATIONS

To protect the repository integrity on GitHub, the following branch protection rules are recommended for the `main` branch:

1. **Require Status Checks to Pass Before Merging:**
   - Require `Security & Secret Scan`
   - Require `Backend Build & Test (Java 21)`
   - Require `Frontend Build & Test (Node 20 LTS)`
   - Require `Python ML Subsystem (Python 3.12)`
2. **Require Branches to Be Up to Date Before Merging:** Enabled.
3. **Block Force Pushes:** Enabled (`Do not allow bypassing the above settings` / `Block force pushes`).
4. **Block Deletions:** Enabled for `main`.
5. **Require Pull Request Reviews:** Minimum 1 approving review prior to merge.

---

## 6. SCIENTIFIC SAFETY & DATASET GOVERNANCE

- **Vision Safety Invariant:** `VISION_ENABLED=false` is enforced in the CI environment to prevent unintended vision inferences without clearance.
- **Unlicensed Dataset Shield:** The unlicensed IRDID dataset (`ml/datasets/irdid`) remains strictly ignored and uncommitted.
- **Authentic Rainfall Provenance:** The 10 historical gridded rainfall files (`ml/artifacts/acquired_data/`) are tracked under Copernicus CC-BY 4.0 terms, providing reproducible ground truth for `test_phase25eb_rainfall_pipeline.py`.
- **Heuristic Labeling Integrity:** Waterlogging risk indicators remain explicitly designated as multi-factor heuristics rather than physically calibrated flood depth sensors.

---

## 7. GITHUB COMMIT & PUSH STATUS

- **Commit Message:** `ci: add JalMarg automated build and test workflows`
- **Commit Hash:** `b03dcf2d21c0b5cb9b19b46527330b823fe03e45`
- **Target Remote:** `origin` (`https://github.com/Vijendra-Pratap/JalMargWeb.git`)
- **Target Branch:** `main`
- **Actions Run URL:** [https://github.com/Vijendra-Pratap/JalMargWeb/actions](https://github.com/Vijendra-Pratap/JalMargWeb/actions)

---

## 8. REMAINING BLOCKERS & VERDICT

- **Blockers:** Zero technical, security, or syntax blockers.
- **Final Verdict:** **`CI_PUSHED_AWAITING_GITHUB_RUN`**
