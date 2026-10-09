# JalMarg Local Run & Operations Guide

This guide provides step-by-step instructions for running, configuring, and verifying the complete JalMarg system locally.

---

## 1. Prerequisites

- **Java JDK:** OpenJDK 21 or 25 installed
- **Apache Maven:** 3.9+ installed
- **Node.js & npm:** Node v20+ / npm v10+ installed
- **Operating System:** Windows, Linux, or macOS

---

## 2. Environment Configuration

### Backend Configuration (`backend/src/main/resources/application.properties` or environment variables)
```properties
# Server
server.port=8080

# Production / Demo Isolation Controls
jalmarg.demo-isolated-mode=false
jalmarg.seed.enabled=true
jalmarg.report.ttl-hours=6

# Local CORS Allowed Origins
jalmarg.cors.allowed-origins=http://localhost:5173,http://localhost:4173,http://localhost:3000,http://127.0.0.1:5173

# Optional AI / Cloud Integrations (default false for pure local offline operation)
jalmarg.ai.enabled=false
jalmarg.s3.enabled=false
jalmarg.sns.enabled=false
```

### Frontend Configuration (`frontend/.env` or `.env.local`)
```properties
VITE_API_BASE=http://localhost:8080
```

---

## 3. Starting the Applications Locally

### Option A: Running the Backend (Spring Boot)
Open a terminal in the backend directory:
```powershell
cd D:\jalmarg\jalmarg\backend
mvn clean spring-boot:run
```
- **Backend Port:** `http://localhost:8080`
- **Health / Meta Check:** `http://localhost:8080/api/meta`

### Option B: Running the Frontend (React + Vite)
Open a second terminal in the frontend directory:
```powershell
cd D:\jalmarg\jalmarg\frontend
npm install
npm run dev
```
- **Frontend URL:** `http://localhost:5173`

---

## 4. Running Automated Tests

### Backend Test Suite (JUnit 5 + Spring Boot Test)
```powershell
cd D:\jalmarg\jalmarg\backend
mvn clean test
```
*Expected: 49 tests passed, 0 failures, 0 errors.*

### Frontend Test Suite (Node.js Test Runner)
```powershell
cd D:\jalmarg\jalmarg\frontend
npm test -- --run
# or
node --test tests/stage2.test.mjs
```
*Expected: 17 tests passed, 0 failures.*

### Production Frontend Build Check
```powershell
cd D:\jalmarg\jalmarg\frontend
npm run build
```
*Expected: Built in ~2s, clean bundle in `dist/`.*

---

## 5. End-to-End Verification Checkpoints

1. **Map View (`/`):**
   - Loads active observed waterlogging reports (blue/red waterdrop icons).
   - Toggles predictive flood-risk layer (pulsing radar markers with risk score `0-100` and `LOW/MODERATE/HIGH/CRITICAL`).
   - Displays real-time Open-Meteo telemetry widget with Copernicus DEM and satellite latency disclaimers.
2. **Citizen Reporting (`/report`):**
   - Submits depth level, location, and optional photo.
   - Multipart upload safely stores local photos at `/api/photos/reports/...`.
   - Prevents unmeasured depth fabrication (defaults to `0.0 ft` unmeasured telemetry note).
3. **Safe Navigation (`/route`):**
   - OSRM route calculation.
   - Evaluates route against active reports and predicted hazard zones via `POST /api/routes/evaluate`.
   - Warns about unverified pedestrian flood safety.
4. **Alerts & Subscriptions (`/alerts`):**
   - Displays real-time rainfall alerts and emergency feeds.
   - Manages citizen email subscriptions.
5. **Authority Portal (`/authority`):**
   - Analyzes municipal flood hotspots and waterlogging severity.
   - Generates downloadable OpenPDF summary reports (`/api/authority/report.pdf`).

---

## 6. Production Hardening Checklist
- [x] Set `jalmarg.demo-isolated-mode=true` to isolate demo sessions in production.
- [x] Set `jalmarg.seed.enabled=false` to prevent mock data seeding in live production deployments.
- [x] Ensure no hardcoded API keys or AWS credentials exist in frontend code or git repository.
- [x] Client IP rate-limiting active on report confirmation and clearance endpoints.
