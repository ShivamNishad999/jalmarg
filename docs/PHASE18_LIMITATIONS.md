# JALMARG PHASE 18 — KNOWN LIMITATIONS & SCIENTIFIC BOUNDARIES
## Critical Constraints, Model Assumptions, and Blocked Telemetry

**Project:** JalMarg — Delhi NCR Urban Waterlogging Intelligence  
**Phase:** 18 — Municipal Flood Digital Twin Foundation  
**Audit Date:** 2026-10-10  

---

### 1. Critical Scientific Limitations

1. **Digital Twin vs. Hydrodynamic Simulation (SWMM / HEC-RAS):**
   - JalMarg provides a geospatial, rule-based predictive decision-support twin. It does **not** compute 2D hydrodynamic Saint-Venant shallow water equations or dynamic pipe backwater surges.
   - Any attempt to interpret risk scores as calibrated water surface elevations (WSE) or instantaneous cubic meters per second discharge would be scientifically unsound.

2. **Drainage Infrastructure Telemetry Absence (BLOCKED):**
   - Real-time IoT ultrasonic level sensors in Delhi municipal trunk drains (Najafgarh, Barapullah, Shahdara) and pump station SCADA outputs are **not publicly accessible** via standard APIs.
   - Consequence: Drainage capacity is treated strictly as an explicit scenario parameter (`assumedDrainageCapacityPercent`) in what-if analyses and is never presented as measured real-time pipe flow.

3. **Copernicus DEM Spatial Resolution (30-meter cell):**
   - The global GLO-30 DSM provides macro-topographic gradients across Delhi NCR (elevation variation ~200m to 260m above MSL).
   - Micro-topographic drainage impediments (e.g. 15cm road medians, localized curb obstructions, unpaved drainage shoulders) cannot be resolved at 30m resolution.

4. **Weather Forecast Lead Times & Convective Cell Uncertainty:**
   - Open-Meteo forecasts reflect numerical weather prediction (ECMWF IFS / GFS). Highly localized convective monsoon cloudburst cells (&lt;2 km spatial footprint) may exhibit temporal offsets of 1-2 hours compared to ground arrival.

---

### 2. Operational & Security Limitations

1. **Municipal Identity & Access Management (IAM):**
   - Currently, municipal mutation endpoints (`/api/v1/authority/incidents`) use an optional/configured `X-Admin-Key` header.
   - In enterprise production environments, this must be upgraded to OIDC / OAuth2 JWT bearer tokens (e.g., Keycloak, AWS Cognito, or e-Gov Single Sign-On) with fine-grained RBAC for non-repudiation of field dispatches.

2. **Database Profile Duality:**
   - The default development profile uses file-backed H2 with latitude/longitude range indexing.
   - Full spatial polygon topology operations (e.g. `ST_Contains`, `ST_DWithin`, `ST_Intersects`) require PostgreSQL 16 + PostGIS 3.4 using the `postgis` Spring profile.

3. **Citizen Field Report Uncertainty:**
   - Citizen water depth reports are inherently subjective estimates unless verified by multi-citizen consensus or official municipal inspectors.
   - Missing depths are kept as `NULL` and never coerced to 0.
