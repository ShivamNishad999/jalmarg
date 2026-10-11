package org.growforge.jalmarg.twin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.growforge.jalmarg.twin.dto.GroundTruthDtos.*;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.service.GroundTruthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ml")
public class GroundTruthController {

    private final GroundTruthService groundTruthService;
    private final MunicipalAuthService authService;

    public GroundTruthController(
            GroundTruthService groundTruthService,
            MunicipalAuthService authService
    ) {
        this.groundTruthService = groundTruthService;
        this.authService = authService;
    }

    private MunicipalPrincipal getPrincipal(String adminKey, String jalmargKey, String authHeader, String operatorId) {
        MunicipalPrincipal principal = authService.authenticate(authHeader, adminKey, jalmargKey);
        if (!principal.isTokenAuthenticated() && operatorId != null && !operatorId.isBlank()) {
            String sanitized = operatorId.replaceAll("[\\r\\n\\t\"]", "").trim();
            if (sanitized.length() > 64) sanitized = sanitized.substring(0, 64);
            return new MunicipalPrincipal(
                    principal.userId(),
                    sanitized.isEmpty() ? principal.username() : sanitized,
                    principal.fullName(),
                    principal.department(),
                    principal.role(),
                    false
            );
        }
        return principal;
    }

    private static String extractClientIp(HttpServletRequest req) {
        if (req == null) return "127.0.0.1";
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "127.0.0.1";
    }

    // ---------------------------------------------------------------- Observation Queries

    @GetMapping("/ground-truth/observations")
    public List<GroundTruthObservationView> getObservations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) String corridor
    ) {
        return groundTruthService.getObservations(status, verificationStatus, corridor);
    }

    @GetMapping("/ground-truth/observations/{id}")
    public ResponseEntity<GroundTruthObservationView> getObservationById(@PathVariable Long id) {
        return ResponseEntity.ok(groundTruthService.getObservationById(id));
    }

    @GetMapping("/ground-truth/queue")
    public List<GroundTruthObservationView> getReviewQueue() {
        return groundTruthService.getReviewQueue();
    }

    @GetMapping("/ground-truth/summary")
    public ResponseEntity<GroundTruthSummaryView> getSummary() {
        return ResponseEntity.ok(groundTruthService.getSummary());
    }

    @GetMapping("/ground-truth/provenance")
    public List<HistoricalSourceView> getHistoricalSources() {
        return groundTruthService.getHistoricalSources();
    }

    @GetMapping("/vision/reliability-audit")
    public ResponseEntity<?> getCvReliabilityAudit() {
        CvReliabilityAuditView audit = groundTruthService.getLatestCvReliabilityAudit();
        if (audit != null) {
            return ResponseEntity.ok(audit);
        }
        // Fallback or empty audit report
        return ResponseEntity.ok(Map.of(
                "model_version", "jalmarg-cv-lightroadwaternet-2026.1",
                "reliability_verdict", "ASSISTIVE_ONLY_DEPLOYMENT_BLOCKED_FOR_AUTONOMY",
                "folder_inferred_negatives_count", 575,
                "human_verified_negatives_count", 0,
                "license_status", "LICENSE_REVIEW_REQUIRED",
                "abstention_policy", Map.of(
                        "policy_name", "DUAL_THRESHOLD_UNCERTAINTY_ABSTENTION",
                        "lower_threshold", 0.35,
                        "upper_threshold", 0.65
                )
        ));
    }

    // ---------------------------------------------------------------- Municipal Verification Workflow

    @PostMapping("/ground-truth/negative-observation")
    public ResponseEntity<GroundTruthObservationView> recordNegativeObservation(
            @RequestBody NegativeObservationRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        GroundTruthObservationView view = groundTruthService.recordVerifiedNegativeObservation(req, principal, clientIp);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/ground-truth/observations/{id}/review")
    public ResponseEntity<GroundTruthObservationView> reviewObservation(
            @PathVariable Long id,
            @RequestBody ObservationDecisionRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        GroundTruthObservationView view = groundTruthService.reviewObservation(id, req, principal, clientIp);
        return ResponseEntity.ok(view);
    }

    // ---------------------------------------------------------------- Phase 25D Evidence Audit Endpoints

    @GetMapping("/evidence-audit/summary")
    public ResponseEntity<EvidenceAuditSummaryView> getEvidenceAuditSummary() {
        return ResponseEntity.ok(groundTruthService.getEvidenceAuditSummary());
    }

    @GetMapping("/evidence-audit/observations")
    public List<ObservationEvidenceAuditView> getObservationEvidenceAudits() {
        return groundTruthService.getObservationEvidenceAudits();
    }

    @GetMapping("/evidence-audit/flyway-status")
    public ResponseEntity<FlywayIntegrityStatusView> getFlywayIntegrityStatus() {
        return ResponseEntity.ok(groundTruthService.getFlywayIntegrityStatus());
    }

    // ==============================================================================
    // Phase 25E-B: Historical Rainfall Coverage & Municipal Inspection Workflows
    // ==============================================================================

    @GetMapping("/rainfall/coverage")
    public List<RainfallCoverageView> getRainfallCoverage() {
        return groundTruthService.getRainfallCoverage();
    }

    @GetMapping("/rainfall/summary")
    public ResponseEntity<Map<String, Object>> getRainfallSummary() {
        return ResponseEntity.ok(groundTruthService.getRainfallManifestSummary());
    }

    @GetMapping("/inspections/dry-road")
    public List<DryRoadInspectionView> getDryRoadInspections(@RequestParam(required = false) String reviewStatus) {
        return groundTruthService.getDryRoadInspections(reviewStatus);
    }

    @PostMapping("/inspections/dry-road")
    public ResponseEntity<DryRoadInspectionView> recordDryRoadInspection(
            @RequestBody DryRoadInspectionRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        DryRoadInspectionView view = groundTruthService.recordDryRoadInspection(req, principal, clientIp);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/inspections/dry-road/{id}/review")
    public ResponseEntity<DryRoadInspectionView> reviewDryRoadInspection(
            @PathVariable Long id,
            @RequestBody DryRoadInspectionDecisionRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        DryRoadInspectionView view = groundTruthService.reviewDryRoadInspection(id, req, principal, clientIp);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/depth-measurements")
    public List<CalibratedDepthMeasurementView> getCalibratedDepthMeasurements(@RequestParam(required = false) String evidenceStatus) {
        return groundTruthService.getCalibratedDepthMeasurements(evidenceStatus);
    }

    @PostMapping("/depth-measurements")
    public ResponseEntity<CalibratedDepthMeasurementView> recordCalibratedDepthMeasurement(
            @RequestBody CalibratedDepthMeasurementRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        CalibratedDepthMeasurementView view = groundTruthService.recordCalibratedDepthMeasurement(req, principal, clientIp);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/depth-measurements/{id}/review")
    public ResponseEntity<CalibratedDepthMeasurementView> reviewCalibratedDepthMeasurement(
            @PathVariable Long id,
            @RequestBody CalibratedDepthDecisionRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        CalibratedDepthMeasurementView view = groundTruthService.reviewCalibratedDepthMeasurement(id, req, principal, clientIp);
        return ResponseEntity.ok(view);
    }
}
