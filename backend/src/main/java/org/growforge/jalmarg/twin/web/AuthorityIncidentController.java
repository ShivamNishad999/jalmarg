package org.growforge.jalmarg.twin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.growforge.jalmarg.dto.Dtos.ReportView;
import org.growforge.jalmarg.twin.dto.TwinDtos.*;
import org.growforge.jalmarg.twin.model.AuditEvent;
import org.growforge.jalmarg.twin.model.MunicipalIncident;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.model.ResponseResource;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.growforge.jalmarg.twin.service.MunicipalIncidentService;
import org.growforge.jalmarg.twin.service.MunicipalReportReviewService;
import org.growforge.jalmarg.twin.service.MunicipalReportReviewService.DetailedReportReviewView;
import org.growforge.jalmarg.twin.service.MunicipalReportReviewService.ReportReviewDecisionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/authority")
public class AuthorityIncidentController {

    private final MunicipalIncidentService incidentService;
    private final MunicipalAuthService authService;
    private final MunicipalReportReviewService reviewService;

    public AuthorityIncidentController(
            MunicipalIncidentService incidentService,
            MunicipalAuthService authService,
            MunicipalReportReviewService reviewService
    ) {
        this.incidentService = incidentService;
        this.authService = authService;
        this.reviewService = reviewService;
    }

    private MunicipalPrincipal getPrincipal(String adminKey, String jalmargKey, String authHeader, String operatorId) {
        MunicipalPrincipal principal = authService.authenticate(authHeader, adminKey, jalmargKey);
        // If authenticated via admin key and an operatorId header is explicitly provided, use that for attribution
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

    // ---------------------------------------------------------------- Citizen Report Review & Verification

    @GetMapping("/reports")
    public List<ReportView> getReportsForReview(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Integer minConfirmations,
            @RequestParam(defaultValue = "30") Integer days,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_VIEWER);
        return reviewService.getReportsForReview(status, verificationStatus, severity, area, minConfirmations, days);
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<DetailedReportReviewView> getDetailedReport(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_VIEWER);
        return ResponseEntity.ok(reviewService.getDetailedReport(id));
    }

    @PostMapping("/reports/{id}/review")
    public ResponseEntity<DetailedReportReviewView> reviewReport(
            @PathVariable Long id,
            @RequestBody ReportReviewDecisionRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        DetailedReportReviewView result = reviewService.reviewReport(id, req, principal, clientIp);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reports/{id}/verify")
    public ResponseEntity<DetailedReportReviewView> verifyReport(
            @PathVariable Long id,
            @RequestBody ReportReviewDecisionRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        DetailedReportReviewView result = reviewService.reviewReport(id, req, principal, clientIp);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reports/{id}/reject")
    public ResponseEntity<DetailedReportReviewView> rejectReport(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String reason = body != null && body.containsKey("reason") ? body.get("reason") : "Report rejected by municipal review officer.";
        String clientIp = extractClientIp(request);
        ReportReviewDecisionRequest decisionReq = new ReportReviewDecisionRequest("REJECTED", reason, false, null, null);
        DetailedReportReviewView result = reviewService.reviewReport(id, decisionReq, principal, clientIp);
        return ResponseEntity.ok(result);
    }

    // ---------------------------------------------------------------- Municipal Incident Lifecycle & Dispatch

    @GetMapping("/incidents")
    public List<MunicipalIncidentView> getIncidents(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_VIEWER);
        return incidentService.getAllIncidents().stream().map(MunicipalIncidentView::of).toList();
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public MunicipalIncidentView createIncident(
            @RequestBody MunicipalIncidentRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);

        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incident title cannot be empty.");
        }
        // Operational coordinate intake filter (approximate Delhi NCR bounding envelope; not an official statutory boundary)
        if (req.latitude() < 28.0 || req.latitude() > 29.2 || req.longitude() < 76.5 || req.longitude() > 77.8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Incident coordinates fall outside the JalMarg operational bounding box (28.0-29.2 N, 76.5-77.8 E). Note: This envelope is an intake filter, not an official statutory boundary."
            );
        }
        String clientIp = extractClientIp(request);
        MunicipalIncident created = incidentService.createIncident(req, principal.username(), clientIp);
        return MunicipalIncidentView.of(created);
    }

    @GetMapping("/incidents/{id}")
    public ResponseEntity<MunicipalIncidentView> getIncidentById(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_VIEWER);
        MunicipalIncident incident = incidentService.getIncidentById(id);
        if (incident == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(MunicipalIncidentView.of(incident));
    }

    @PatchMapping({"/incidents/{id}/status", "/incidents/{id}"})
    public ResponseEntity<MunicipalIncidentView> updateIncidentStatus(
            @PathVariable Long id,
            @RequestBody IncidentStatusUpdateRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        MunicipalIncident updated = incidentService.updateIncidentStatus(id, req, principal.username(), clientIp);
        return ResponseEntity.ok(MunicipalIncidentView.of(updated));
    }

    @PostMapping("/incidents/{id}/assign")
    public ResponseEntity<MunicipalIncidentView> assignIncident(
            @PathVariable Long id,
            @RequestBody IncidentAssignRequest req,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String clientIp = extractClientIp(request);
        MunicipalIncident assigned = incidentService.assignIncident(id, req, principal.username(), clientIp);
        return ResponseEntity.ok(MunicipalIncidentView.of(assigned));
    }

    @GetMapping("/resources")
    public List<ResponseResourceView> getResources(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_VIEWER);
        return incidentService.getAllResources().stream().map(ResponseResourceView::of).toList();
    }

    @GetMapping("/audit-logs")
    public List<AuditEventView> getAuditLogs(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId
    ) {
        MunicipalPrincipal principal = getPrincipal(adminKey, jalmargKey, authHeader, operatorId);
        authService.enforceRole(principal, Role.MUNICIPAL_VIEWER);
        return incidentService.getRecentAuditEvents().stream().map(AuditEventView::of).toList();
    }

    private static String extractClientIp(HttpServletRequest req) {
        if (req == null) return "127.0.0.1";
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "127.0.0.1";
    }
}
