package org.growforge.jalmarg;

import org.growforge.jalmarg.twin.dto.GroundTruthDtos.*;
import org.growforge.jalmarg.twin.model.CalibratedDepthMeasurement;
import org.growforge.jalmarg.twin.model.MunicipalDryRoadInspection;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.model.RainfallCoverageManifest;
import org.growforge.jalmarg.twin.model.VerificationAuditLog;
import org.growforge.jalmarg.twin.repo.*;
import org.growforge.jalmarg.twin.service.GroundTruthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.growforge.jalmarg.twin.web.GroundTruthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Phase25EbRainfallAndInspectionTest {

    private GroundTruthObservationRepository observationRepo;
    private HistoricalSourceRegistryRepository sourceRepo;
    private VerificationAuditLogRepository auditLogRepo;
    private CvReliabilityAuditRepository cvAuditRepo;
    private RainfallCoverageManifestRepository rainfallRepo;
    private MunicipalDryRoadInspectionRepository dryRoadInspectionRepo;
    private CalibratedDepthMeasurementRepository depthMeasurementRepo;
    private MunicipalAuthService authService;
    private GroundTruthService groundTruthService;
    private GroundTruthController controller;

    @BeforeEach
    void setUp() {
        observationRepo = mock(GroundTruthObservationRepository.class);
        sourceRepo = mock(HistoricalSourceRegistryRepository.class);
        auditLogRepo = mock(VerificationAuditLogRepository.class);
        cvAuditRepo = mock(CvReliabilityAuditRepository.class);
        rainfallRepo = mock(RainfallCoverageManifestRepository.class);
        dryRoadInspectionRepo = mock(MunicipalDryRoadInspectionRepository.class);
        depthMeasurementRepo = mock(CalibratedDepthMeasurementRepository.class);

        MunicipalUserRepository userRepo = mock(MunicipalUserRepository.class);
        AuditEventRepository auditRepo = mock(AuditEventRepository.class);
        authService = new MunicipalAuthService(userRepo, auditRepo, "test-admin-key-2026");

        groundTruthService = new GroundTruthService(
                observationRepo, sourceRepo, auditLogRepo, cvAuditRepo,
                rainfallRepo, dryRoadInspectionRepo, depthMeasurementRepo
        );
        controller = new GroundTruthController(groundTruthService, authService);
    }

    @Test
    @DisplayName("getRainfallCoverage returns authentic multi-period coverage records")
    void testRainfallCoverageReturnsAuthenticPeriods() {
        RainfallCoverageManifest p1 = new RainfallCoverageManifest();
        p1.setId(1L);
        p1.setPeriodCode("2023_MONSOON_JUL");
        p1.setPeriodName("July 2023 Historical Monsoon Deluge");
        p1.setStartDate(LocalDate.of(2023, 7, 1));
        p1.setEndDate(LocalDate.of(2023, 7, 31));
        p1.setCorridorsSampled(11);
        p1.setRecordsCount(8184);
        p1.setSha256Checksum("3806deaf6d68ea6c7bbf6960c1d6835a7065912301fa2d0392f58e401fa95ab9");
        p1.setAcquisitionStatus("ACQUIRED_AND_VALIDATED");
        p1.setQualityStatus("PASSED_QUALITY_CHECKS");

        when(rainfallRepo.findAllByOrderByStartDateAsc()).thenReturn(List.of(p1));

        List<RainfallCoverageView> views = controller.getRainfallCoverage();
        assertNotNull(views);
        assertFalse(views.isEmpty());
        RainfallCoverageView v = views.get(0);
        assertEquals("2023_MONSOON_JUL", v.periodCode());
        assertEquals("OPEN_METEO_ERA5_LAND", v.sourceDataset());
        assertEquals(8184, v.recordsCount());
        assertEquals(11, v.corridorsSampled());
        assertEquals("PASSED_QUALITY_CHECKS", v.qualityStatus());
    }

    @Test
    @DisplayName("getRainfallSummary returns authentic source evaluations and counts")
    void testRainfallSummaryReturnsSources() {
        ResponseEntity<Map<String, Object>> resp = controller.getRainfallSummary();
        assertNotNull(resp.getBody());
        Map<String, Object> summary = resp.getBody();
        assertTrue(summary.containsKey("total_hourly_records_acquired") || summary.containsKey("target_periods_count"));
    }

    @Test
    @DisplayName("recordDryRoadInspection creates pending inspection with audit trail")
    void testDryRoadInspectionLifecycle() {
        MunicipalPrincipal principal = new MunicipalPrincipal(
                1L, "inspector_sharma", "Amit Sharma", "PWD_DELHI", Role.MUNICIPAL_OPERATOR, true
        );

        DryRoadInspectionRequest req = new DryRoadInspectionRequest(
                28.6347, 77.2263, "Minto Bridge Underpass", "Connaught Place",
                Instant.now().minusSeconds(1800), 10.0, "DRY", false, 0.0,
                "https://photos.jalmarg.internal/insp1.jpg", "INSP-FILE-001.pdf",
                "Inspected pump station; zero standing water."
        );

        when(dryRoadInspectionRepo.save(any(MunicipalDryRoadInspection.class))).thenAnswer(inv -> {
            MunicipalDryRoadInspection saved = inv.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        DryRoadInspectionView view = groundTruthService.recordDryRoadInspection(req, principal, "127.0.0.1");
        assertNotNull(view);
        assertEquals("PENDING_REVIEW", view.reviewStatus());
        assertFalse(view.waterPresent());
        assertEquals("DRY", view.roadCondition());
        assertNotNull(view.inspectionCode());
        assertTrue(view.inspectionCode().startsWith("INSP-DRY-"));

        verify(auditLogRepo, times(1)).save(any(VerificationAuditLog.class));

        // Test review approval to VERIFIED_NEGATIVE
        MunicipalDryRoadInspection existing = new MunicipalDryRoadInspection();
        existing.setId(101L);
        existing.setInspectionCode(view.inspectionCode());
        existing.setReviewStatus("PENDING_REVIEW");

        when(dryRoadInspectionRepo.findById(101L)).thenReturn(Optional.of(existing));
        when(dryRoadInspectionRepo.save(existing)).thenReturn(existing);

        DryRoadInspectionDecisionRequest decReq = new DryRoadInspectionDecisionRequest(
                "VERIFY", "Supervisory officer confirmed dry road photo", "Approved for negative ground truth"
        );

        DryRoadInspectionView reviewed = groundTruthService.reviewDryRoadInspection(101L, decReq, principal, "127.0.0.1");
        assertEquals("VERIFIED_NEGATIVE", reviewed.reviewStatus());
        assertEquals("inspector_sharma", reviewed.reviewedBy());
    }

    @Test
    @DisplayName("reviewDryRoadInspection rejects self-approval by non-admin operator")
    void testDryRoadInspectionRejectsSelfApprovalByNonAdmin() {
        MunicipalPrincipal inspector = new MunicipalPrincipal(
                1L, "inspector_sharma", "Amit Sharma", "PWD_DELHI", Role.MUNICIPAL_OPERATOR, true
        );

        MunicipalDryRoadInspection existing = new MunicipalDryRoadInspection();
        existing.setId(102L);
        existing.setInspectionCode("INSP-DRY-SELF001");
        existing.setInspectorUsername("inspector_sharma");
        existing.setReviewStatus("PENDING_REVIEW");

        when(dryRoadInspectionRepo.findById(102L)).thenReturn(Optional.of(existing));

        DryRoadInspectionDecisionRequest decReq = new DryRoadInspectionDecisionRequest(
                "VERIFY", "Attempted self-approval", "Trying to verify own inspection"
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                groundTruthService.reviewDryRoadInspection(102L, decReq, inspector, "127.0.0.1")
        );
        assertTrue(ex.getReason().contains("Separation of duties violation"));
    }

    @Test
    @DisplayName("recordCalibratedDepthMeasurement creates pending measurement and review updates evidence status")
    void testCalibratedDepthMeasurementLifecycle() {
        MunicipalPrincipal principal = new MunicipalPrincipal(
                2L, "hydrologist_kapoor", "Dr. Kapoor", "CWC_DELHI", Role.MUNICIPAL_OPERATOR, true
        );

        CalibratedDepthMeasurementRequest req = new CalibratedDepthMeasurementRequest(
                "HIST-2023-DEL-01", "Connaught Place - Paharganj",
                28.6347, 77.2263, Instant.now(), "CALIBRATED_PHYSICAL_MEASUREMENT",
                1.25, 0.03, "m", "ULTRASONIC_STAGE_GAUGE", "GAUGE-US-MB-01",
                "CERT-2023-CAL-CWC-0941", "DTP Monsoon Sitrep 2023/07/09",
                "https://traffic.delhipolice.gov.in/sitrep/20230709.pdf"
        );

        when(depthMeasurementRepo.save(any(CalibratedDepthMeasurement.class))).thenAnswer(inv -> {
            CalibratedDepthMeasurement saved = inv.getArgument(0);
            saved.setId(201L);
            return saved;
        });

        CalibratedDepthMeasurementView view = groundTruthService.recordCalibratedDepthMeasurement(req, principal, "127.0.0.1");
        assertNotNull(view);
        assertEquals("PENDING_VERIFICATION", view.evidenceStatus());
        assertEquals(1.25, view.measuredDepthM());
        assertEquals("ULTRASONIC_STAGE_GAUGE", view.instrumentType());
        assertTrue(view.measurementCode().startsWith("DEPTH-CAL-"));

        verify(auditLogRepo, times(1)).save(any(VerificationAuditLog.class));

        // Test review approval
        CalibratedDepthMeasurement existing = new CalibratedDepthMeasurement();
        existing.setId(201L);
        existing.setMeasurementCode(view.measurementCode());
        existing.setEvidenceStatus("PENDING_VERIFICATION");

        when(depthMeasurementRepo.findById(201L)).thenReturn(Optional.of(existing));
        when(depthMeasurementRepo.save(existing)).thenReturn(existing);

        CalibratedDepthDecisionRequest decReq = new CalibratedDepthDecisionRequest(
                "VERIFY", "Gauge calibration log verified against CWC certificate", "Certified physical measurement"
        );

        CalibratedDepthMeasurementView reviewed = groundTruthService.reviewCalibratedDepthMeasurement(201L, decReq, principal, "127.0.0.1");
        assertEquals("VERIFIED_CALIBRATED", reviewed.evidenceStatus());
        assertEquals("hydrologist_kapoor", reviewed.reviewerUsername());
    }

    @Test
    @DisplayName("recordDryRoadInspection rejects out-of-bounds coordinates")
    void testDryRoadInspectionRejectsOutOfBounds() {
        MunicipalPrincipal principal = new MunicipalPrincipal(
                1L, "inspector_sharma", "Amit Sharma", "PWD_DELHI", Role.MUNICIPAL_OPERATOR, true
        );

        DryRoadInspectionRequest outOfBounds = new DryRoadInspectionRequest(
                12.9716, 77.5946, "Bengaluru Road", "MG Road",
                Instant.now(), 10.0, "DRY", false, 0.0, null, null, null
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                groundTruthService.recordDryRoadInspection(outOfBounds, principal, "127.0.0.1")
        );
        assertTrue(ex.getReason().contains("outside Delhi NCR bounds"));
    }
}
