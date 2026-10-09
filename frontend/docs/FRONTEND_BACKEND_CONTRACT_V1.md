# JalMarg frontend/backend contract V1

Stage 1 audit, 2026-10-09. Scope: frontend only. Project root is `../` (contains backend, frontend, docs, infra); no Git metadata was discoverable, so branch and pre-existing Git changes are UNKNOWN. This document lives in frontend/docs because the parent docs directory is outside the writable workspace.

## Status vocabulary and evidence

- EXISTING: present in inspected source, not a runtime guarantee.
- PROPOSED: design requiring agreement; not implemented.
- IMPLEMENTED: code exists or was added.
- VERIFIED: only the specifically stated check was executed.
- UNTESTED: runtime/device behavior has not been exercised.
- BLOCKED: a required contract or capability is absent.

Source of truth: `../backend/src/main/java/org/growforge/jalmarg/web/{ReportController,ApiController,ErrorHandler}.java`, `dto/Dtos.java`, `model/Enums.java`, and the Report, Route, Weather, Alert, Dashboard, AI Depth and PDF services. Existing `../docs/API.md` is broadly aligned but its “All JSON” description excludes binary photos/PDFs. All endpoint runtime behavior below is UNTESTED unless separately reported.

## Architecture and configuration

EXISTING React 18.3.1 / Vite 5.4.21, React Router 6.30.6, Leaflet 1.9.4 / React Leaflet 4.2.1, vite-plugin-pwa 0.20.5. Installed versions inspected with `npm.cmd ls --depth=0`. No Capacitor dependency/configuration or native Android project found in the frontend. `src/main.jsx` mounts StrictMode, BrowserRouter and LangProvider. `src/App.jsx` declares map `/`, report `/report`, route `/route`, alerts `/alerts`, authority `/authority`, about `/about`, with map fallback.

`VITE_API_BASE` is the only frontend environment setting found; empty means same origin. Vite development proxy forwards `/api` to localhost:8080. Production must configure a backend origin or reverse proxy; Vite preview does not inherit that development proxy. No browser API keys are required by the inspected frontend. PWA caches tiles and uses NetworkFirst for active reports; cached incidents can be stale and have no visible offline freshness state.

External calls: browser Nominatim search/reverse, OSRM driving (bike/car) and routed-foot walking, OSM tiles. Spring Boot handles storage, Bedrock AI, weather, SNS and PDF generation. Cloud deployment/capability availability is UNKNOWN; static About-page claims are not evidence of deployment.

## Endpoint inventory

All rows are EXISTING source contracts. JSON success unless explicitly binary. Optional parameters marked `?`; absent request bodies are `none`. Path IDs use Java long. Common errors: 400 `{error:string}` for IllegalArgumentException, 413 for upload too large, 500 generic error; Spring errors retain their status. Other deployment/proxy statuses are UNKNOWN. No authentication should be inferred from frontend presence.

| Method / actual URL | Query / request | Success response and status | Frontend caller / handling gap |
|---|---|---|---|
| GET /api/reports/active | lat?:double, lng?:double, radiusKm:double=50; both coordinates enable filtering; body none | 200 ReportView[] | api.activeReports → MapPage; retries, error banner; malformed arrays currently become empty lists |
| GET /api/reports/recent | days:int=7, clamped 1–120; none | 200 ReportView[] | api.recentReports exported, no current page caller |
| GET /api/reports/{id} | id:long; none | 200 ReportView; missing report **400**, not 404 | No wrapper/UI |
| POST /api/reports | multipart latitude:double, longitude:double required; locationName?, area?, depthLevel?, note?:string; photo?:file; requires photo or depthLevel | 201 ReportView; 400 invalid coordinate/depth/image or neither input; 413 upload limit; 500 processing failure | api.createReport → ReportPage; FormData, error/busy handling; no abort or processing status |
| GET /api/photos/** | nested storage key in path; none | 200 image bytes (type from key), cache public 7 days; 404 empty if missing | photoUrl → ReportCard; broken image hidden, no explicit failure explanation |
| POST /api/reports/{id}/confirm | id:long; none | 200 ReportView; missing 400 | api.confirm → ReportCard; increments count/time and reactivates non-active report |
| POST /api/reports/{id}/clear | id:long; none | 200 ReportView; missing 400 | api.clear → ReportCard; sets CLEARED; UI busy/error message |
| POST /api/routes/evaluate | JSON RouteEvaluateRequest | 200 RouteEvaluateResponse | api.evaluateRoutes → RoutePage; error banner; malformed payload defaults documented below |
| GET /api/weather | lat:double, lng:double required; none | 200 RainForecast | No wrapper; MapPage consumes weather through feed instead |
| GET /api/alerts/feed | lat,lng:double required; radiusKm:double=3 clamped .5–25; lang:string=en | 200 AlertFeed | api.feed → MapPage; errors silently swallowed |
| POST /api/alerts/subscriptions | JSON SubscribeRequest | 201 SubscriptionView; invalid email 400 | api.subscribe → AlertsPage; success UI ignores emailAlertsActive |
| GET /api/alerts/subscriptions | email:string required; none | 200 SubscriptionView[] | api.mySubscriptions → AlertsPage; loading/error/empty states |
| DELETE /api/alerts/subscriptions/{id} | email:string required, id:long; none | 200 `{ok:boolean}` (true even for no matching row) | api.unsubscribe → AlertsPage; removes local row on any success; no per-row busy state |
| GET /api/hotspots | none | 200 HotspotView[] | api.hotspots exported, no current page caller |
| GET /api/authority/summary | area?:string, days:int=30 clamped 1–120; none | 200 DashboardSummary | api.summary → AuthorityPage; old data remains during subsequent load/error |
| GET /api/authority/report.pdf | area?:string, hotspotId?:Long; hotspot takes precedence | 200 application/pdf bytes; attachment filename jalmarg-waterlogging-report.pdf; missing hotspot 400 | api.pdfUrl → AuthorityPage area/hotspot anchors; no inline download busy/error states |
| GET /api/meta | none | 200 Meta | api.meta exported, no current page caller; capabilities not surfaced |

Additional EXISTING backend-only operations: POST `/api/alerts/run`, header `X-Jalmarg-Key`, no body, 200 `{sent:int}`, 401 `{error:string}` for bad/missing key; POST `/api/admin/demo-refresh`, same header, no body, 200 `{added:int}`, 401 bad/missing key. No frontend callers; do not expose keys or call these as a frontend feature. `/actuator/health` is mentioned by existing API docs; its detailed response/configuration is UNKNOWN in this audit.

## Actual JSON schemas and field types

Java primitives map to JSON number/boolean, Instant to ISO timestamp string. Boxed types and strings may be null; DTO declarations alone do not guarantee non-null reference fields. Java long IDs/counts arrive as JSON numbers; maximum deployed values are UNKNOWN.

**ReportView**: id:Long; latitude,longitude,depthFt:double; locationName,hotspotName,area,photoUrl,citizenDepthLevel,severity,depthSource,aiSummary,aiSummaryHi,note,status:String; hotspotId:Long; aiConfidence:Double; walkSafe,bikeSafe,carSafe,demo:boolean; confirmations:int; createdAt,lastConfirmedAt:Instant. photoUrl is `/api/photos/` + nested storage key. area is derived from attached hotspot. Text trimmed to 200 (location), 500 (note), 1000 (AI summaries) by service.

Enums: depthLevel PATCHY=.15 ft, ANKLE=.35, SHIN=.8, KNEE=1.5, WAIST=3; severity LOW/MODERATE/HIGH/SEVERE; depthSource AI/CITIZEN/AI_AND_CITIZEN/DEMO; status ACTIVE/CLEARED/EXPIRED. There is **no VERIFIED lifecycle status**.

**HotspotView**: id:Long; name,area:String; latitude,longitude,maxDepthFt:double; totalReports,reportsLast30Days:int; activeNow:boolean; firstSeen,lastSeen:Instant.

**RouteEvaluateRequest**: routes:List<List<double[]>>, where each point is `[latitude,longitude]`; bufferMeters:Double optional, default 60, clamped 20–300. Null routes produces empty results. Invalid point shapes are not comprehensively validated by service.

**RouteHazard**: reportId:Long; locationName,severity:String; latitude,longitude,depthFt,distanceFromRouteM:double. No createdAt/lastConfirmedAt/model/verification field.

**RouteResult**: index,hazardCount:int; riskScore:double; safeForBike,safeForCar:boolean; hazards:RouteHazard[]. **RouteEvaluateResponse**: recommendedIndex:int (-1 with no routes), routes:RouteResult[].

**SubscribeRequest**: name,email,label,lang:String; latitude,longitude:double; radiusKm:Double. Email required by service validation, default label Home, radius default 3 clamped .5–15, lang normalized hi/en; email lowercased. Other reference nullability follows service behavior.

**SubscriptionView**: id:Long; name,email,label,lang:String; latitude,longitude,radiusKm:double; emailAlertsActive:boolean. This flag records SNS subscription creation, not confirmed email delivery; SNS confirmation remains external. Delete only removes a matching email/id record and still returns success if none matches.

**RainForecast**: next6hMm,maxHourlyMm:double; level:String (NONE/LIGHT/MODERATE/HEAVY/VERY_HEAVY/UNKNOWN). Weather exceptions return zero values plus UNKNOWN; do not interpret UNKNOWN as no rain.

**AlertItem**: level,title,message:String; reportId:Long; latitude,longitude:Double. Rain alerts have null reportId. **AlertFeed**: rainNext6hMm,maxHourlyMm:double; rainLevel:String; alerts:AlertItem[]. These field names differ from RainForecast.

**DayCount**: date:String (ISO local date), count:long. **DashboardSummary**: totalReports,activeNow,hotspots,reportsLast7Days:long; perDay:DayCount[]; bySeverity,byArea:Map<String,Long>; topHotspots:HotspotView[]. totalReports is all-time for area, severity/area distributions use requested window, perDay uses Asia/Kolkata dates, topHotspots limited to 10.

**Meta**: aiEnabled,s3Storage,snsAlerts:boolean; aiModel:String (null when disabled); reportTtlHours:int. This is global configuration, not per-incident analysis provenance.

## Protected routing contract

EXISTING `getRoutes` requests full GeoJSON geometry and `alternatives=3`; converts OSRM `[lng,lat]` to `[lat,lng]`, metres to km, seconds to minutes. Bike/car use driving network, walking routed-foot. These services can return fewer alternatives; no invented candidates are required.

RoutePage evaluates all candidate geometries against backend reports using 60 m buffer. Backend skips LOW severity, weights MODERATE=3, HIGH=8, SEVERE=20, sums risk, and recommends lowest score then input index. Frontend deliberately re-ranks by chosen-mode blocking count, backend risk, then duration; uses thresholds bike .5, car .8, walk 1.5 ft. Backend safety is strict depth < threshold; frontend blocking >= threshold agrees.

When every initial candidate is blocked, four perpendicular via points (both sides at 700/1500 m) around deepest hazard on first route generate detours. Candidates under 2.2 times first-route duration are evaluated and appended. Up to four ranked cards remain selectable; map renders selected and alternative geometry and unique hazard markers. Google Maps handoff includes endpoints and optional detour waypoint but cannot guarantee identical geometry.

Stage 1 leaves algorithm and route page untouched. Known gaps: missing evaluations become empty hazards/zero risk; results are matched by position instead of explicit index; changing mode after search leaves prior scoring displayed; explanation forces minimum extra minute; fastest route may be dropped from top four; hazard timestamp UI cannot render because DTO lacks createdAt. These require a focused routing stage, with contract validation before treating a result as assessed. No runtime routing guarantee is claimed.

## PDF, AI and verification foundation

IMPLEMENTED Stage 1: `apiUrl` normalizes base/path boundaries, `request` accepts JSON/+json and reports wrong MIME or malformed JSON, preserves HTTP status on errors and abort errors, handles 204, and `requestPdf(path,{signal})` returns a validated application/pdf Blob. `activeReports(options)` and `evaluateRoutes(routes,{signal})` accept cancellation without changing existing calls. `photoUrl` handles optional/non-string values, relative paths and absolute HTTP(S), rejecting unsupported schemes. All existing exports and valid response shapes remain. Unexpected non-JSON success now rejects intentionally instead of becoming null. Existing authority PDF URLs/anchors are unchanged.

PROPOSED P0 `GET /api/reports/{id}/evidence.pdf`: no implementation found. Suggested contract: id:long, no body; 200 binary application/pdf with Content-Disposition filename such as jalmarg-report-{id}.pdf; 404 missing report, structured non-200 JSON error otherwise. Actual error statuses, permissions and filename are UNKNOWN until backend implementation. BLOCKED: incident PDF UI. Future button must implement loading/error state, preserve user gesture for mobile save/open, sanitize filename, revoke object URLs and test native download handling. Cross-origin filename reading needs exposed Content-Disposition; current helper intentionally does not implement saving or filename extraction.

AI EXISTING: depthFt, depthSource, citizenDepthLevel, aiSummary/Hi, aiConfidence. Internal AiResult contains waterVisible and reference, but DTO does not expose those structurally. Summary parsing is not a stable reference-object contract. Missing PROPOSED fields: aiStatus enum (NOT_REQUESTED/DISABLED/SUCCEEDED/FAILED, exact names to agree), waterVisible:boolean|null, observations:string[]|null, limitations:string[]|null, per-report modelId:string|null, analyzedAt:timestamp|null, estimate provenance that distinguishes fallback from citizen input. No fabricated defaults. Critical backend blocker: photo-only with AI unavailable currently becomes 0.3 ft and CITIZEN even with no citizen depth; global meta cannot resolve an individual record's provenance. No backend change made.

Verification EXISTING: status, confirmations, lastConfirmedAt; initial lastConfirmedAt is submission time even with zero confirmations. Confirmation count is not unique verified people; service increments with each call. ACTIVE means current lifecycle, not independent verification. PROPOSED verificationStatus/verificationMethod/verifierRole and clearedAt exposure require backend semantics. Current badges should not infer verified status from AI or ACTIVE.

## Future contracts and reusable components

All following are PROPOSED and not called/implemented: authenticated `GET /api/reports/mine` with pagination/ownership; individual evidence details can initially use EXISTING GET report by id but attachments/provenance need agreed fields; complaint create/read/status endpoints require identity, report linkage, recipient, reference, timestamps, lifecycle and authorization; authority status updates require role-checked endpoint, allowed transitions and audit history; enhanced route explanations require evaluation timestamp, report freshness, explicit unknown state and optional walking safety. Exact URL/body/error schemas for these future capabilities are UNKNOWN pending backend agreement. Local remembered report IDs would not establish ownership.

Component decisions: reuse ReportCard, VerificationBadges, ReportStatusTimeline, SeverityPill and existing inline route cards. Defer PdfDownloadButton until incident endpoint/mobile strategy; AiAssessmentCard and ReportEvidencePanel until AI contract; RouteComparisonCard until focused routing work; shared ApiErrorState/LoadingState extraction is optional and not necessary for Stage 1. No speculative UI components added.

## Stage 2–8 roadmap and dependencies

1. Stage 2: submission, truthful AI status/provenance, verification display and incident evidence UI; blocked portions await backend fields/PDF. Fix zero-depth share text and photo URL lifecycle with scoped validation.
2. Stage 3: report detail/history/My Reports; agree identity and ownership.
3. Stage 4: route contract validation, mode consistency and explanations; preserve alternatives/detours and validate with real service results.
4. Stage 5: map/alerts freshness, unavailable weather, cancellation and subscription delivery states.
5. Stage 6: authority evidence/complaint lifecycle, roles and status updates after backend support.
6. Stage 7: Hindi parity, keyboard/accessibility, responsive/dark polish, PWA freshness and native integration.
7. Stage 8: integrated browser/device/API regression and deployment verification.

Only Stage 1 is authorized in this implementation. Subsequent stages require approval.

## Stage 2 amendment — 2026-10-09

The sections above record the Stage 1 baseline. The user subsequently authorized Stage 2; this amendment supersedes earlier statements that route validation and evidence components remain deferred. No backend contracts or Java files changed.

- IMPLEMENTED: ReportEvidencePanel and AiAssessmentCard consume only existing ReportView fields. Numeric finite non-negative depth includes zero; missing/invalid depth is unavailable. Source labels are attributed to the backend. AI status, waterVisible, limitations and per-report model remain unavailable; confidence is not measurement accuracy. Photo-only fallback remains an unresolved backend issue.
- IMPLEMENTED: lifecycle uses ACTIVE/CLEARED/EXPIRED without inferred verification or AI timeline steps. Confirmation count describes events, not unique people. Last confirmation is shown only when count is greater than zero; initial timestamp with count zero is not presented as a confirmation.
- IMPLEMENTED: route evaluation joins rows by validated index and validates finite scores, boolean flags, hazard counts and hazard fields. Missing/duplicate/malformed results have null risk/blocking/safety and are unassessed. Unknown candidates retain geometry, rank after assessed candidates, and are never labeled safe. Mode/endpoint changes invalidate pending searches and displayed results. Existing request body, OSRM alternatives, detour construction and assessed-route ranking remain unchanged.
- IMPLEMENTED: `api.createReport(formData, {signal})` supports cancellation without changing existing callers. UI confirms success only after a successful response with a valid report ID. Aborting a request does not guarantee the server did not save it; uncertain failures advise checking the map before retry.
- IMPLEMENTED: reusable PdfDownloadButton calls existing requestPdf, validates MIME, non-empty Blob and `%PDF-` signature, sanitizes filenames, handles busy/error states and cleans temporary URLs. This is browser download support, not validated native Android support or full PDF structural validation.
- PROPOSED/BLOCKED: GET `/api/reports/{id}/evidence.pdf` is still absent. `VITE_INCIDENT_PDF_VERIFIED` defaults false. A deployment operator may set literal `true` only after verifying that endpoint against the configured backend. This explicit operational assertion is not auto-detection; /api/meta is not used to infer incident PDF support. No repeated probes or disabled-action requests occur. Existing area/hotspot links are unchanged.
- IMPLEMENTED: image decoding/canvas failures now reject instead of uploading an unsupported original file under a JPEG name. Compression remains 1280px/quality .82 JPEG; report selection accepts JPEG/PNG/WebP/GIF up to 10 MiB, with HEIC/other formats rejected explicitly. Latest-task guards prevent stale photo/GPS/search completions, and preview URLs are effect-owned and revoked on replacement/unmount.

Validation details and remaining UI/device limitations are in [STAGE_2_IMPLEMENTATION_REPORT.md](STAGE_2_IMPLEMENTATION_REPORT.md). Stage 3 is not authorized.
