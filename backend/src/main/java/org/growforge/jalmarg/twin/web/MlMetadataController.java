package org.growforge.jalmarg.twin.web;

import org.growforge.jalmarg.twin.dto.MlDtos.*;
import org.growforge.jalmarg.twin.repo.MlDataQualityReportRepository;
import org.growforge.jalmarg.twin.repo.MlDatasetRegistryRepository;
import org.growforge.jalmarg.twin.repo.MlTrainingReadinessRepository;
import org.growforge.jalmarg.twin.service.VisionInferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ml")
public class MlMetadataController {

    private final MlDatasetRegistryRepository datasetRepo;
    private final MlTrainingReadinessRepository readinessRepo;
    private final MlDataQualityReportRepository qualityRepo;
    private final VisionInferenceService visionService;

    public MlMetadataController(
            MlDatasetRegistryRepository datasetRepo,
            MlTrainingReadinessRepository readinessRepo,
            MlDataQualityReportRepository qualityRepo,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            VisionInferenceService visionService
    ) {
        this.datasetRepo = datasetRepo;
        this.readinessRepo = readinessRepo;
        this.qualityRepo = qualityRepo;
        this.visionService = visionService;
    }

    @GetMapping("/datasets")
    public List<MlDatasetView> getDatasets() {
        return datasetRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(MlDatasetView::of)
                .toList();
    }

    @GetMapping("/readiness")
    public ResponseEntity<MlReadinessView> getLatestReadiness() {
        return readinessRepo.findTopByOrderByAssessmentTimestampDesc()
                .map(MlReadinessView::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/quality-reports")
    public List<MlQualityReportView> getQualityReports(@RequestParam(required = false) String datasetCode) {
        if (datasetCode != null && !datasetCode.isBlank()) {
            return qualityRepo.findByDatasetCodeOrderByReportTimestampDesc(datasetCode).stream()
                    .map(MlQualityReportView::of)
                    .toList();
        }
        return qualityRepo.findAllByOrderByReportTimestampDesc().stream()
                .map(MlQualityReportView::of)
                .toList();
    }

    // ==============================================================================
    // Phase 25B: IRDID Computer Vision Metadata Endpoints (Separate from Climate ML)
    // ==============================================================================

    @GetMapping("/vision/readiness")
    public ResponseEntity<Map<String, Object>> getVisionReadiness() {
        if (visionService == null) {
            return ResponseEntity.ok(Map.of("status", "SERVICE_NOT_INITIALIZED"));
        }
        return ResponseEntity.ok(visionService.getReadinessAssessment());
    }

    @GetMapping("/vision/evaluation")
    public ResponseEntity<Map<String, Object>> getVisionEvaluation() {
        if (visionService == null) {
            return ResponseEntity.ok(Map.of("status", "SERVICE_NOT_INITIALIZED"));
        }
        return ResponseEntity.ok(visionService.getEvaluationReport());
    }

    @GetMapping("/vision/dataset-audit")
    public ResponseEntity<Map<String, Object>> getVisionDatasetAudit() {
        if (visionService == null) {
            return ResponseEntity.ok(Map.of("status", "SERVICE_NOT_INITIALIZED"));
        }
        return ResponseEntity.ok(visionService.getDatasetAuditReport());
    }
}
