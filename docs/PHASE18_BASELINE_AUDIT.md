# JalMarg Phase 18: Baseline Architecture & Repository Audit

## 1. Executive Summary

This audit establishes the verified baseline of the JalMarg full-stack application before introducing the Municipal Flood Digital Twin foundation.

- **Backend Architecture:** Spring Boot 3.4.3 / Java 25 / Hibernate JPA / H2 File Database (MySQL compatibility mode)
- **Frontend Architecture:** React 18 / Vite 5.4.21 / Leaflet 1.9.4 / PWA Workbox
- **Backend Baseline Test Results:** `mvn test` — **54/54 Passed** (0 Failures, 0 Errors)
- **Frontend Baseline Test Results:** `node --test` — **17/17 Passed** (0 Failures)
- **Frontend Build Results:** `vite build` — **421.93 kB bundle generated** in 1.76s

---

## 2. Component & Architecture Inventory

### 2.1 Existing Core Entities & Models
1. `Report`: Citizen crowd waterlogging report (`latitude`, `longitude`, `depthFt`, `citizenDepthLevel`, `severity`, `depthSource`, `photoKey`, `status`, `confirmations`, `aiSummary`, `aiConfidence`).
2. `Hotspot`: Historical waterlogging location (`name`, `area`, `latitude`, `longitude`, `totalReports`, `maxDepthFt`, `avgDepthFt`).
3. `FloodRiskAssessment`: Multi-factor risk calculation (`status`, `riskLevel`, `riskScore`, `confidence`, `rainfallNext6hMm`, `maxHourlyMm`, `rainLevel`, `historicalProneness`, `factorBreakdown`, `dataQuality`).
4. `Subscription`: Citizen email alert subscription (`email`, `latitude`, `longitude`, `radiusKm`, `area`).

### 2.2 Historical Hotspots Verification Status
- **Storage:** Stored in H2 `hotspots` table, pre-seeded from municipal chronic waterlogging lists (Minto Bridge, Pul Prahladpur, AIIMS underpass, Zakhira underpass, etc.).
- **Evidence Verification:** Historical hotspot counts represent recurring vulnerability records; they are classified as **historical susceptibility priors** and are **not** treated as real-time confirmed ground observations without live corroboration.

### 2.3 Copernicus DEM Usage Audit
- **Service:** `CopernicusDemService.java` querying Copernicus DEM GLO-30 GeoTIFFs / elevation profiles.
- **Role:** Strictly **contextual terrain evidence** (providing elevation AMSL, relative catchment depression depth, and slope). It does **not** modify or calibrate flood depth values mathematically.

### 2.4 Authentication, Admin Security & Authorization
- **Shared Key Protection:** `X-Jalmarg-Key` header (`${ALERT_RUN_KEY}`) protects administrative maintenance endpoints (`/api/alerts/run`, `/api/admin/demo-refresh`, `/api/flood-risk/run`).
- **Audit Finding:** Adequate for internal EventBridge schedulers and demonstration, but municipal authority actions require auditable operator identification and structured audit logs.

### 2.5 Route Geometry Representation & Hazard Logic
- **Router:** OSRM provides multi-alternative route polylines as `List<List<double[]>>` `[lat, lng]`.
- **Evaluator:** `RouteService.java` calculates point-to-segment distance within a spatial buffer (default 60m), identifies both observed reports and forecast hotspots (`hazardType="PREDICTED"`), penalizes high-risk paths, and selects the lowest-risk alternative.

### 2.6 AWS Deployment Assumptions
- **Containerization:** Spring Boot runnable JAR on AWS App Runner / ECS Fargate.
- **Database:** AWS RDS for MySQL 8.0 (configured via `DB_URL`, `DB_USER`, `DB_PASSWORD`).
- **Media Storage:** AWS S3 (`S3_BUCKET`) with graceful local filesystem fallback (`./uploads`).
- **AI Vision:** Amazon Bedrock (`AI_ENABLED=true`, `BEDROCK_MODEL_ID`).
- **SMS/Email Alerts:** AWS SNS (`SNS_TOPIC_ARN`).

---

## 3. Baseline Audit Verdict

The baseline is healthy, stable, and 100% verified. Proceed to Stage 1: Geospatial Foundation & PostGIS-compatible data model design without destructive modifications to the existing H2 local development setup.
