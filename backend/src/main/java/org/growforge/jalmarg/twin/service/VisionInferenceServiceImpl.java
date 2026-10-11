package org.growforge.jalmarg.twin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.ReportRepository;
import org.growforge.jalmarg.twin.model.ReportImageInference;
import org.growforge.jalmarg.twin.repo.ReportImageInferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Spring Boot Integration Service for IRDID Computer Vision Image Inference.
 * Communicates with the local vision daemon or artifacts, enforcing non-destructive,
 * assistive-only predictions without altering human verification status.
 */
@Service
public class VisionInferenceServiceImpl implements VisionInferenceService {

    private static final Logger log = LoggerFactory.getLogger(VisionInferenceServiceImpl.class);

    private static final String DEFAULT_MODEL_VERSION = "jalmarg-cv-lightroadwaternet-2026.1";
    private static final long MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private final ReportRepository reportRepo;
    private final ReportImageInferenceRepository inferenceRepo;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${jalmarg.vision.enabled:true}")
    private boolean visionEnabled = true;

    @Value("${jalmarg.vision.service-url:http://127.0.0.1:8092/predict}")
    private String visionServiceUrl = "http://127.0.0.1:8092/predict";

    @Value("${jalmarg.vision.metadata-path:ml/artifacts/vision/test_evaluation_report.json}")
    private String metadataPath = "ml/artifacts/vision/test_evaluation_report.json";

    @Value("${jalmarg.vision.readiness-path:ml/artifacts/vision/readiness_assessment.json}")
    private String readinessPath = "ml/artifacts/vision/readiness_assessment.json";

    @Value("${jalmarg.vision.audit-path:ml/artifacts/vision/dataset_audit_report.json}")
    private String auditPath = "ml/artifacts/vision/dataset_audit_report.json";

    public VisionInferenceServiceImpl(
            ReportRepository reportRepo,
            ReportImageInferenceRepository inferenceRepo,
            ObjectMapper objectMapper
    ) {
        this.reportRepo = reportRepo;
        this.inferenceRepo = inferenceRepo;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1500))
                .build();
    }

    public void setVisionEnabled(boolean visionEnabled) {
        this.visionEnabled = visionEnabled;
    }

    public void setVisionServiceUrl(String visionServiceUrl) {
        this.visionServiceUrl = visionServiceUrl;
    }

    public void setMetadataPath(String metadataPath) {
        this.metadataPath = metadataPath;
    }

    public void setReadinessPath(String readinessPath) {
        this.readinessPath = readinessPath;
    }

    public void setAuditPath(String auditPath) {
        this.auditPath = auditPath;
    }

    @Override
    public ReportImageInference processReportImage(Report report, byte[] photoBytes) {
        if (report == null || report.getId() == null) {
            log.warn("Cannot process image inference for unsaved report.");
            return null;
        }

        if (photoBytes == null || photoBytes.length == 0) {
            return null;
        }

        String imageSha256 = computeSha256(photoBytes);

        if (!visionEnabled) {
            log.info("Vision inference is disabled by configuration. Skipping report #{}", report.getId());
            return recordInferenceOutcome(report, DEFAULT_MODEL_VERSION, "UNPROCESSED", null, null,
                    "DISABLED_BY_CONFIG", true, imageSha256, "Vision inference is disabled in configuration.", 0.0);
        }

        if (photoBytes.length > MAX_IMAGE_SIZE_BYTES) {
            log.warn("Image size {} exceeds limit for report #{}", photoBytes.length, report.getId());
            return recordInferenceOutcome(report, DEFAULT_MODEL_VERSION, "INDETERMINATE", null, null,
                    "PAYLOAD_TOO_LARGE", true, imageSha256, "Image size exceeds 10MB limit.", 0.0);
        }

        long t0 = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(visionServiceUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(photoBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            double durationMs = (double) (System.currentTimeMillis() - t0);

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String modelVersion = json.path("modelVersion").asText(DEFAULT_MODEL_VERSION);
                String prediction = json.path("prediction").asText("INDETERMINATE");
                Double confidence = json.hasNonNull("confidence") ? json.path("confidence").asDouble() : null;
                Double rawProb = json.hasNonNull("rawProbability") ? json.path("rawProbability").asDouble() : null;
                String status = json.path("status").asText("INFERENCE_COMPLETED");
                boolean reviewRequired = json.path("reviewRequired").asBoolean(true);
                String limitations = json.path("limitations").toString();

                return recordInferenceOutcome(report, modelVersion, prediction, confidence, rawProb,
                        status, reviewRequired, imageSha256, limitations, durationMs);
            } else {
                log.warn("Vision server returned status {} for report #{}", response.statusCode(), report.getId());
                return recordInferenceOutcome(report, DEFAULT_MODEL_VERSION, "INDETERMINATE", null, null,
                        "SERVER_HTTP_ERROR", true, imageSha256, "HTTP status " + response.statusCode(), durationMs);
            }
        } catch (Exception e) {
            log.warn("Vision service call failed for report #{}: {}. Preserving report workflow.", report.getId(), e.getMessage());
            double durationMs = (double) (System.currentTimeMillis() - t0);
            return recordInferenceOutcome(report, DEFAULT_MODEL_VERSION, "INDETERMINATE", null, null,
                    "MODEL_UNAVAILABLE", true, imageSha256, "Vision service unavailable: " + e.getMessage(), durationMs);
        }
    }

    private ReportImageInference recordInferenceOutcome(
            Report report, String modelVersion, String prediction, Double confidence, Double rawProb,
            String status, boolean reviewRequired, String sha256, String limitationsOrReason, double durationMs
    ) {
        ReportImageInference record = new ReportImageInference(
                report.getId(),
                modelVersion,
                prediction,
                confidence,
                rawProb,
                status,
                reviewRequired,
                sha256,
                status.equals("INFERENCE_COMPLETED") ? null : limitationsOrReason,
                "IRDID_BASELINE_LIGHTROADWATERNET",
                status.equals("INFERENCE_COMPLETED") ? limitationsOrReason : "[]",
                durationMs
        );

        ReportImageInference saved = inferenceRepo.save(record);

        // Update report summary fields without altering verification status
        report.setImagePrediction(prediction);
        report.setImageConfidence(confidence);
        report.setImageInferenceStatus(status);
        reportRepo.save(report);

        log.info("Recorded vision inference for report #{}: prediction={}, confidence={}, status={}",
                report.getId(), prediction, confidence, status);

        return saved;
    }

    @Override
    public Map<String, Object> getEvaluationReport() {
        return readJsonFile(metadataPath);
    }

    @Override
    public Map<String, Object> getReadinessAssessment() {
        return readJsonFile(readinessPath);
    }

    @Override
    public Map<String, Object> getDatasetAuditReport() {
        return readJsonFile(auditPath);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonFile(String relativePath) {
        if (relativePath == null) {
            return Map.of("status", "ARTIFACT_PATH_NULL");
        }
        try {
            Path path = Path.of(relativePath);
            if (!Files.exists(path)) {
                path = Path.of("..", relativePath);
            }
            if (!Files.exists(path)) {
                path = Path.of("ml", relativePath.startsWith("ml/") ? relativePath.substring(3) : relativePath);
            }
            if (!Files.exists(path)) {
                path = Path.of("..", "ml", relativePath.startsWith("ml/") ? relativePath.substring(3) : relativePath);
            }
            if (Files.exists(path)) {
                return objectMapper.readValue(path.toFile(), Map.class);
            }
        } catch (Exception e) {
            log.warn("Could not read vision artifact from {}: {}", relativePath, e.getMessage());
        }
        return Map.of("status", "ARTIFACT_NOT_FOUND", "path", relativePath);
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            return "unknown";
        }
    }
}
