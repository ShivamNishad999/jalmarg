# JalMarg API

Base URL: `http://localhost:8080` locally, App Runner / EC2 URL in production. All JSON. Errors: `{"error": "message"}`.

## Reports

### `POST /api/reports` (multipart/form-data)
| field | required | notes |
|---|---|---|
| `latitude`, `longitude` | ✅ | decimal degrees |
| `photo` | one of photo / depthLevel | image file (frontend compresses to ≤1280px JPEG) |
| `depthLevel` | one of photo / depthLevel | `PATCHY` 0.15 ft · `ANKLE` 0.35 · `SHIN` 0.8 · `KNEE` 1.5 · `WAIST` 3.0 |
| `locationName`, `area`, `note` | | area = Delhi / Noida / Greater Noida / Ghaziabad / Gurugram (guessed if empty) |

Returns `201` + **Report**:
```json
{
  "id": 42, "latitude": 28.6347, "longitude": 77.2263,
  "locationName": "Minto Bridge underpass", "hotspotId": 1, "hotspotName": "Minto Bridge underpass, Connaught Place", "area": "Delhi",
  "photoUrl": "/api/photos/reports/2026-10-09/uuid.jpg",
  "depthFt": 1.4, "citizenDepthLevel": "KNEE", "severity": "HIGH",
  "depthSource": "AI_AND_CITIZEN",
  "walkSafe": true, "bikeSafe": false, "carSafe": false,
  "aiSummary": "Water reaches the scooter's footboard; two-wheelers should avoid. (judged from: scooter tyre)",
  "aiSummaryHi": "पानी स्कूटर के फुटबोर्ड तक है; दोपहिया वाहन न जाएँ।",
  "aiConfidence": 0.74, "note": null, "confirmations": 0, "status": "ACTIVE",
  "createdAt": "2026-10-09T11:02:00Z", "lastConfirmedAt": "2026-10-09T11:02:00Z", "demo": false
}
```
`severity`: LOW < 0.3 ft ≤ MODERATE < 0.8 ≤ HIGH < 1.5 ≤ SEVERE. `depthSource`: AI · CITIZEN · AI_AND_CITIZEN · DEMO.

### Other report endpoints
- `GET /api/reports/active?lat=&lng=&radiusKm=50` → Report[] (live map)
- `GET /api/reports/recent?days=7` → Report[] (any status)
- `GET /api/reports/{id}`
- `POST /api/reports/{id}/confirm` → "still flooded" (+1, keeps it alive)
- `POST /api/reports/{id}/clear` → "water cleared"
- `GET /api/photos/{key}` → image bytes (from S3 or disk)

## Routes
`POST /api/routes/evaluate`
```json
{ "routes": [ [[28.62,77.36],[28.63,77.37]], [[...]] ], "bufferMeters": 60 }
```
→
```json
{ "recommendedIndex": 1,
  "routes": [ { "index": 0, "hazardCount": 1, "riskScore": 8, "safeForBike": false, "safeForCar": false,
                "hazards": [ { "reportId": 6, "locationName": "NH-9 underpass", "latitude": 28.628, "longitude": 77.369,
                               "depthFt": 1.1, "severity": "HIGH", "distanceFromRouteM": 22 } ] } ] }
```
The frontend gets route geometry from OSRM, sends it here, and re-ranks for the chosen vehicle.

## Alerts
- `GET /api/alerts/feed?lat=&lng=&radiusKm=3&lang=en|hi` → `{ rainNext6hMm, maxHourlyMm, rainLevel, alerts: [{level,title,message,reportId,latitude,longitude}] }`
- `POST /api/alerts/subscriptions` 🔒 `{label,latitude,longitude,radiusKm,lang}` → Subscription for the signed-in account's email (`emailAlertsActive` = SNS subscription created)
- `GET /api/alerts/subscriptions` 🔒 (mine) · `DELETE /api/alerts/subscriptions/{id}` 🔒
- `POST /api/alerts/run` header `X-Jalmarg-Key` → `{sent: n}` (EventBridge Scheduler)
- `GET /api/weather?lat=&lng=` → `{next6hMm, maxHourlyMm, level}`

## Autonomous Flood Risk & Environmental Intelligence
- `GET /api/flood-risk?lat=&lng=&area=&hotspotId=` → `FloodRiskView` `{id, hotspotId, hotspotName, area, latitude, longitude, riskLevel, riskScore, status, rainfallNext6hMm, maxHourlyMm, rainLevel, activeReportCount, maxObservedDepthFt, historicalProneness, confidence, dataQuality, summaryEn, summaryHi, factorBreakdown, assessedAt, expiresAt}`
  - `status`: `OBSERVED_FLOODING` (confirmed by citizen/authority reports) vs `PREDICTED_RISK` (inferred from precipitation forecast + hotspot proneness) vs `NO_IMMEDIATE_RISK` vs `UNKNOWN`
  - `riskLevel`: `LOW` (0-25) · `MODERATE` (26-50) · `HIGH` (51-75) · `CRITICAL` (76-100) · `UNKNOWN`
  - `dataQuality`: `FRESH` · `STALE` · `MISSING_TELEMETRY` · `SYNTHETIC`
  - `factorBreakdown`: JSON object detailing contributing data streams (`OPEN_METEO` ECMWF forecast, `NASA_GES_DISC` GPM IMERG V07B satellite observation, `COPERNICUS_ESA` GLO-30 elevation & depression index, and `CITIZEN_CROWDSOURCE` ground truth).
- `GET /api/flood-risk/hotspots` → `FloodRiskView[]` (active assessments across all recurring hotspots)
- `GET /api/flood-risk/status` → `{isRunning, totalCycles, lastRunAt, lastDurationMs, evaluatedHotspotsCount, criticalRiskCount, highRiskCount, alertsGeneratedCount, activeAssessmentTtl}`
- `POST /api/flood-risk/run` header `X-Jalmarg-Key` → `{evaluated: n, status: "success"}`

## Authority
- `GET /api/hotspots` → Hotspot[] `{id,name,area,latitude,longitude,totalReports,reportsLast30Days,maxDepthFt,activeNow,firstSeen,lastSeen}`
- `GET /api/authority/summary?area=&days=30` 🏛 → KPIs, per-day counts, severity split, top 10 hotspots
- `GET /api/authority/report.pdf?area=Noida` or `?hotspotId=3` 🏛 → complaint PDF
- `PATCH /api/authority/reports/{id}` 🏛 `{status: ACTIVE|CLEARED, note}` → Report (adds `authorityNote`, `statusUpdatedAt`)

## Accounts
Legend: 🔒 = needs `Authorization: Bearer <token>` · 🏛 = role AUTHORITY or ADMIN.
Errors are `{ "error": "...", "code": "..." }`.

| Call | Body | Result |
|---|---|---|
| `POST /api/auth/register` | `{name,email,password}` (password ≥ 8 chars, letters + digits) | `202 {status:"CODE_SENT"}` — 6-digit code emailed. Same answer for an existing email (no account enumeration). |
| `POST /api/auth/verify-email` | `{email,code}` | `{status:"VERIFIED"}` · `OTP_INVALID` / `OTP_EXPIRED` / `OTP_LOCKED` |
| `POST /api/auth/resend-code` | `{email,purpose: VERIFY_EMAIL\|RESET_PASSWORD}` | `202` · `OTP_COOLDOWN` (60 s) / `OTP_LIMIT` (5 per hour) |
| `POST /api/auth/login` | `{email,password}` | `{token,expiresAt,user:{id,name,email,role,emailVerified,createdAt}}` · `INVALID_CREDENTIALS` 401 / `EMAIL_NOT_VERIFIED` 403 / `ACCOUNT_LOCKED` 423 (5 failures → 15 min) / `RATE_LIMITED` 429 |
| `GET /api/auth/me` 🔒 | – | user |
| `POST /api/auth/logout` 🔒 | – | `204`, session revoked on the server |
| `POST /api/auth/forgot-password` | `{email}` | `202` always |
| `POST /api/auth/reset-password` | `{email,code,newPassword}` | `{status:"PASSWORD_RESET"}`; all sessions of that user are revoked |
| `POST /api/admin/users/role` (ADMIN) | `{email, role: CITIZEN\|AUTHORITY\|ADMIN}` | user |

Security: passwords BCrypt(11); OTPs 6 digits from SecureRandom, stored BCrypt-hashed, 5 min expiry, single use, 5 attempts.
Session tokens are 256-bit random values; only their SHA-256 is stored. Writes (`POST /api/reports`, confirm/clear,
subscriptions) need a verified account; reading the map, feed and routes is public.

## Misc
- `GET /api/health` → `{status, database, ai, storage, alerts, email: smtp|dev-console|not-configured, time}` (used by the app's "Save & test")
- `GET /api/meta` → `{aiEnabled, aiModel, s3Storage, snsAlerts, reportTtlHours}`
- `POST /api/admin/demo-refresh` header `X-Jalmarg-Key` → re-creates the live demo reports
- `GET /actuator/health`
