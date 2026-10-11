# JALMARG PHASE 18 — API CONTRACT SPECIFICATION
## Versioned Digital Twin & Municipal Authority Endpoints

**Base Path:** `/api/v1`  
**Security:** Optional `X-Admin-Key` header for mutation endpoints (Enforced server-side)  
**Encoding:** `application/json; charset=utf-8`  

---

### 1. Digital Twin Endpoints (`/api/v1/twin`)

#### 1.1 `GET /api/v1/twin/status`
Returns high-level system telemetry, twin layer counts, and drainage notice.
- **Response (200 OK):**
```json
{
  "systemStatus": "OPERATIONAL",
  "totalZonesCount": 5,
  "totalRoadSegmentsCount": 6,
  "totalDrainageAssetsCount": 4,
  "totalRegisteredSourcesCount": 6,
  "activeOperationalIncidentsCount": 2,
  "activeTwinSnapshotsCount": 1,
  "lastSnapshotTime": "2026-10-09T20:50:00Z",
  "drainageTelemetryNotice": "Stormwater drainage network telemetry is currently UNAVAILABLE. Physical pipe geometries and pump capacities are not fabricated."
}
```

#### 1.2 `GET /api/v1/twin/zones`
Lists all monitored municipal boundaries with boundary type and baseline vulnerability.
- **Response (200 OK):**
```json
[
  {
    "id": 1,
    "zoneCode": "ZONE_NDMC_CENTRAL",
    "name": "New Delhi Municipal Council - Central",
    "municipalityName": "NDMC",
    "boundaryType": "PLANNING_BOUNDING_BOX",
    "boundaryGeoJson": "{\"type\":\"Polygon\",\"coordinates\":[...]}",
    "centroidLat": 28.6250,
    "centroidLng": 77.2150,
    "areaSqKm": 42.5,
    "populationEstimate": 300000.0,
    "drainageNetworkStatus": "DRAINAGE_DATA_UNAVAILABLE"
  }
]
```

#### 1.3 `GET /api/v1/twin/roads`
Lists monitored road segments, underpasses, and elevation profiles.
- **Query Params:** `?zoneId=1` (Optional)
- **Response (200 OK):**
```json
[
  {
    "id": 1,
    "segmentCode": "RD-MINTO-01",
    "roadName": "Minto Road Underpass Sump",
    "roadType": "ARTERIAL",
    "startLat": 28.6360,
    "startLng": 77.2280,
    "endLat": 28.6340,
    "endLng": 77.2260,
    "geometryGeoJson": "{\"type\":\"LineString\",\"coordinates\":[[77.2280,28.6360],[77.2260,28.6340]]}",
    "isUnderpass": true,
    "lengthMeters": 450.0,
    "elevationMinMeters": 208.5,
    "elevationMaxMeters": 214.0,
    "zoneId": 1
  }
]
```

#### 1.4 `GET /api/v1/twin/sources`
Returns the complete data provenance ledger and classification status.
- **Response (200 OK):**
```json
[
  {
    "id": 1,
    "sourceCode": "SRC_OPEN_METEO",
    "sourceName": "Open-Meteo Weather Forecast (ECMWF IFS)",
    "provider": "Open-Meteo GmbH",
    "originalUrl": "https://open-meteo.com",
    "geographicCoverage": "Delhi NCR / Global",
    "crs": "EPSG:4326 (WGS84)",
    "license": "CC-BY 4.0",
    "permittedUsage": "Commercial and Non-Commercial with attribution",
    "sourceClassification": "PROVIDER_FORECAST",
    "verificationStatus": "VERIFIED"
  }
]
```

#### 1.5 `GET /api/v1/twin/snapshots`
Retrieves published immutable digital twin snapshots.
- **Query Params:** `?type=CURRENT` (Optional: `CURRENT`, `HISTORICAL`, `SIMULATED`)
- **Response (200 OK):**
```json
[
  {
    "id": 1,
    "snapshotCode": "SNAP-20261010-0001",
    "snapshotType": "CURRENT",
    "createdAt": "2026-10-09T20:50:00Z",
    "zoneId": null,
    "zoneName": "Delhi NCR Regional",
    "aggregatedRiskScore": 38,
    "riskLevel": "MODERATE",
    "observedRainfallTotalMm": null,
    "forecastRainfallTotalMm": 18.5,
    "dataFreshnessMinutes": 12,
    "modelVersion": "JALMARG_TWIN_V1.0",
    "immutableHash": "d2f348e89fbc8b603f90119e8c460a80e4b85e0544f808795ff0d1a49f7e53f7"
  }
]
```

#### 1.6 `POST /api/v1/twin/scenarios`
Executes an isolated deterministic what-if emergency simulation.
- **Request Body:**
```json
{
  "scenarioName": "Monsoon Cloudburst Simulation",
  "rainfallTotalMm": 85.0,
  "rainfallIntensityMmPerHour": 40.0,
  "durationHours": 3,
  "targetZoneId": 1,
  "assumedDrainageCapacityPercent": 50.0,
  "closedRoadSegmentCodes": ["RD-MINTO-01"],
  "availablePumpCount": 4
}
```
- **Response (200 OK):**
```json
{
  "id": 2,
  "scenarioName": "Monsoon Cloudburst Simulation",
  "runType": "CUSTOM_RUN",
  "createdBy": "SCENARIO_ENGINE",
  "createdAt": "2026-10-09T20:55:00Z",
  "rainfallTotalMm": 85.0,
  "rainfallIntensityMmPerHour": 40.0,
  "durationHours": 3,
  "targetZoneId": 1,
  "targetZoneName": "New Delhi Municipal Council - Central",
  "assumedDrainageCapacityPercent": 50.0,
  "simulationLabel": "SIMULATION",
  "affectedRoadsCount": 4,
  "baselineRiskScore": 10,
  "simulatedRiskScore": 67,
  "deltaRiskScore": 57,
  "modelVersion": "JALMARG_WHATIF_V1.0_HEURISTIC",
  "status": "COMPLETED"
}
```

---

### 2. Municipal Authority Endpoints (`/api/v1/authority`)

#### 2.1 `GET /api/v1/authority/incidents`
Lists operational incidents with optional status and severity filtering.
- **Headers:** `X-Admin-Key: <key>` (Optional in local dev)
- **Query Params:** `?status=NEW&minSeverity=HIGH`

#### 2.2 `POST /api/v1/authority/incidents`
Creates a verified municipal operational incident.
- **Request Body:**
```json
{
  "title": "Minto Bridge Underpass Sump Submerged",
  "description": "Rising water level blocking vehicular movement",
  "incidentType": "UNDERPASS_INUNDATION",
  "severity": "HIGH",
  "latitude": 28.6347,
  "longitude": 77.2263,
  "locationName": "Minto Bridge Underpass",
  "zoneId": 1,
  "reportedDepthMeters": 0.85,
  "isRoadBlocked": true,
  "reporterType": "AUTHORITY_OFFICIAL",
  "sourceVerification": "VERIFIED_OBSERVATION"
}
```

#### 2.3 `POST /api/v1/authority/incidents/{id}/assign`
Dispatches response units and assigns dewatering resources.
- **Request Body:**
```json
{
  "assignedUnit": "NDRF Rescue Team 03",
  "assignedPumpAssetId": null,
  "assignedVehicleAssetId": null,
  "actorId": "dispatcher-officer-01",
  "actorRole": "OPERATIONS_DISPATCH"
}
```

#### 2.4 `PATCH /api/v1/authority/incidents/{id}`
Transitions incident lifecycle status (`TRIAGED` ➔ `ASSIGNED` ➔ `IN_PROGRESS` ➔ `RESOLVED`).
- **Request Body:**
```json
{
  "targetStatus": "IN_PROGRESS",
  "reasonOrNotes": "Dewatering pumps active on site.",
  "actorId": "field-supervisor-02",
  "actorRole": "FIELD_ENGINEER"
}
```

#### 2.5 `GET /api/v1/authority/audit-logs`
Returns tamper-evident audit history records for municipal oversight.
