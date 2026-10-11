package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Audit record of computer vision inference on citizen-uploaded photos.
 * Preserves model version, prediction, confidence, limitations, and verification flags.
 * AI predictions are strictly assistive signals and never auto-verify a report.
 */
@Entity
@Table(name = "twin_report_image_inference", indexes = {
        @Index(name = "idx_image_inference_report_id", columnList = "report_id"),
        @Index(name = "idx_image_inference_prediction", columnList = "prediction"),
        @Index(name = "idx_image_inference_status", columnList = "inference_status"),
        @Index(name = "idx_image_inference_sha256", columnList = "image_sha256")
})
public class ReportImageInference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(nullable = false, length = 64)
    private String prediction; // VISIBLE_WATERLOGGING, NO_VISIBLE_WATERLOGGING, INDETERMINATE

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "raw_probability")
    private Double rawProbability;

    @Column(name = "inference_status", nullable = false, length = 32)
    private String inferenceStatus = "INFERENCE_COMPLETED"; // INFERENCE_COMPLETED, MODEL_UNAVAILABLE, FAILED

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired = true;

    @Column(name = "image_sha256", length = 64)
    private String imageSha256;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    @Column(name = "model_provenance", length = 128)
    private String modelProvenance = "IRDID_BASELINE_LIGHTROADWATERNET";

    @Column(name = "limitations_json", columnDefinition = "TEXT")
    private String limitationsJson;

    @Column(name = "inference_duration_ms")
    private Double inferenceDurationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ReportImageInference() {}

    public ReportImageInference(Long reportId, String modelVersion, String prediction,
                                Double confidenceScore, Double rawProbability, String inferenceStatus,
                                boolean reviewRequired, String imageSha256, String failureReason,
                                String modelProvenance, String limitationsJson, Double inferenceDurationMs) {
        this.reportId = reportId;
        this.modelVersion = modelVersion;
        this.prediction = prediction;
        this.confidenceScore = confidenceScore;
        this.rawProbability = rawProbability;
        this.inferenceStatus = inferenceStatus != null ? inferenceStatus : "INFERENCE_COMPLETED";
        this.reviewRequired = reviewRequired;
        this.imageSha256 = imageSha256;
        this.failureReason = failureReason;
        this.modelProvenance = modelProvenance;
        this.limitationsJson = limitationsJson;
        this.inferenceDurationMs = inferenceDurationMs;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getPrediction() { return prediction; }
    public void setPrediction(String prediction) { this.prediction = prediction; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Double getRawProbability() { return rawProbability; }
    public void setRawProbability(Double rawProbability) { this.rawProbability = rawProbability; }

    public String getInferenceStatus() { return inferenceStatus; }
    public void setInferenceStatus(String inferenceStatus) { this.inferenceStatus = inferenceStatus; }

    public boolean isReviewRequired() { return reviewRequired; }
    public void setReviewRequired(boolean reviewRequired) { this.reviewRequired = reviewRequired; }

    public String getImageSha256() { return imageSha256; }
    public void setImageSha256(String imageSha256) { this.imageSha256 = imageSha256; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getModelProvenance() { return modelProvenance; }
    public void setModelProvenance(String modelProvenance) { this.modelProvenance = modelProvenance; }

    public String getLimitationsJson() { return limitationsJson; }
    public void setLimitationsJson(String limitationsJson) { this.limitationsJson = limitationsJson; }

    public Double getInferenceDurationMs() { return inferenceDurationMs; }
    public void setInferenceDurationMs(Double inferenceDurationMs) { this.inferenceDurationMs = inferenceDurationMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
