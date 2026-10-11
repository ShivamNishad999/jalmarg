# Frontend-Backend API Integration Matrix

This matrix documents the verification of all Spring Boot backend endpoints against the React frontend client ([src/api.js](file:///D:/jalmarg/jalmarg/frontend/src/api.js)).

## 1. API Verification Matrix

| Endpoint | Method | Backend Controller & Method | Request Format / Parameters | Response Schema / Type | Integration Status |
|---|---|---|---|---|---|
| `/api/meta` | `GET` | `ApiController.meta()` | None | `AppMeta` (`aiEnabled`, `aiModel`, `s3Storage`, `snsAlerts`, `reportTtlHours`) | **PASS** |
| `/api/reports/active` | `GET` | `ReportController.getActiveReports()` | Query: `area` (optional) | `List<ReportResponse>` (`id`, `lat`, `lng`, `depthFt`, `locationName`, `area`, `status`, etc.) | **PASS** |
| `/api/reports/recent` | `GET` | `ReportController.getRecentReports()` | Query: `hours` (default 24), `area` | `List<ReportResponse>` | **PASS** |
| `/api/reports/{id}` | `GET` | `ReportController.getReportById()` | Path: `id` | `ReportResponse` | **PASS** |
| `/api/reports` | `POST` | `ReportController.createReport()` | `multipart/form-data`: `latitude`, `longitude`, `locationName`, `area`, `depthLevel`, `note`, `photo` (file) | `ReportResponse` (with generated ID, fused depth, and photo URL) | **PASS** |
| `/api/reports/{id}/confirm` | `POST` | `ReportController.confirmReport()` | Path: `id`, Client IP header | `ReportResponse` (incremented confirmation count, throttled) | **PASS** |
| `/api/reports/{id}/clear` | `POST` | `ReportController.clearReport()` | Path: `id`, Client IP header | `ReportResponse` (status updated to `CLEARED`, throttled) | **PASS** |
| `/api/photos/**` | `GET` | `ReportController.getPhoto()` | Path: storage key | `byte[]` (`image/jpeg` or `image/png`) | **PASS** |
| `/api/flood-risk` | `GET` | `ApiController.floodRisk()` | Query: `lat`, `lng`, `area` | `FloodRiskResponse` (`status`, `riskLevel`, `riskScore`, `activeReportCount`, `weather`, `topography`) | **PASS** |
| `/api/flood-risk/hotspots` | `GET` | `ApiController.floodRiskHotspots()` | Query: `area` (optional) | `List<FloodRiskResponse>` | **PASS** |
| `/api/flood-risk/status` | `GET` | `ApiController.floodRiskStatus()` | None | `FloodRiskEngineStatus` (`isRunning`, `totalCycles`, `evaluatedHotspotsCount`, `lastCycleDurationMs`) | **PASS** |
| `/api/weather` | `GET` | `ApiController.weather()` | Query: `lat`, `lng` | `WeatherForecast` (`next6hMm`, `maxHourlyMm`, `level`, `forecastHours`) | **PASS** |
| `/api/routes/evaluate` | `POST` | `ApiController.evaluateRoutes()` | JSON: `RouteEvalRequest` (`routes`: `List<List<double[]>>` `[lat, lng]`, `bufferMeters`: `int`) | `RouteEvalResponse` (`routes`: `List<EvaluatedRoute>`, `recommendedIndex`: `int`) | **PASS** |
| `/api/alerts/feed` | `GET` | `ApiController.alertFeed()` | Query: `lat`, `lng`, `radiusKm`, `lang` | `AlertFeedResponse` (`rainLevel`, `rainSummary`, `alerts`: `List<AlertItem>`) | **PASS** |
| `/api/alerts/subscribe` | `POST` | `ApiController.subscribe()` | JSON: `SubscriptionRequest` (`email`, `lat`, `lng`, `radiusKm`, `area`) | `Map<String, String>` (`status`: "subscribed", `id`: "...") | **PASS** |
| `/api/alerts/my` | `GET` | `ApiController.mySubscriptions()` | Query: `email` | `List<SubscriptionResponse>` | **PASS** |
| `/api/alerts/unsubscribe` | `DELETE` | `ApiController.unsubscribe()` | Query: `id`, `email` | `Map<String, String>` (`status`: "unsubscribed") | **PASS** |
| `/api/authority/hotspots` | `GET` | `ApiController.hotspots()` | Query: `days` (default 30), `area` | `List<Hotspot>` (`name`, `area`, `latitude`, `longitude`, `reportCount`, `maxDepthFt`, `avgDepthFt`) | **PASS** |
| `/api/authority/summary` | `GET` | `ApiController.authoritySummary()` | Query: `days` (default 30) | `AuthoritySummary` (`totalReports`, `activeNow`, `cleared`, `autoExpired`, `hotspots`) | **PASS** |
| `/api/authority/report.pdf` | `GET` | `ApiController.authorityReportPdf()` | Query: `days` (default 30) | `byte[]` (`application/pdf` OpenPDF stream) | **PASS** |

---

## 2. DTO & Field Mapping Parity

| Frontend Field / DTO | Java DTO Class | Verification Notes |
|---|---|---|
| `ReportResponse` | `org.growforge.jalmarg.dto.Dtos.ReportResponse` | Matches all JSON properties (`id`, `lat`, `lng`, `depthFt`, `locationName`, `area`, `depthLevel`, `sourceProvenance`, `photoUrl`, `status`, `confirmations`, `createdAt`, `updatedAt`, `aiSummary`). |
| `FloodRiskResponse` | `org.growforge.jalmarg.dto.Dtos.FloodRiskResponse` | Fully exposed to UI with `riskLevel`, `riskScore`, `factors`, `weather`, and `topography`. |
| `RouteEvalRequest` | `org.growforge.jalmarg.dto.Dtos.RouteEvalRequest` | Frontend sends `[lat, lng]` 2D coordinate arrays; backend iterates coordinate geometry for spatial intersection with buffer. |
| `RouteHazard` | `org.growforge.jalmarg.dto.Dtos.RouteHazard` | Supports both observed reports (`reportId != null`) and predicted hotspots (`reportId == null`). Frontend correctly handles both. |
| `AlertFeedResponse` | `org.growforge.jalmarg.dto.Dtos.AlertFeedResponse` | Matches `rainLevel`, `rainSummary`, and `alerts` array. |
| `AuthoritySummary` | `org.growforge.jalmarg.dto.Dtos.AuthoritySummary` | Matches authority counters and stats. |
