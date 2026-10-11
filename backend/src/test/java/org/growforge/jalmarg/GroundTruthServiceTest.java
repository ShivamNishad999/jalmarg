package org.growforge.jalmarg;

import org.growforge.jalmarg.twin.dto.GroundTruthDtos.*;
import org.growforge.jalmarg.twin.model.CvReliabilityAudit;
import org.growforge.jalmarg.twin.model.GroundTruthObservation;
import org.growforge.jalmarg.twin.model.HistoricalSourceRegistry;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.model.VerificationAuditLog;
import org.growforge.jalmarg.twin.repo.CvReliabilityAuditRepository;
import org.growforge.jalmarg.twin.repo.GroundTruthObservationRepository;
import org.growforge.jalmarg.twin.repo.HistoricalSourceRegistryRepository;
import org.growforge.jalmarg.twin.repo.VerificationAuditLogRepository;
import org.growforge.jalmarg.twin.repo.AuditEventRepository;
import org.growforge.jalmarg.twin.repo.MunicipalUserRepository;
import org.growforge.jalmarg.twin.service.GroundTruthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.growforge.jalmarg.twin.web.GroundTruthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroundTruthServiceTest {

    private GroundTruthObservationRepository observationRepo;
    private HistoricalSourceRegistryRepository sourceRepo;
    private VerificationAuditLogRepository auditLogRepo;
    private CvReliabilityAuditRepository cvAuditRepo;
    private MunicipalAuthService authService;
    private GroundTruthService service;
    private GroundTruthController controller;

    @BeforeEach
    void setUp() {
        observationRepo = mock(GroundTruthObservationRepository.class);
        sourceRepo = mock(HistoricalSourceRegistryRepository.class);
        auditLogRepo = mock(VerificationAuditLogRepository.class);
        cvAuditRepo = mock(CvReliabilityAuditRepository.class);
        MunicipalUserRepository userRepo = mock(MunicipalUserRepository.class);
        AuditEventRepository auditRepo = mock(AuditEventRepository.class);
        authService = new MunicipalAuthService(userRepo, auditRepo, "test-admin-key-2026");

        service = new GroundTruthService(observationRepo, sourceRepo, auditLogRepo, cvAuditRepo);
        controller = new GroundTruthController(service, authService);
    }

    @Test
    @DisplayName("getObservations returns mapped ground truth views")
    void testGetObservations() {
        GroundTruthObservation obs = new GroundTruthObservation();
        obs.setId(1L);
        obs.setObservationId("HIST-2023-DEL-01");
        obs.setLatitude(28.6366);
        obs.setLongitude(77.2241);
        obs.setLocationDescription("Minto Bridge Underpass");
        obs.setWaterloggingStatus("VERIFIED_WATERLOGGING");
        obs.setVerificationStatus("VERIFIED");
        obs.setMeasuredWaterDepthM(1.25);
        obs.setCorridorName("Connaught Place Arterial");

        when(observationRepo.findAllByOrderByObservationTimestampDesc()).thenReturn(List.of(obs));

        List<GroundTruthObservationView> views = controller.getObservations(null, null, null);
        assertEquals(1, views.size());
        assertEquals("HIST-2023-DEL-01", views.get(0).observationId());
        assertEquals("VERIFIED_WATERLOGGING", views.get(0).waterloggingStatus());
        assertEquals(1.25, views.get(0).measuredWaterDepthM());
    }

    @Test
    @DisplayName("getSummary computes correct aggregate counts")
    void testGetSummary() {
        GroundTruthObservation pos = new GroundTruthObservation();
        pos.setWaterloggingStatus("VERIFIED_WATERLOGGING");
        pos.setMeasuredWaterDepthM(1.2);
        pos.setAlignmentStatus("ALIGNED");

        GroundTruthObservation neg = new GroundTruthObservation();
        neg.setWaterloggingStatus("VERIFIED_NO_WATERLOGGING");
        neg.setMeasuredWaterDepthM(0.0);
        neg.setAlignmentStatus("ALIGNED");

        when(observationRepo.count()).thenReturn(2L);
        when(observationRepo.countByWaterloggingStatus("VERIFIED_WATERLOGGING")).thenReturn(1L);
        when(observationRepo.countByWaterloggingStatus("VERIFIED_NO_WATERLOGGING")).thenReturn(1L);
        when(observationRepo.countByReviewQueueStatus("IN_QUEUE")).thenReturn(0L);
        when(observationRepo.countByWaterloggingStatus("DISPUTED")).thenReturn(0L);
        when(observationRepo.findAll()).thenReturn(List.of(pos, neg));

        ResponseEntity<GroundTruthSummaryView> resp = controller.getSummary();
        assertNotNull(resp.getBody());
        GroundTruthSummaryView summary = resp.getBody();
        assertEquals(2, summary.totalObservations());
        assertEquals(1, summary.verifiedPositives());
        assertEquals(1, summary.verifiedNegatives());
        assertEquals(1, summary.depthMeasurements());
        assertEquals(2, summary.monsoonSeasonsCovered());
        assertEquals("100.0%", summary.rainfallAlignmentCompleteness());
    }

    @Test
    @DisplayName("recordVerifiedNegativeObservation succeeds for authorized officer with valid coordinates")
    void testRecordVerifiedNegativeSuccess() {
        MunicipalPrincipal principal = new MunicipalPrincipal(
                1L, "officer_verma", "Raj Verma", "PWD_DELHI", Role.MUNICIPAL_OPERATOR, true
        );

        NegativeObservationRequest req = new NegativeObservationRequest(
                28.6366, 77.2241, "Minto Bridge Dry Inspection", "Connaught Place",
                Instant.now().minusSeconds(3600), true,
                "Roadway completely dry; pumps on standby.", "photo.jpg"
        );

        when(observationRepo.save(any(GroundTruthObservation.class))).thenAnswer(i -> {
            GroundTruthObservation saved = i.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        GroundTruthObservationView view = service.recordVerifiedNegativeObservation(req, principal, "127.0.0.1");
        assertNotNull(view);
        assertEquals("VERIFIED_NO_WATERLOGGING", view.waterloggingStatus());
        assertEquals("VERIFIED", view.verificationStatus());
        assertEquals(0.0, view.measuredWaterDepthM());

        // Verify audit log recorded
        verify(auditLogRepo, times(1)).save(any(VerificationAuditLog.class));
    }

    @Test
    @DisplayName("recordVerifiedNegativeObservation rejects outside Delhi coordinates")
    void testRecordVerifiedNegativeRejectsOutOfBounds() {
        MunicipalPrincipal principal = new MunicipalPrincipal(
                1L, "officer_verma", "Raj Verma", "PWD_DELHI", Role.MUNICIPAL_OPERATOR, true
        );

        NegativeObservationRequest req = new NegativeObservationRequest(
                19.0760, 72.8777, "Mumbai road", "Marine Drive",
                Instant.now(), true, "Dry road notes", null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.recordVerifiedNegativeObservation(req, principal, "127.0.0.1")
        );
        assertTrue(ex.getReason().contains("outside monitored Delhi NCR bounds"));
    }

    @Test
    @DisplayName("recordVerifiedNegativeObservation rejects viewer role")
    void testRecordVerifiedNegativeRejectsViewer() {
        MunicipalPrincipal principal = new MunicipalPrincipal(
                1L, "viewer_guest", "Guest Viewer", "MUNICIPAL", Role.MUNICIPAL_VIEWER, true
        );

        NegativeObservationRequest req = new NegativeObservationRequest(
                28.6366, 77.2241, "Minto Bridge", "Connaught Place",
                Instant.now(), true, "Dry road notes", null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.recordVerifiedNegativeObservation(req, principal, "127.0.0.1")
        );
        assertTrue(ex.getReason().contains("not authorized"));
    }

    @Test
    @DisplayName("reviewObservation verifies and updates depth with audit event")
    void testReviewObservationVerify() {
        MunicipalPrincipal principal = new MunicipalPrincipal(
                1L, "admin_sharma", "Amit Sharma", "PWD_DELHI", Role.MUNICIPAL_ADMIN, true
        );

        GroundTruthObservation obs = new GroundTruthObservation();
        obs.setId(5L);
        obs.setObservationId("HIST-05");
        obs.setVerificationStatus("UNDER_REVIEW");

        when(observationRepo.findById(5L)).thenReturn(Optional.of(obs));
        when(observationRepo.save(any(GroundTruthObservation.class))).thenAnswer(i -> i.getArgument(0));

        ObservationDecisionRequest req = new ObservationDecisionRequest(
                "VERIFY", "Confirmed from CCTV telemetry", "Depth verified by field crew", 0.75
        );

        GroundTruthObservationView view = service.reviewObservation(5L, req, principal, "127.0.0.1");
        assertEquals("VERIFIED", view.verificationStatus());
        assertEquals(0.75, view.measuredWaterDepthM());

        verify(auditLogRepo, times(1)).save(any(VerificationAuditLog.class));
    }

    @Test
    @DisplayName("getCvReliabilityAudit returns abstention policy and label audit details")
    void testGetCvReliabilityAudit() {
        CvReliabilityAudit audit = new CvReliabilityAudit();
        audit.setId(1L);
        audit.setModelVersion("jalmarg-cv-lightroadwaternet-2026.1");
        audit.setTotalImagesAudited(1049);
        audit.setUniqueImagesCount(671);
        audit.setExactDuplicatesCount(378);
        audit.setCrossClassDuplicatesCount(1);
        audit.setFolderInferredNegativesCount(575);
        audit.setHumanVerifiedNegativesCount(0);
        audit.setLicenseStatus("LICENSE_REVIEW_REQUIRED");
        audit.setAbstentionLowerThreshold(0.35);
        audit.setAbstentionUpperThreshold(0.65);
        audit.setReliabilityVerdict("ASSISTIVE_ONLY_DEPLOYMENT_BLOCKED_FOR_AUTONOMY");

        when(cvAuditRepo.findTopByOrderByAuditTimestampDesc()).thenReturn(Optional.of(audit));

        ResponseEntity<?> resp = controller.getCvReliabilityAudit();
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody() instanceof CvReliabilityAuditView);
        CvReliabilityAuditView view = (CvReliabilityAuditView) resp.getBody();
        assertEquals(575, view.folderInferredNegativesCount());
        assertEquals(0, view.humanVerifiedNegativesCount());
        assertEquals(0.35, view.abstentionLowerThreshold());
        assertEquals(0.65, view.abstentionUpperThreshold());
    }

    @Test
    @DisplayName("getEvidenceAuditSummary returns recalculated census and blocked readiness verdict")
    void testGetEvidenceAuditSummary() {
        ResponseEntity<EvidenceAuditSummaryView> resp = controller.getEvidenceAuditSummary();
        assertNotNull(resp.getBody());
        EvidenceAuditSummaryView summary = resp.getBody();
        assertEquals(24, summary.totalObservationsAudited());
        assertEquals(0, summary.evidenceVerifiedPositives());
        assertEquals(0, summary.evidenceVerifiedNegatives());
        assertEquals(16, summary.partiallyVerifiedObservations());
        assertEquals(8, summary.unverifiedObservations());
        assertEquals(0, summary.verifiedCalibratedDepthMeasurements());
        assertEquals(16, summary.unverifiedDepthClaims());
        assertEquals(0, summary.eligibleForClassificationTraining());
        assertEquals("BLOCKED_UNVERIFIED_GROUND_TRUTH", summary.readinessVerdict());
        assertEquals("VERIFIED", summary.flywayIntegrityStatus());
        assertEquals("REPAIRED_AND_VALIDATED", summary.flywayRemediationStatus());
    }

    @Test
    @DisplayName("getFlywayIntegrityStatus exposes V6 checksum synchronization and verified validation")
    void testGetFlywayIntegrityStatus() {
        ResponseEntity<FlywayIntegrityStatusView> resp = controller.getFlywayIntegrityStatus();
        assertNotNull(resp.getBody());
        FlywayIntegrityStatusView status = resp.getBody();
        assertEquals(6, status.flywayVersionChecked());
        assertEquals("V6__ground_truth_and_ml_reliability.sql", status.scriptName());
        assertEquals(16961246, status.appliedDbChecksum());
        assertEquals(16961246, status.resolvedLocalChecksum());
        assertEquals("VERIFIED", status.validationStatus());
        assertEquals("REPAIRED_AND_VALIDATED", status.remediationStatus());
        assertTrue(status.schemaTablesPresent());
    }
}
