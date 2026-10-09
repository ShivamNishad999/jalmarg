# JALMARG FRONTEND STAGE 1 IMPLEMENTATION REPORT

2026-10-09 · Stage 1 only

## 1. Executive summary

Inspected the existing frontend, dependencies, configuration, six pages, shared components, translations/style structure, existing API documentation, Spring controllers, DTOs and relevant service behavior. Project root: `C:/Users/hp/Downloads/jalmarg/jalmarg`; frontend: its `frontend` directory. This is an extracted working tree with no discoverable Git repository: branch/status/history are UNKNOWN. No destructive Git commands were used.

Implemented limited API utility hardening and produced the [versioned contract](FRONTEND_BACKEND_CONTRACT_V1.md). Build and isolated API utility checks passed. Existing route alternatives, detours, pages, UI styling, translations and PDF anchors were left intact. No backend source was edited, dependencies installed, dummy application data added, or later-stage UI implemented.

## 2. Frontend feature matrix

EXISTING means source implementation only. All browser/device/live service outcomes below are UNTESTED; production compilation is VERIFIED. Risk is regression risk for future work, not a claim of an observed runtime failure.

| Feature | Existing implementation / files | API dependency | Missing or problematic behavior | Risk / verification |
|---|---|---|---|---|
| Citizen incident reporting | ReportPage.jsx; FormData, optional photo/depth, result card | POST reports | No persisted My Reports/detail page | High; source inspected |
| Photo upload | ReportPage.jsx, api.js compressImage; 1280px JPEG | POST multipart, photos | Preview URLs not consistently revoked on reset/remove/unmount; processing races | Medium; device UNTESTED |
| Camera and GPS | Camera file capture, gallery, geolocation, draggable/map-picked position | Browser APIs; Nominatim reverse | Native permission/denial and secure-context behavior untested | High; source inspected |
| Markers and popups | MapPage.jsx, shared.jsx ReportCard | activeReports, confirm, clear, photos | Error can coexist with empty-state message; stale cache not labeled | High; source inspected |
| AI assessment | ReportCard, badges, timeline, ReportPage result | ReportView AI fields | No explicit status/waterVisible/limitations/model provenance | High; backend BLOCKED |
| Alternative routes | RoutePage.jsx, api.js getRoutes | OSRM alternatives | External service availability and candidate count unknown | High; algorithm unchanged |
| Hazard-aware evaluation | RoutePage scoreAll | POST routes/evaluate | Missing assessment silently becomes zero risk | High; runtime UNTESTED |
| Route comparison | Up to four ranked cards; map selection | OSRM duration/distance, backend risk | Mode changes can leave old scoring; forced minimum extra minute | High; source inspected |
| Weather | MapPage rain banner and nearby feed | alerts/feed; weather endpoint unused directly | UNKNOWN forecast not explained; feed errors swallowed | Medium; source inspected |
| Alerts/subscriptions | AlertsPage create/lookup/remove | subscriptions CRUD | Success text ignores emailAlertsActive; no delete busy state | High; live SNS UNTESTED |
| Hotspot visualization | MapPage report clusters; AuthorityPage hotspot map | activeReports; summary.topHotspots | Report circles are illustrative, not measured flood extent; cluster severity misses LOW→MODERATE promotion | Medium; visual UNTESTED |
| Authority dashboard | AuthorityPage counters/charts/table/area filter | authority/summary | Old area results can remain during errors; request race | Medium; source inspected |
| Area PDF | AuthorityPage target=_blank anchor | authority/report.pdf?area | No inline loading/error feedback | High; binary runtime UNTESTED |
| Hotspot PDF | Table anchor | authority/report.pdf?hotspotId | Same mobile/download limitations | High; existing links unchanged |
| Hindi/English | i18n.jsx, LangProvider, About bilingual copy | Feed lang parameter | Several hardcoded English strings remain; unsupported stored language can break About | Medium; strings unchanged |
| Responsive layouts | styles.css breakpoints, bottom nav, sheets, safe area | None | Actual viewport/touch coverage absent | Medium; visual UNTESTED |
| Dark mode | prefers-color-scheme CSS tokens/overrides | None | Inline light colors can conflict; contrast unmeasured | Medium; visual UNTESTED |
| Loading/error/empty states | Page spinners/banners/retries | All calls | Inconsistent swallowing, stale data, absent live regions | Medium; source inspected |
| Capacitor compatibility | Browser/PWA only | Native bridge absent | No installed Capacitor/config/native project; camera/GPS/PDF native flow unknown | High; BLOCKED native verification |
| Accessibility | Some aria labels/roles, semantic buttons | None | Labels lack htmlFor; search lacks keyboard option navigation/active descendant; no reduced-motion rule found | Medium; keyboard/screen reader UNTESTED |

Paths in this matrix are under `src/pages`, `src/components`, or `src` as appropriate. AboutPage is informational and does not verify claimed AWS deployment.

## 3. API contract matrix

The complete method/endpoint/query/body/status/caller matrix and every DTO field/type are in [FRONTEND_BACKEND_CONTRACT_V1.md](FRONTEND_BACKEND_CONTRACT_V1.md#endpoint-inventory), including all 17 requested endpoints, binary formats, common errors, and backend-only operations.

Key reconciliation: report lookup failures currently return 400; report photo keys can contain nested paths; summary counts mix all-time and windowed metrics; weather and feed use different rainfall field names; subscription success does not mean delivery; authority PDF exists but individual evidence PDF does not. ReportView has lifecycle/count/timestamp but no VERIFIED status. RouteHazard lacks the timestamp the UI conditionally tries to show.

## 4. Existing problems

Prioritized follow-up findings; these were not silently rewritten in Stage 1:

1. **P0, routing data integrity:** absent assessment defaults to no hazards and risk 0. Add focused shape/index validation before presenting assessed routes; preserve routing candidates and detours.
2. **P0, backend AI provenance:** photo-only with AI unavailable is stored as 0.3 ft/CITIZEN despite no selected citizen depth. Existing DTO cannot distinguish fallback from observation. Status/water visibility/provenance require backend changes.
3. **P0, report sharing:** `result.depthFt ? ... : '1.0'` invents a 1.0 ft value when depth is zero/missing. Fix in Stage 2 submission/evidence scope; do not spread this fallback into new components.
4. **P1, effect churn:** LangProvider creates a new `t` each render; page callbacks/effects depending on it can repeat load/GPS work when provider renders, including language changes. Stabilize deliberately with language-aware dependencies during the UI stage.
5. **P1, asynchronous state:** place search lacks request cancellation/stale-result protection; authority requests can resolve out of order; photo preview cleanup incomplete; changing route mode does not recalculate existing results.
6. **P1, verification:** timeline marks live stage done regardless of lifecycle and infers AI completion from summary/source. Counts are not unique people. Initial lastConfirmedAt is submission time. Do not label ACTIVE as verified.
7. **P1, availability:** map feed errors swallowed, weather UNKNOWN can look like no heavy rain, offline report cache has no age indicator. PDF anchors cannot report failures inside the page.
8. **P1, subscriptions:** emailAlertsActive is ignored by success UI; deletion is intentionally a backend no-op success for unmatched email/id. Identity/authorization requirements need backend agreement before ownership-based features.
9. **P2, presentation:** hardcoded English in route cards/search/authority/forms, incomplete keyboard search, unassociated labels, light inline colors in dark mode, illustrative circles and incomplete cluster severity ranking.

## 5. Changes implemented

| Path relative to frontend | Change / reason |
|---|---|
| src/api.js | Normalize base/path boundary; handle optional photo URLs safely; reject unexpected response MIME and malformed JSON; retain HTTP error status; consistent initial network failure message; preserve cancellation; opt-in signal support on activeReports/evaluateRoutes; validated PDF Blob helper. Existing callers and success shapes remain compatible. |
| docs/FRONTEND_BACKEND_CONTRACT_V1.md | Actual API schemas/callers/errors, proposed incident PDF/AI/verification/future contracts, routing preservation details, component decisions, blockers and Stage 2–8 roadmap. |
| docs/STAGE_1_IMPLEMENTATION_REPORT.md | This feature audit, implementation inventory and validation evidence. |
| dist/ | Regenerated production/PWA artifacts from the existing build script. |

No UI components were added: existing shared components suffice for current contracts. `requestPdf` is a transport primitive, not a completed mobile download flow. Existing `api.pdfUrl` and links remain unchanged. No speculative endpoint wrapper was introduced.

Compatibility limit: non-JSON successful responses now reject, intentionally, instead of returning null. Optional AbortSignal support is foundational; existing effects are not yet wired to cancellation. Body-stream network errors beyond fetch still use the runtime's error rather than a fully localized error taxonomy. No new libraries or secrets were introduced.

## 6. Backend requirements for Vijendra

- **P0 incident PDF:** implement GET `/api/reports/{id}/evidence.pdf`, binary application/pdf, meaningful non-200 JSON errors, safe attachment filename. Agree missing-ID status (proposed 404), authorization, and CORS exposure of Content-Disposition if frontend reads filenames.
- **P0 truthful AI fields:** expose explicit analysis status, nullable waterVisible, structured observations/reference/limitations and per-report model/time provenance. Eliminate or explicitly identify unsupported fallback depth. Meta aiEnabled is not per-report success.
- **P0/P1 verification semantics:** retain lifecycle status/count/lastConfirmedAt; define separate verification state and confirmation meaning. Expose clearedAt/audit history if required. Do not infer identity from a count.
- **P1 routes:** validate geometries, preserve explicit result index, document incomplete evaluation failures; optionally expose hazard timestamps and walking safety. Frontend must also stop defaulting missing assessments to safe values.
- **P1 ownership/complaints/authority updates:** agree authentication, ownership, pagination, complaint lifecycle, allowed role-based transitions and audit records before adding endpoints/UI.
- **P1 subscriptions/weather:** distinguish subscription creation from confirmed delivery and forecast unavailable from zero rainfall. Current frontend must respect available flags.

Exact unimplemented endpoint schemas are PROPOSED/UNKNOWN, never IMPLEMENTED. Backend was read-only throughout.

## 7. Test results

| Command / check | Actual result |
|---|---|
| git status --short; git branch --show-current; git rev-parse --show-toplevel | FAILED: fatal: not a git repository. No branch/diff claims made. |
| npm ls --depth=0 | Initial PowerShell npm.ps1 invocation blocked by execution policy. |
| npm.cmd ls --depth=0 | PASS: all eight declared top-level packages present, versions recorded in contract. |
| npm.cmd run build, sandbox | FAILED: esbuild could not read ancestor directory, Access is denied; could not resolve Vite configuration. |
| npm.cmd run build, approved outside sandbox | PASS, exit 0: Vite 5.4.21, 87 modules transformed, built in 1.88 s. JS 397.19 kB (gzip 126.65), CSS 39.76 kB (gzip 11.98). PWA generateSW: 8 precache entries, 439.86 KiB; generated sw.js/workbox. |
| Inline Node assertions via node --input-type=module | PASS: 19 isolated checks of URL/photo handling, existing PDF URL, JSON/+json, wrong MIME, malformed JSON, HTTP errors/status, 204, network error, abort preservation/signal forwarding, route request body, PDF Blob. Fetch stubs existed only inside the validation process, not application code. These are transport checks, not backend integration tests. |
| SHA-256 baseline comparison | Only src/api.js differed among readable existing src/public/backend/config/package files checked; page/routing/i18n/style/backend source hashes unchanged. A locked backend/data/jalmarg.mv.db could not be baselined; database content is not claimed verified. |
| npm lint/tests | NOT RUN: no lint/test script exists; no framework added. |
| Manual browser / live API / OSRM / PDFs / camera / GPS / Hindi interaction / accessibility / native Android | NOT TESTED. Build/source preservation do not prove these work. |

All six routes remain declared and compile. Alternative-route logic and existing PDF links are source-preserved, not runtime-verified. Backend source preservation was checked by hashes because no Git baseline exists. No live mutations or external messages were sent.

## 8. Stage 2 readiness

**Ready with blockers.** Existing submission and report display can be improved against known contracts; individual incident PDF and trustworthy enhanced AI/verification presentation need backend agreement/implementation. End-to-end acceptance still requires a browser/device and live service validation pass. No production release or deployment claim is made.

## 9. Next stage scope

After approval: improve report submission and photo lifecycle; remove fabricated share depth; build truthful AI assessment and verification display using actual fields; add incident evidence details and PDF UI only when backend support exists; reuse current design and shared components. Verify success/error/unknown states in English/Hindi and on desktop/mobile. Stage 2 has not started.
