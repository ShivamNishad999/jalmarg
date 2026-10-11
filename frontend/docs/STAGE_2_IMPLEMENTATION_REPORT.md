# JALMARG FRONTEND STAGE 2 IMPLEMENTATION REPORT

2026-10-09 · Stage 2 only · Frontend implementation complete; backend/device limitations below remain open.

## 1. Executive summary

Improved the existing citizen report flow, added reusable AI/evidence/PDF components, removed fabricated depth displays, corrected confirmation/lifecycle presentation, and patched route assessment handling without replacing the routing algorithm. English/Hindi text, focus states, touch targets and reduced-motion support were added within the existing design system.

Read the Stage 1 contract and implementation report, inspected current source/configuration and backend report mappings, and ran the production build before edits. Baseline build passed outside the sandbox: 87 modules; JS 397.19 kB, CSS 39.76 kB; PWA 8 entries/439.86 KiB. Sandbox build could not read ancestor directories. Git still reports “not a git repository”; no repository was initialized. SHA-256 baselines captured frontend and backend source before edits.

No Java/database changes, new dependencies, production fixtures, fake AI data, municipal acceptance claims, or Stage 3 implementation. Existing map, authority, alerts and About pages were preserved; shared report presentation changes also appear in map cards.

## 2. Files changed

Paths are relative to frontend.

| File | Purpose |
|---|---|
| src/pages/ReportPage.jsx | Validated submission, synchronous duplicate lock, abort/stale-response guards, safe photo lifecycle, keyboard coordinate entry, evidence result and truthful share text |
| src/pages/RoutePage.jsx | Validated assessments, unknown-risk geometry/labels/ranking, invalidation on mode/endpoints, GPS/search race guards |
| src/components/shared.jsx | Neutral confirmation/lifecycle presentation, reusable AI assessment, valid/missing depth gauge, unknown safety styling, photo failure and action error feedback |
| src/components/AiAssessmentCard.jsx (new) | Existing DTO source/summary/confidence presentation with limitations and neutral unavailable status |
| src/components/ReportEvidencePanel.jsx (new) | Incident metadata, timestamps, category, lifecycle, confirmation timestamp and capability-gated PDF action |
| src/components/PdfDownloadButton.jsx (new) | Cancellable PDF fetch, validation, browser download, error/loading states and URL cleanup |
| src/utils/evidence.js (new) | Numeric depth, IDs, lifecycle/source mapping, timestamp formatting and latest-task guard |
| src/utils/routeAssessment.js (new) | Index/shape validation, unassessed representation and assessed-first comparator |
| src/utils/pdf.js (new) | PDF Blob validation and safe filenames |
| src/stage2Strings.js (new) | Matching English/Hindi keys for all new interface text |
| src/i18n.jsx | Integrates new strings, stable translation callback, validates stored language, literal interpolation |
| src/api.js | Optional createReport AbortSignal; reject image decode/canvas failures while retaining JPEG compression settings |
| src/styles.css | Evidence layout, touch targets, focus indication, responsive wrapping and reduced motion |
| .env.example | Explicit default-disabled VITE_INCIDENT_PDF_VERIFIED deployment assertion |
| tests/stage2.test.mjs (new) | Focused tests using Node's built-in runner; no test library added |
| docs/FRONTEND_BACKEND_CONTRACT_V1.md | Stage 2 amendment superseding relevant Stage 1 implementation gaps |
| docs/STAGE_2_IMPLEMENTATION_REPORT.md | This handoff |
| dist/ | Regenerated production and PWA artifacts |

Source hash comparison found only the six existing source files listed above changed (api, i18n, styles, shared, ReportPage, RoutePage). New files and documentation/config changes are listed separately. Backend source changes: **0**. Package files and other existing page sources were not edited.

## 3. Report submission improvements

- Preserved camera/gallery, browser GPS, clickable/draggable pin, optional citizen depth, notes, compression and existing multipart names. Added labeled coordinate inputs so location selection is possible using a keyboard without dragging a map.
- Checks finite latitude/longitude and valid ranges, requires photo or selected depth, and limits place/note lengths to backend limits. JPEG/PNG/WebP/GIF selection is checked for type, non-empty content and a 10 MiB size ceiling before decoding.
- Photo preparation and server-save states are separate; no simulated upload percentage or premature success.
- A synchronous ref lock prevents double submission before React updates disabled controls. The form and pin are disabled during save; pending GPS is invalidated. POST still has the same FormData contract.
- Success requires a successful server response with a positive safe integer ID. No ID is generated on the client. Saved results use ReportEvidencePanel.
- On failure, input is retained for retry and a translated alert advises checking the live map before retrying. A network failure or abort can occur after a server commit; frontend-only code cannot guarantee idempotency.
- Abort on unmount and late-response checks prevent stale save updates. Language changes no longer start another initial GPS request.

## 4. Photo lifecycle fixes

Preview URLs are created in a photo-owned effect and revoked on replacement, removal, successful submission and unmount. The compression helper revokes its temporary decode URL on success, decode error, canvas exception and empty encoder output. It now rejects failed processing instead of silently submitting the original unsupported file under photo.jpg.

Latest-task tokens prevent slow prior selections from replacing a newer selection; remove/reset/unmount invalidate pending work. Cancellation/no selected file preserves the existing photo. Input value is reset after selection so retrying the same file works. Existing selected image is retained if a replacement fails; the error remains visible. Compression remains maximum 1280px JPEG at quality 0.82. No library was installed.

Reverse-geocode responses cannot overwrite a manually edited place name or a newer position. Late GPS does not replace a manually chosen position. Actual image decoding/camera permission behavior on Android is NOT TESTED; mocked tests verify compression dimensions, MIME, error behavior and URL cleanup.

## 5. Depth fabrication fix

Removed the 1.0 ft sharing fallback and zero-default report card measurements. Shared formatting accepts only finite, non-negative JavaScript numbers; genuine 0 renders 0.0 ft. Null, missing, negative, nonnumeric and non-finite values render the translated “Depth unavailable.” Share text uses the same formatter as assessment display. The gauge is absent for unknown depth and genuinely empty for zero. Missing depth does not receive a green passability label.

Backend numeric values are still displayed as **backend-reported** values. The frontend cannot distinguish the backend's existing fabricated/fallback 0.3 ft from a supported estimate solely from depthFt. That backend issue remains unresolved and is explicitly explained for CITIZEN source without a citizen category.

## 6. AI assessment states

AiAssessmentCard uses only existing depthSource, depthFt, citizenDepthLevel, aiSummary/Hi and aiConfidence fields:

| Available information | UI treatment |
|---|---|
| AI source | AI-sourced estimate attributed to backend; no claim of independently proven analysis success |
| AI_AND_CITIZEN | Combined source attributed to backend |
| Valid citizen category | Citizen-provided category |
| CITIZEN without category | Neutral analysis-status-unavailable plus backend fallback limitation |
| DEMO | Existing demo designation retained |
| Missing/invalid depth | Depth unavailable; never a guessed measurement |

Summary is displayed only when supplied, with Hindi preferred when present. Finite confidence in [0,1] is labeled model-reported confidence, not scientifically validated accuracy. No regex-derived reference object or fabricated observation is generated. All states explain that explicit analysis status, water visibility and model details are not exposed for that incident. /api/meta is not consulted to infer per-report success.

## 7. Evidence panel implementation

ReportEvidencePanel reuses ReportCard and the design tokens. It shows report ID, location, submission timestamp, supplied photo with failure handling, supported citizen category, valid reported depth, severity, AI summary/source, confirmation event count, current lifecycle, and last confirmation time only when count is positive. Missing values are unavailable. Timestamps are formatted for en-IN/hi-IN in the browser's timezone; no unsupported event timestamp is invented.

No official municipal acceptance or independent verification is claimed. The panel explains the limits of confirmation counts and lifecycle status. It is used on saved-report results; shared cards also receive truthful depth/AI/lifecycle presentation.

## 8. PDF integration readiness

PdfDownloadButton uses the Stage 1 requestPdf transport with AbortSignal. It validates application/pdf Blob MIME, minimum length and `%PDF-` signature, sanitizes a generated filename, triggers a browser anchor download, removes the temporary anchor and revokes object URLs after a 60-second consumption window or on unmount. Busy/error states and retry are bilingual. This is not full PDF structural/security validation.

**BACKEND BLOCKED:** individual evidence.pdf remains absent in inspected ReportController. The button is disabled with a clear explanation by default; it does not probe the endpoint. Deployment may explicitly set `VITE_INCIDENT_PDF_VERIFIED=true` only after an operator verifies the endpoint on the configured backend. This is an operational assertion, not a claim of automatic detection. No capability is inferred from /api/meta.

Existing area/hotspot PDF links in AuthorityPage are unchanged. Desktop browser saving, iOS/Android browser saving and native Capacitor behavior remain NOT TESTED. No native compatibility is claimed.

## 9. Verification display corrections

VerificationBadges now shows lifecycle plus **confirmation events**, including zero, without claiming unique people. AI/citizen provenance is in assessment presentation rather than verification badges. Unknown count is unavailable. ACTIVE is not VERIFIED; CLEARED means reported cleared; EXPIRED is represented explicitly.

The former speculative Submitted → AI Assessed → Live → Resolved timeline is replaced by known submission timestamp and current lifecycle. No historical event ordering or AI completion is invented. Initial lastConfirmedAt with zero confirmations is not displayed as an independent confirmation. Existing confirm/clear actions remain, with a synchronous duplicate-action lock and translated error/status feedback; they do not imply municipal authorization.

## 10. Critical route safety patch

- Keeps OSRM alternatives, geometries, route selection, Google Maps handoff, 700/1500 m detour candidates, duration cutoff and existing up-to-four-card display.
- Backend request remains `{routes, bufferMeters:60}`. Responses join by explicit index rather than array order. Index range/uniqueness, finite non-negative risk, integer/count agreement, boolean safety flags, hazard coordinate ranges, depth, severity, report ID and route distance are checked.
- Missing/duplicate/malformed/failed evaluations yield `assessed:false`, null risk/blocking/safety, and no interpreted hazard data. Unknown hazard list storage is never presented as a measured zero hazard count.
- Geometry remains navigable, unknown selected lines are neutral grey, cards/banners clearly say assessment unavailable, and unassessed candidates never receive a recommendation badge or no-hazard assurance.
- Comparator preserves blocking → risk → duration ranking for assessed candidates and ranks unknown candidates after them. Unknown candidates use duration only within the unknown group. Detours require genuinely assessed blocking initial routes.
- Mode/start/destination changes clear old results and invalidate in-flight searches; late responses cannot restore old safety labels. GPS start-location responses have their own latest-task guard.
- Zero-hazard text warns that unreported flooding may exist. Removed the forced minimum extra minute in recommendation text.

Automated tests verify core mapping/ranking/geometry retention and stale-task behavior. OSRM service behavior, detour execution and route selection interaction are source-inspected, NOT browser/live verified. No routing algorithm replacement was performed.

## 11. Build and test results

| Verification | Result / evidence |
|---|---|
| Initial `npm.cmd run build` in sandbox | FAILED: ancestor directory Access is denied while loading Vite configuration |
| Baseline production build outside sandbox | PASS before edits; 87 modules, PWA generated |
| First edited build | FAILED: missing closing fieldset in ReportPage; corrected before subsequent validation |
| Final `npm.cmd run build` outside sandbox | PASS, exit 0; 94 modules, 1.74 s; JS 409.70 kB (gzip 130.74), CSS 40.68 kB (gzip 12.24); PWA 8 entries/455.96 KiB; sw.js and workbox generated |
| `node --test tests/stage2.test.mjs` | PASS: 17 tests, zero failures; no additional framework |
| Test coverage | Zero/missing depth; AI/citizen/combined/unavailable provenance; lifecycle/count neutrality; route failure/partial/malformed/reordered/duplicate indices; geometry preservation and ranking; PDF signature/MIME/error/cancellation/filename; stale task completion; bilingual key parity; image decode cleanup and 1280px JPEG/error behavior |
| Existing npm lint/test scripts | None configured; no such scripts claimed executed |
| Git status | Not a Git repository; no Git diff/branch claims |
| Source hash comparison | Backend source unchanged; existing Area/Hotspot PDF page source unchanged |
| Browser-control initialization | BLOCKED: two attempts failed with “trusted Node process exited unexpectedly; kernel reset, rerun your request” |
| Desktop/tablet/small Android viewport, light/dark, actual keyboard/screen reader interaction | SOURCE INSPECTED ONLY; visual/manual checks NOT TESTED due to browser helper failure |
| Live backend submission, OSRM, camera/GPS, incident PDF download, native Android | NOT TESTED; missing incident endpoint additionally BACKEND BLOCKED |

Tests use contract fixtures/mocks only inside the test process. No fake production API responses were introduced. Key parity and CSS review do not establish visual quality, accessibility compliance or end-to-end behavior. No real incident was submitted during testing.

## 12. Backend blockers for Vijendra

1. **Unresolved photo-only fallback:** ReportService may return 0.3 ft/CITIZEN when AI is unavailable and citizen depth was never supplied. Expose truthful provenance/unknown depth; frontend cannot reconstruct this from the current DTO.
2. **Per-report AI state:** provide agreed explicit status, nullable waterVisible, structured observations/reference, limitations, model and analysis timestamp. Distinguish unavailable/disabled/failed from success. No field names were fabricated into current API calls.
3. **Incident PDF:** implement and verify `/api/reports/{id}/evidence.pdf`, application/pdf, safe attachment filename and meaningful errors; agree missing-ID status/permissions. Only then enable the deployment flag.
4. **Confirmation semantics:** unique verifier identity/authorization and municipal status cannot be inferred from current count/lifecycle. Supply separate semantics only if those capabilities are required.
5. **Submission idempotency:** optional server-supported idempotency is needed to prevent duplicates after ambiguous network failure; the UI lock only prevents repeated concurrent clicks.

## 13. Known limitations

Browser control was unavailable, so physical-device camera/gallery cancellation, memory profiling, real responsive layouts, focus traversal, dark contrast, actual PDF downloads and API integration remain unverified. CSS uses existing surface/color tokens, responsive evidence layout, 44px report/evidence buttons, focus outlines, and reduced-motion rules, but these are source-level checks.

HEIC and other unsupported formats are rejected rather than silently renamed. Image encoding is not cancellable mid-decode; stale results are ignored and temporary URLs are released on completion. Route network calls are not actively aborted; stale results are discarded and stale detour iterations stop. Partial route assessment remains visibly incomplete.

The deployment PDF flag can be misconfigured by an operator; enabled requests still validate errors/content. Existing PWA cache freshness, unrelated page translations, authority request races and PlaceSearch keyboard/stale-request gaps remain outside this focused Stage 2 patch. Existing backend fallback estimates remain unresolved. No full browser/native compatibility or accessibility certification is claimed.

## 14. Stage 3 readiness

**Ready with blockers and pending interactive acceptance.** Frontend foundations and safety patch compile and have focused automated coverage. Before shipping, run desktop/mobile/English/Hindi/light/dark interaction checks plus real-service validation in a suitable test environment. Backend AI provenance and incident PDF are still required for complete evidence functionality. My Reports/ownership and later complaint flows need the separate contracts recorded in Stage 1.

Stage 2 stops here. Stage 3 has not started and requires approval.
