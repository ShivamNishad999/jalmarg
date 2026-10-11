package org.growforge.jalmarg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.ReportRepository;
import org.growforge.jalmarg.twin.model.ReportImageInference;
import org.growforge.jalmarg.twin.repo.ReportImageInferenceRepository;
import org.growforge.jalmarg.twin.service.VisionInferenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VisionInferenceServiceTest {

    private ReportRepository reportRepo;
    private ReportImageInferenceRepository inferenceRepo;
    private ObjectMapper objectMapper;
    private VisionInferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        reportRepo = Mockito.mock(ReportRepository.class);
        inferenceRepo = Mockito.mock(ReportImageInferenceRepository.class);
        objectMapper = new ObjectMapper();
        service = new VisionInferenceServiceImpl(reportRepo, inferenceRepo, objectMapper);
        service.setVisionEnabled(true);
        service.setVisionServiceUrl("http://127.0.0.1:59999/dummy"); // offline port for testing fallback
    }

    @Test
    @DisplayName("processReportImage returns null when report or bytes are empty")
    void testEmptyInputs() {
        assertNull(service.processReportImage(null, new byte[]{1, 2, 3}));

        Report unsaved = new Report();
        assertNull(service.processReportImage(unsaved, new byte[]{1, 2, 3}));

        Report report = new Report();
        report.setId(101L);
        assertNull(service.processReportImage(report, null));
        assertNull(service.processReportImage(report, new byte[0]));
    }

    @Test
    @DisplayName("processReportImage handles oversized images gracefully")
    void testOversizedImage() {
        Report report = new Report();
        report.setId(102L);
        report.setVerificationStatus("UNVERIFIED");

        when(inferenceRepo.save(any(ReportImageInference.class))).thenAnswer(inv -> inv.getArgument(0));

        // 11 MB payload
        byte[] large = new byte[11 * 1024 * 1024];
        ReportImageInference res = service.processReportImage(report, large);

        assertNotNull(res);
        assertEquals("PAYLOAD_TOO_LARGE", res.getInferenceStatus());
        assertEquals("INDETERMINATE", res.getPrediction());
        assertTrue(res.isReviewRequired());
        // Critical: Verification status must remain UNVERIFIED
        assertEquals("UNVERIFIED", report.getVerificationStatus());
    }

    @Test
    @DisplayName("processReportImage handles disabled configuration gracefully")
    void testDisabledConfig() {
        service.setVisionEnabled(false);
        Report report = new Report();
        report.setId(105L);
        report.setVerificationStatus("UNVERIFIED");

        when(inferenceRepo.save(any(ReportImageInference.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportImageInference res = service.processReportImage(report, new byte[]{1, 2, 3});
        assertNotNull(res);
        assertEquals("DISABLED_BY_CONFIG", res.getInferenceStatus());
        assertEquals("UNVERIFIED", report.getVerificationStatus());
    }

    @Test
    @DisplayName("processReportImage handles offline vision service gracefully without throwing")
    void testOfflineServiceFallback() {
        Report report = new Report();
        report.setId(103L);
        report.setVerificationStatus("UNVERIFIED");

        when(inferenceRepo.save(any(ReportImageInference.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] sampleBytes = new byte[]{10, 20, 30, 40};
        ReportImageInference res = service.processReportImage(report, sampleBytes);

        assertNotNull(res);
        assertTrue(res.getInferenceStatus().equals("MODEL_UNAVAILABLE") || res.getInferenceStatus().equals("INFERENCE_COMPLETED"));
        assertTrue(res.isReviewRequired());
        assertEquals("UNVERIFIED", report.getVerificationStatus());
        verify(reportRepo, atLeastOnce()).save(report);
    }

    @Test
    @DisplayName("Metadata reading methods handle existing or missing files gracefully")
    void testMetadataReading() {
        Map<String, Object> eval = service.getEvaluationReport();
        assertNotNull(eval);

        Map<String, Object> readiness = service.getReadinessAssessment();
        assertNotNull(readiness);

        Map<String, Object> audit = service.getDatasetAuditReport();
        assertNotNull(audit);
    }
}
