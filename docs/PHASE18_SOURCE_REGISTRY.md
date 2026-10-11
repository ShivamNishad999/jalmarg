# JALMARG PHASE 18 — DATA SOURCE & PROVENANCE REGISTRY
## Data Lineage, Source Classifications, and Scientific Traceability

**Project:** JalMarg — Delhi NCR Urban Waterlogging Intelligence  
**Phase:** 18 — Municipal Flood Digital Twin Foundation  
**Audit Date:** 2026-10-10  

---

### 1. Provenance Philosophy & Classification Standard

JalMarg adheres to strict provenance standards to ensure decision-makers know whether data reflects:
1. Physical ground truth observations,
2. Numerical weather model forecasts,
3. Computational estimates,
4. Crowdsourced reports,
5. Sandbox simulations, or
6. Unavailable data.

```
+-----------------------------------------------------------------------------------------+
|                               PROVENANCE CLASSIFICATIONS                                |
+--------------------------+--------------------------------------------------------------+
| Classification           | Description & Constraints                                    |
+--------------------------+--------------------------------------------------------------+
| VERIFIED_OBSERVATION     | Measured ground observations or officially surveyed data.    |
| PROVIDER_FORECAST        | Numerical atmospheric/weather model forecasts (e.g. Open-M). |
| DERIVED_ESTIMATE         | Algorithmically computed or contextual data (e.g. DEM).      |
| USER_REPORTED            | Crowdsourced citizen observations with uncertainty.         |
| SIMULATED                | Hypothetical scenario sandbox runs (strictly isolated).      |
| UNAVAILABLE              | Missing physical measurements (never coerced to zero).       |
+--------------------------+--------------------------------------------------------------+
```

---

### 2. Registered Data Sources Ledger

| Source Code | Source Name | Provider | Classification | CRS | License | Verification Status | Operational Usage |
|---|---|---|---|---|---|---|---|
| `SRC_OPEN_METEO` | Open-Meteo Weather Forecast (ECMWF IFS / GFS) | Open-Meteo GmbH | `PROVIDER_FORECAST` | EPSG:4326 | CC-BY 4.0 | `VERIFIED` | Real-time hourly precipitation forecasts (6h window) for predictive risk engine. |
| `SRC_COPERNICUS_DEM` | Copernicus DEM GLO-30 Topography | European Space Agency (ESA) | `DERIVED_ESTIMATE` | EPSG:4326 | Open Access | `VERIFIED` | Contextual Digital Surface Model (DSM) terrain elevation at 30m resolution. |
| `SRC_CITIZEN_CROWD` | JalMarg Citizen Waterlogging Reports | JalMarg Platform Users | `USER_REPORTED` | EPSG:4326 | Database Contributor | `USER_REPORTED` | Crowdsourced geolocated photos, confirmation votes, and estimated flood depth. |
| `SRC_NASA_IMERG` | NASA GPM IMERG Early Precipitation Run (V07B) | NASA GSFC | `PROVIDER_FORECAST` | EPSG:4326 | NASA Open Data | `UNVERIFIED_THIRD_PARTY` | Satellite precipitation estimates for macro-scale storm tracking. |
| `SRC_HISTORICAL_HOTSPOTS` | Delhi NCR Chronic Waterlogging Hotspots Registry | Delhi Municipal Corporation / PWD | `DERIVED_ESTIMATE` | EPSG:4326 | Municipal Open Data | `DERIVED_CALCULATED` | Historical recurrence coordinates from monsoon reports (2018-2025). |
| `SRC_DELHI_DRAINAGE_STATUS` | Delhi Municipal Drainage Telemetry Status | Municipal Corporations of Delhi | `UNAVAILABLE` | N/A | Proprietary / Absent | `UNAVAILABLE` | Explicit declaration that live stormwater flow / pump telemetry is unavailable. |

---

### 3. Scientific Invariants & Enforcement

1. **No Forecast-to-Measurement Promotion:**
   - Open-Meteo precipitation values are NEVER labeled as measured rain gauge rainfall.
   - Snapshots clearly differentiate `observedRainfallMm` (NULL when no rain gauge exists) from `forecastRainfallMm`.

2. **No Visual Terrain-to-Depth Promotion:**
   - Copernicus DSM height (e.g. 214m above sea level) is contextual topographic elevation, NEVER water column depth.

3. **No Uncalibrated Drainage Pipe Rendering:**
   - Because primary-source geometric vectors for Delhi's subsurface stormwater drainage network are not officially published, the twin renders NO synthetic pipe conduits on the map.
   - The UI and API explicitly display: *"Stormwater drainage network telemetry is currently UNAVAILABLE. Physical pipe geometries and pump capacities are not fabricated."*

4. **Missing Values Integrity:**
   - Missing physical water depth is stored as `NULL`, never converted to `0.0`.
   - UI renders "Depth Unspecified / Unknown" instead of false 0-foot claims.
