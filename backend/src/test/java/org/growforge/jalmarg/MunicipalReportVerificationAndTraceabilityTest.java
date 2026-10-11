package org.growforge.jalmarg;

import org.growforge.jalmarg.model.Enums.DepthLevel;
import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.growforge.jalmarg.repo.ReportRepository;
import org.growforge.jalmarg.service.AiDepthService;
import org.growforge.jalmarg.service.HotspotService;
import org.growforge.jalmarg.service.ReportService;
import org.growforge.jalmarg.service.StorageService;
import org.growforge.jalmarg.twin.dto.TwinDtos.MunicipalIncidentRequest;
import org.growforge.jalmarg.twin.model.AuditEvent;
import org.growforge.jalmarg.twin.model.MunicipalIncident;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.model.ReportObservation;
import org.growforge.jalmarg.twin.repo.*;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.growforge.jalmarg.twin.service.MunicipalIncidentService;
import org.growforge.jalmarg.twin.service.MunicipalReportReviewService;
import org.growforge.jalmarg.twin.service.MunicipalReportReviewService.DetailedReportReviewView;
import org.growforge.jalmarg.twin.service.MunicipalReportReviewService.ReportReviewDecisionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MunicipalReportVerificationAndTraceabilityTest {

    private ReportRepository reportRepo;
    private ReportObservationRepository observationRepo;
    private MunicipalIncidentRepository incidentRepo;
    private ResponseResourceRepository resourceRepo;
    private MunicipalZoneRepository zoneRepo;
    private AuditEventRepository auditRepo;
    private MunicipalUserRepository userRepo;
    private HotspotRepository hotspotRepo;
    private MunicipalAuthService authService;
    private MunicipalIncidentService incidentService;
    private MunicipalReportReviewService reviewService;
    private HotspotService hotspotService;
    private StorageService storage;
    private AiDepthService ai;
    private ReportService reportService;

    @BeforeEach
    void setUp() throws IOException {
        reportRepo = Mockito.mock(ReportRepository.class);
        observationRepo = Mockito.mock(ReportObservationRepository.class);
        incidentRepo = Mockito.mock(MunicipalIncidentRepository.class);
        resourceRepo = Mockito.mock(ResponseResourceRepository.class);
        zoneRepo = Mockito.mock(MunicipalZoneRepository.class);
        auditRepo = Mockito.mock(AuditEventRepository.class);
        userRepo = Mockito.mock(MunicipalUserRepository.class);
        hotspotRepo = Mockito.mock(HotspotRepository.class);

        when(hotspotRepo.findAll()).thenReturn(List.of());
        when(hotspotRepo.save(any(Hotspot.class))).thenAnswer(i -> {
            Hotspot h = i.getArgument(0);
            h.setId(1L);
            return h;
        });

        authService = new MunicipalAuthService(userRepo, auditRepo, "test-admin-key-2026");
        hotspotService = new HotspotService(hotspotRepo, reportRepo);
        storage = new StorageService(null, "", "target/test-uploads");
        ai = new AiDepthService(null, false, "apac.amazon.nova-lite-v1:0");

        incidentService = new MunicipalIncidentService(incidentRepo, resourceRepo, zoneRepo, auditRepo);

        reviewService = new MunicipalReportReviewService(
                reportRepo, observationRepo, incidentService, auditRepo, authService
        );

        reportService = new ReportService(
                reportRepo, hotspotService, storage, ai, 6, false, observationRepo
        );
    }

    @Test
    @DisplayName("Submitting a citizen report creates an immutable observation record for evidence traceability")
    void testCitizenReportRecordsObservation() throws IOException {
        Report report = new Report();
        report.setId(501L);
        report.setLatitude(28.6289);
        report.setLongitude(77.2065);
        report.setLocationName("Minto Bridge");
        report.setCitizenDepthLevel(DepthLevel.KNEE);
        report.setDepthFt(1.5);
        report.setSeverity(Severity.HIGH);
        report.setStatus(ReportStatus.ACTIVE);
        report.setCreatedAt(Instant.now());
        report.setLastConfirmedAt(Instant.now());

        when(reportRepo.findByStatusOrderByCreatedAtDesc(ReportStatus.ACTIVE)).thenReturn(List.of());
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> {
            Report r = i.getArgument(0);
            r.setId(501L);
            return r;
        });

        Report saved = reportService.create(new ReportService.NewReport(
                28.6289, 77.2065, "Minto Bridge", "Connaught Place",
                DepthLevel.KNEE, "Traffic stranded under bridge", null, null, "10.0.0.1"
        ));

        assertNotNull(saved);
        assertEquals(501L, saved.getId());
        assertEquals("UNVERIFIED", saved.getVerificationStatus());

        // Verify observation log was captured
        ArgumentCaptor<ReportObservation> obsCaptor = ArgumentCaptor.forClass(ReportObservation.class);
        verify(observationRepo, times(1)).save(obsCaptor.capture());
        ReportObservation obs = obsCaptor.getValue();
        assertEquals(501L, obs.getReportId());
        assertEquals(DepthLevel.KNEE, obs.getCitizenDepthLevel());
        assertEquals("10.0.0.1", obs.getClientIp());
        assertEquals("Traffic stranded under bridge", obs.getNote());
    }

    @Test
    @DisplayName("Merging a duplicate report updates confirmation, preserves observation, and sets isDuplicateMerged flag")
    void testDuplicateReportMergePreservesEvidence() throws IOException {
        Report existing = new Report();
        existing.setId(501L);
        existing.setLatitude(28.6289);
        existing.setLongitude(77.2065);
        existing.setLocationName("Minto Bridge");
        existing.setCitizenDepthLevel(DepthLevel.ANKLE);
        existing.setDepthFt(0.35);
        existing.setSeverity(Severity.LOW);
        existing.setConfirmations(1);
        existing.setStatus(ReportStatus.ACTIVE);
        existing.setCreatedAt(Instant.now());
        existing.setLastConfirmedAt(Instant.now());

        when(reportRepo.findByStatusOrderByCreatedAtDesc(ReportStatus.ACTIVE)).thenReturn(List.of(existing));
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));

        // Submit another report at same location with deeper water
        Report merged = reportService.create(new ReportService.NewReport(
                28.6289, 77.2065, "Minto Bridge", "Connaught Place",
                DepthLevel.WAIST, "Water rising fast!", null, null, "10.0.0.2"
        ));

        assertNotNull(merged);
        assertEquals(501L, merged.getId());
        assertEquals(2, merged.getConfirmations());
        assertTrue(merged.isDuplicateMerged());
        assertEquals(3.0, merged.getDepthFt());
        assertEquals(DepthLevel.WAIST, merged.getCitizenDepthLevel());

        // Verify that the duplicate submission was logged into observations
        ArgumentCaptor<ReportObservation> obsCaptor = ArgumentCaptor.forClass(ReportObservation.class);
        verify(observationRepo, times(1)).save(obsCaptor.capture());
        ReportObservation obs = obsCaptor.getValue();
        assertEquals(501L, obs.getReportId());
        assertEquals(DepthLevel.WAIST, obs.getCitizenDepthLevel());
        assertEquals("10.0.0.2", obs.getClientIp());
        assertEquals("Water rising fast!", obs.getNote());
    }

    @Test
    @DisplayName("Municipal operator verifying a report transitions verification status and records audit event")
    void testMunicipalReportVerification() {
        Report report = new Report();
        report.setId(501L);
        report.setLatitude(28.6289);
        report.setLongitude(77.2065);
        report.setLocationName("Minto Bridge");
        report.setVerificationStatus("UNVERIFIED");
        report.setDepthFt(3.0);
        report.setSeverity(Severity.SEVERE);
        report.setStatus(ReportStatus.ACTIVE);
        report.setCreatedAt(Instant.now());
        report.setLastConfirmedAt(Instant.now());

        when(reportRepo.findById(501L)).thenReturn(Optional.of(report));
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(observationRepo.findByReportIdOrderBySubmittedAtAsc(501L)).thenReturn(List.of());

        MunicipalPrincipal operator = new MunicipalPrincipal(1L, "officer_sharma", "Sharma", "PWD Drainage", Role.MUNICIPAL_OPERATOR, true);

        ReportReviewDecisionRequest req = new ReportReviewDecisionRequest(
                "VERIFIED", "Confirmed 3.0ft water by field patrol team", false, null, null
        );

        DetailedReportReviewView review = reviewService.reviewReport(501L, req, operator, "127.0.0.1");

        assertNotNull(review);
        assertEquals("VERIFIED", review.verificationStatus());
        assertEquals("officer_sharma", review.verifiedBy());
        assertEquals("Confirmed 3.0ft water by field patrol team", review.verificationNotes());
        assertNotNull(review.verifiedAt());

        // Verify audit event was logged
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepo, atLeastOnce()).save(auditCaptor.capture());
        AuditEvent event = auditCaptor.getValue();
        assertEquals("officer_sharma", event.getActorId());
        assertEquals("REPORT_VERIFY", event.getActionType());
        assertEquals("Report", event.getTargetEntity());
        assertEquals("501", event.getTargetEntityId());
    }

    @Test
    @DisplayName("Promoting a verified report creates a linked Municipal Incident")
    void testPromoteVerifiedReportToIncident() {
        Report report = new Report();
        report.setId(501L);
        report.setLatitude(28.6289);
        report.setLongitude(77.2065);
        report.setLocationName("Minto Bridge Underpass");
        report.setVerificationStatus("UNVERIFIED");
        report.setDepthFt(3.0);
        report.setSeverity(Severity.SEVERE);
        report.setStatus(ReportStatus.ACTIVE);
        report.setCreatedAt(Instant.now());
        report.setLastConfirmedAt(Instant.now());

        when(reportRepo.findById(501L)).thenReturn(Optional.of(report));
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(observationRepo.findByReportIdOrderBySubmittedAtAsc(501L)).thenReturn(List.of());

        when(incidentRepo.count()).thenReturn(10L);
        when(incidentRepo.save(any(MunicipalIncident.class))).thenAnswer(i -> {
            MunicipalIncident inc = i.getArgument(0);
            inc.setId(901L);
            return inc;
        });

        MunicipalPrincipal operator = new MunicipalPrincipal(1L, "officer_sharma", "Sharma", "PWD Drainage", Role.MUNICIPAL_OPERATOR, true);

        ReportReviewDecisionRequest req = new ReportReviewDecisionRequest(
                "VERIFIED", "Confirmed extreme inundation, emergency pumping required", true, "UNDERPASS_INUNDATION", null
        );

        DetailedReportReviewView review = reviewService.reviewReport(501L, req, operator, "127.0.0.1");

        assertNotNull(review);
        assertEquals("VERIFIED", review.verificationStatus());
        verify(incidentRepo, times(1)).save(any(MunicipalIncident.class));
    }

    @Test
    @DisplayName("Municipal operator rejecting a report transitions status to CLEARED, sets REJECTED, and records audit event")
    void testMunicipalReportRejectionClearsStatusAndAudits() {
        Report report = new Report();
        report.setId(502L);
        report.setLatitude(28.6289);
        report.setLongitude(77.2065);
        report.setLocationName("Minto Bridge");
        report.setVerificationStatus("UNVERIFIED");
        report.setDepthFt(1.5);
        report.setSeverity(Severity.HIGH);
        report.setStatus(ReportStatus.ACTIVE);
        report.setCreatedAt(Instant.now());
        report.setLastConfirmedAt(Instant.now());

        when(reportRepo.findById(502L)).thenReturn(Optional.of(report));
        when(reportRepo.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(observationRepo.findByReportIdOrderBySubmittedAtAsc(502L)).thenReturn(List.of());

        MunicipalPrincipal operator = new MunicipalPrincipal(1L, "officer_sharma", "Sharma", "PWD Drainage", Role.MUNICIPAL_OPERATOR, true);

        ReportReviewDecisionRequest req = new ReportReviewDecisionRequest(
                "REJECTED", "Inspected on site - road is completely dry. False alarm.", false, null, null
        );

        DetailedReportReviewView review = reviewService.reviewReport(502L, req, operator, "127.0.0.1");

        assertNotNull(review);
        assertEquals("REJECTED", review.verificationStatus());
        assertEquals("officer_sharma", review.verifiedBy());
        assertEquals(ReportStatus.CLEARED, report.getStatus());

        // Verify audit event was logged
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRepo, atLeastOnce()).save(auditCaptor.capture());
        AuditEvent event = auditCaptor.getValue();
        assertEquals("officer_sharma", event.getActorId());
        assertEquals("REPORT_REJECT", event.getActionType());
        assertEquals("Report", event.getTargetEntity());
        assertEquals("502", event.getTargetEntityId());
    }
}
