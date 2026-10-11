package org.growforge.jalmarg.twin.service;

import org.growforge.jalmarg.dto.Dtos.ReportView;
import org.growforge.jalmarg.model.Enums.DepthLevel;
import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.ReportRepository;
import org.growforge.jalmarg.twin.dto.TwinDtos.MunicipalIncidentRequest;
import org.growforge.jalmarg.twin.dto.TwinDtos.MunicipalIncidentView;
import org.growforge.jalmarg.twin.model.AuditEvent;
import org.growforge.jalmarg.twin.model.MunicipalIncident;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.model.ReportObservation;
import org.growforge.jalmarg.twin.repo.AuditEventRepository;
import org.growforge.jalmarg.twin.repo.ReportObservationRepository;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class MunicipalReportReviewService {

    private static final Logger log = LoggerFactory.getLogger(MunicipalReportReviewService.class);

    private final ReportRepository reportRepo;
    private final ReportObservationRepository observationRepo;
    private final MunicipalIncidentService incidentService;
    private final AuditEventRepository auditRepo;
    private final MunicipalAuthService authService;
    private final org.growforge.jalmarg.twin.repo.ReportImageInferenceRepository imageInferenceRepo;

    public record ReportReviewDecisionRequest(
            String decision, // VERIFIED, REJECTED, UNDER_REVIEW, FLAGGED_DUPLICATE
            String operatorNotes,
            String reviewNotes,
            boolean promoteToIncident,
            String incidentType,
            Long zoneId
    ) {
        public ReportReviewDecisionRequest(String decision, String operatorNotes, boolean promoteToIncident, String incidentType, Long zoneId) {
            this(decision, operatorNotes, operatorNotes, promoteToIncident, incidentType, zoneId);
        }

        public String effectiveNotes() {
            if (operatorNotes != null && !operatorNotes.isBlank()) return operatorNotes.trim();
            if (reviewNotes != null && !reviewNotes.isBlank()) return reviewNotes.trim();
            return null;
        }
    }

    public record ReportObservationView(
            Long id,
            Long reportId,
            double latitude,
            double longitude,
            String citizenDepthLevel,
            Double depthFt,
            String photoUrl,
            String clientIp,
            String note,
            Instant submittedAt
    ) {
        public static ReportObservationView of(ReportObservation o) {
            return new ReportObservationView(
                    o.getId(),
                    o.getReportId(),
                    o.getLatitude(),
                    o.getLongitude(),
                    o.getCitizenDepthLevel() != null ? o.getCitizenDepthLevel().name() : null,
                    o.getDepthFt(),
                    o.getPhotoKey() != null ? "/api/photos/" + o.getPhotoKey() : null,
                    o.getClientIp(),
                    o.getNote(),
                    o.getSubmittedAt()
            );
        }
    }

    public record ReportImageInferenceView(
            Long id,
            Long reportId,
            String modelVersion,
            String prediction,
            Double confidenceScore,
            Double rawProbability,
            String inferenceStatus,
            boolean reviewRequired,
            String imageSha256,
            String failureReason,
            String modelProvenance,
            String limitationsJson,
            Double inferenceDurationMs,
            Instant createdAt
    ) {
        public static ReportImageInferenceView of(org.growforge.jalmarg.twin.model.ReportImageInference inf) {
            if (inf == null) return null;
            return new ReportImageInferenceView(
                    inf.getId(), inf.getReportId(), inf.getModelVersion(),
                    inf.getPrediction(), inf.getConfidenceScore(), inf.getRawProbability(),
                    inf.getInferenceStatus(), inf.isReviewRequired(), inf.getImageSha256(),
                    inf.getFailureReason(), inf.getModelProvenance(), inf.getLimitationsJson(),
                    inf.getInferenceDurationMs(), inf.getCreatedAt()
            );
        }
    }

    public record DetailedReportReviewView(
            ReportView report,
            String verificationStatus,
            String verifiedBy,
            Instant verifiedAt,
            String verificationNotes,
            Long parentReportId,
            boolean isDuplicateMerged,
            List<ReportObservationView> observations,
            Long linkedIncidentId,
            ReportImageInferenceView imageInference
    ) {
        public DetailedReportReviewView(
                ReportView report, String verificationStatus, String verifiedBy, Instant verifiedAt,
                String verificationNotes, Long parentReportId, boolean isDuplicateMerged,
                List<ReportObservationView> observations, Long linkedIncidentId) {
            this(report, verificationStatus, verifiedBy, verifiedAt, verificationNotes, parentReportId,
                    isDuplicateMerged, observations, linkedIncidentId, null);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MunicipalReportReviewService(
            ReportRepository reportRepo,
            ReportObservationRepository observationRepo,
            MunicipalIncidentService incidentService,
            AuditEventRepository auditRepo,
            MunicipalAuthService authService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            org.growforge.jalmarg.twin.repo.ReportImageInferenceRepository imageInferenceRepo
    ) {
        this.reportRepo = reportRepo;
        this.observationRepo = observationRepo;
        this.incidentService = incidentService;
        this.auditRepo = auditRepo;
        this.authService = authService;
        this.imageInferenceRepo = imageInferenceRepo;
    }

    public MunicipalReportReviewService(
            ReportRepository reportRepo,
            ReportObservationRepository observationRepo,
            MunicipalIncidentService incidentService,
            AuditEventRepository auditRepo,
            MunicipalAuthService authService
    ) {
        this(reportRepo, observationRepo, incidentService, auditRepo, authService, null);
    }

    @Transactional(readOnly = true)
    public List<ReportView> getReportsForReview(String status, String verificationStatus, String severity,
                                                String area, Integer minConfirmations, Integer days) {
        Instant since = days != null && days > 0 ? Instant.now().minus(Duration.ofDays(days)) : Instant.now().minus(Duration.ofDays(30));
        return reportRepo.findByCreatedAtAfterOrderByCreatedAtDesc(since).stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().name().equalsIgnoreCase(status.trim()))
                .filter(r -> verificationStatus == null || verificationStatus.isBlank() || r.getVerificationStatus().equalsIgnoreCase(verificationStatus.trim()))
                .filter(r -> severity == null || severity.isBlank() || (r.getSeverity() != null && r.getSeverity().name().equalsIgnoreCase(severity.trim())))
                .filter(r -> area == null || area.isBlank() || (r.getHotspot() != null && area.equalsIgnoreCase(r.getHotspot().getArea())))
                .filter(r -> minConfirmations == null || r.getConfirmations() >= minConfirmations)
                .map(ReportView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public DetailedReportReviewView getDetailedReport(Long reportId) {
        Report report = reportRepo.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Citizen report not found: " + reportId));

        List<ReportObservationView> observations = observationRepo.findByReportIdOrderBySubmittedAtAsc(reportId).stream()
                .map(ReportObservationView::of)
                .toList();

        // Check if there is a linked incident
        Long linkedIncidentId = incidentService.getAllIncidents().stream()
                .filter(inc -> reportId.equals(inc.getRelatedReportId()))
                .map(MunicipalIncident::getId)
                .findFirst()
                .orElse(null);

        ReportImageInferenceView imageInference = null;
        if (imageInferenceRepo != null) {
            imageInference = imageInferenceRepo.findTopByReportIdOrderByCreatedAtDesc(reportId)
                    .map(ReportImageInferenceView::of)
                    .orElse(null);
        }

        return new DetailedReportReviewView(
                ReportView.of(report),
                report.getVerificationStatus(),
                report.getVerifiedBy(),
                report.getVerifiedAt(),
                report.getVerificationNotes(),
                report.getParentReportId(),
                report.isDuplicateMerged(),
                observations,
                linkedIncidentId,
                imageInference
        );
    }

    @Transactional
    public DetailedReportReviewView reviewReport(Long reportId, ReportReviewDecisionRequest req,
                                                MunicipalPrincipal principal, String clientIp) {
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);

        if (req.decision() == null || req.decision().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision (VERIFIED, REJECTED, UNDER_REVIEW) is required.");
        }

        String targetDecision = req.decision().trim().toUpperCase(Locale.ROOT);
        if ("VERIFY".equals(targetDecision)) targetDecision = "VERIFIED";
        if ("REJECT".equals(targetDecision)) targetDecision = "REJECTED";

        if (!List.of("VERIFIED", "REJECTED", "UNDER_REVIEW", "FLAGGED_DUPLICATE", "UNVERIFIED").contains(targetDecision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification decision: " + req.decision());
        }

        Report report = reportRepo.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Citizen report not found: " + reportId));

        String prevStatus = report.getVerificationStatus();
        report.setVerificationStatus(targetDecision);
        if ("REJECTED".equals(targetDecision)) {
            report.setStatus(ReportStatus.CLEARED);
        }
        report.setVerifiedBy(principal.username());
        report.setVerifiedAt(Instant.now());
        String notes = req.effectiveNotes();
        if (notes != null) {
            report.setVerificationNotes(notes);
        }

        Report saved = reportRepo.save(report);

        // Audit Trail
        String actionType = "VERIFIED".equals(targetDecision) ? "REPORT_VERIFY"
                : "REJECTED".equals(targetDecision) ? "REPORT_REJECT" : "REPORT_REVIEW_UPDATE";

        auditRepo.save(new AuditEvent(
                principal.username(),
                principal.role().name(),
                actionType,
                "Report",
                saved.getId().toString(),
                clientIp,
                String.format(Locale.ROOT, "{\"previousStatus\":\"%s\",\"newStatus\":\"%s\",\"notes\":\"%s\"}",
                        prevStatus, targetDecision, notes != null ? notes.replace("\"", "\\\"") : "")
        ));

        // Optionally promote to formal municipal incident
        Long linkedIncidentId = null;
        if (req.promoteToIncident() && "VERIFIED".equals(targetDecision)) {
            MunicipalIncidentRequest incReq = new MunicipalIncidentRequest(
                    "Verified Flood: " + (saved.getLocationName() != null ? saved.getLocationName() : "Report #" + saved.getId()),
                    "Promoted from citizen report #" + saved.getId() + ". Depth: " + saved.getDepthFt() + " ft. Notes: " + (saved.getNote() != null ? saved.getNote() : "None"),
                    req.incidentType() != null && !req.incidentType().isBlank() ? req.incidentType() : "SURFACE_FLOODING",
                    saved.getSeverity() != null ? saved.getSeverity().name() : "HIGH",
                    saved.getLatitude(),
                    saved.getLongitude(),
                    saved.getLocationName() != null ? saved.getLocationName() : "Delhi NCR",
                    req.zoneId(),
                    saved.getId()
            );
            MunicipalIncident createdInc = incidentService.createIncident(incReq, principal.username(), clientIp);
            linkedIncidentId = createdInc.getId();
            log.info("Promoted citizen report #{} into Municipal Incident #{}", saved.getId(), createdInc.getIncidentNumber());
        }

        return getDetailedReport(saved.getId());
    }

    @Transactional
    public void recordObservation(Long reportId, double lat, double lng, DepthLevel level, Double depthFt,
                                  String photoKey, String clientIp, String note) {
        try {
            ReportObservation obs = new ReportObservation(reportId, lat, lng, level, depthFt, photoKey, clientIp, note);
            observationRepo.save(obs);
        } catch (Exception e) {
            log.error("Failed to persist observation for report #{}", reportId, e);
        }
    }
}
