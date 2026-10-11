package org.growforge.jalmarg;

import org.growforge.jalmarg.twin.dto.MlDtos.*;
import org.growforge.jalmarg.twin.model.MlDataQualityReport;
import org.growforge.jalmarg.twin.model.MlDatasetRegistry;
import org.growforge.jalmarg.twin.model.MlTrainingReadiness;
import org.growforge.jalmarg.twin.repo.MlDataQualityReportRepository;
import org.growforge.jalmarg.twin.repo.MlDatasetRegistryRepository;
import org.growforge.jalmarg.twin.repo.MlTrainingReadinessRepository;
import org.growforge.jalmarg.twin.service.VisionInferenceService;
import org.growforge.jalmarg.twin.web.MlMetadataController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MlMetadataControllerTest {

    private MlDatasetRegistryRepository datasetRepo;
    private MlTrainingReadinessRepository readinessRepo;
    private MlDataQualityReportRepository qualityRepo;
    private VisionInferenceService visionService;
    private MlMetadataController controller;

    @BeforeEach
    void setUp() {
        datasetRepo = Mockito.mock(MlDatasetRegistryRepository.class);
        readinessRepo = Mockito.mock(MlTrainingReadinessRepository.class);
        qualityRepo = Mockito.mock(MlDataQualityReportRepository.class);
        visionService = Mockito.mock(VisionInferenceService.class);
        controller = new MlMetadataController(datasetRepo, readinessRepo, qualityRepo, visionService);
    }

    @Test
    @DisplayName("getDatasets returns mapped dataset views")
    void testGetDatasets() {
        MlDatasetRegistry reg = new MlDatasetRegistry();
        reg.setDatasetCode("COP_DEM_GLO30");
        reg.setDatasetName("Copernicus DEM GLO-30 COG");
        reg.setSourceOrganization("ESA");
        reg.setProvenanceType("OBSERVED");
        reg.setValidationStatus("VERIFIED");

        when(datasetRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(reg));

        List<MlDatasetView> views = controller.getDatasets();
        assertEquals(1, views.size());
        assertEquals("COP_DEM_GLO30", views.get(0).datasetCode());
        assertEquals("VERIFIED", views.get(0).validationStatus());
        assertEquals("OBSERVED", views.get(0).provenanceType());
    }

    @Test
    @DisplayName("getLatestReadiness returns readiness view when present")
    void testGetLatestReadinessPresent() {
        MlTrainingReadiness r = new MlTrainingReadiness();
        r.setReadinessGate("BLOCKED_INSUFFICIENT_GROUND_TRUTH");
        r.setUsablePositiveEvents(12);
        r.setUsableNegativeObservations(0);
        r.setMonsoonSeasonsCovered(1);
        r.setClassImbalanceRatio("12:0");
        r.setDepthMeasurementAvailability("INSUFFICIENT (3/30)");
        r.setSummaryNotes("Blocked due to positive/negative label deficit.");

        when(readinessRepo.findTopByOrderByAssessmentTimestampDesc()).thenReturn(Optional.of(r));

        ResponseEntity<MlReadinessView> res = controller.getLatestReadiness();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertEquals("BLOCKED_INSUFFICIENT_GROUND_TRUTH", res.getBody().readinessGate());
        assertEquals(12, res.getBody().usablePositiveEvents());
        assertEquals(0, res.getBody().usableNegativeObservations());
    }

    @Test
    @DisplayName("getLatestReadiness returns 204 No Content when empty")
    void testGetLatestReadinessEmpty() {
        when(readinessRepo.findTopByOrderByAssessmentTimestampDesc()).thenReturn(Optional.empty());
        ResponseEntity<MlReadinessView> res = controller.getLatestReadiness();
        assertEquals(204, res.getStatusCode().value());
    }

    @Test
    @DisplayName("getQualityReports returns quality audit summaries")
    void testGetQualityReports() {
        MlDataQualityReport q = new MlDataQualityReport();
        q.setDatasetCode("OPEN_METEO_ARCHIVE");
        q.setTotalRecordsChecked(744);
        q.setQualityScore(100);
        q.setQualityVerdict("PASSED");

        when(qualityRepo.findAllByOrderByReportTimestampDesc()).thenReturn(List.of(q));

        List<MlQualityReportView> reports = controller.getQualityReports(null);
        assertEquals(1, reports.size());
        assertEquals("OPEN_METEO_ARCHIVE", reports.get(0).datasetCode());
        assertEquals(100, reports.get(0).qualityScore());
        assertEquals("PASSED", reports.get(0).qualityVerdict());
    }

    @Test
    @DisplayName("Phase 25B vision endpoints return metadata maps")
    void testVisionEndpoints() {
        when(visionService.getReadinessAssessment()).thenReturn(Map.of("readiness_gate", "READY_FOR_BASELINE_TRAINING"));
        when(visionService.getEvaluationReport()).thenReturn(Map.of("model_architecture", "LightRoadWaterNet", "release_decision", "HUMAN_REVIEW_ASSIST"));
        when(visionService.getDatasetAuditReport()).thenReturn(Map.of("total_images_discovered", 1049, "unique_sha256_count", 671));

        ResponseEntity<Map<String, Object>> readiness = controller.getVisionReadiness();
        assertEquals(200, readiness.getStatusCode().value());
        assertEquals("READY_FOR_BASELINE_TRAINING", readiness.getBody().get("readiness_gate"));

        ResponseEntity<Map<String, Object>> evaluation = controller.getVisionEvaluation();
        assertEquals(200, evaluation.getStatusCode().value());
        assertEquals("HUMAN_REVIEW_ASSIST", evaluation.getBody().get("release_decision"));

        ResponseEntity<Map<String, Object>> audit = controller.getVisionDatasetAudit();
        assertEquals(200, audit.getStatusCode().value());
        assertEquals(1049, audit.getBody().get("total_images_discovered"));
    }
}
