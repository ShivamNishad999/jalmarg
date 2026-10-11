package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_calibrated_depth_measurements", indexes = {
        @Index(name = "idx_ml_cal_depth_code", columnList = "measurement_code"),
        @Index(name = "idx_ml_cal_depth_status", columnList = "evidence_status"),
        @Index(name = "idx_ml_cal_depth_time", columnList = "measurement_timestamp")
})
public class CalibratedDepthMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measurement_code", nullable = false, unique = true, length = 64)
    private String measurementCode;

    @Column(name = "observation_id", length = 64)
    private String observationId;

    @Column(name = "corridor_name", nullable = false, length = 128)
    private String corridorName;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "measurement_timestamp", nullable = false)
    private Instant measurementTimestamp;

    @Column(name = "measurement_type", nullable = false, length = 64)
    private String measurementType; // CALIBRATED_PHYSICAL_MEASUREMENT, HUMAN_ESTIMATED, PHOTO_DERIVED, SIMULATED

    @Column(name = "measured_depth_m", nullable = false)
    private Double measuredDepthM;

    @Column(name = "measurement_uncertainty_m")
    private Double measurementUncertaintyM;

    @Column(nullable = false, length = 16)
    private String units = "m";

    @Column(name = "instrument_type", length = 64)
    private String instrumentType; // ULTRASONIC_STAGE_GAUGE, CALIBRATED_STAFF_GAUGE, SURVEYORS_LEVEL_ROD

    @Column(name = "instrument_id", length = 64)
    private String instrumentId;

    @Column(name = "calibration_certificate_ref", length = 256)
    private String calibrationCertificateRef;

    @Column(name = "source_document_title", length = 256)
    private String sourceDocumentTitle;

    @Column(name = "source_document_url", length = 512)
    private String sourceDocumentUrl;

    @Column(name = "evidence_status", nullable = false, length = 32)
    private String evidenceStatus = "PENDING_VERIFICATION"; // VERIFIED_CALIBRATED, UNVERIFIED_CLAIM, REJECTED

    @Column(name = "reviewer_username", length = 64)
    private String reviewerUsername;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "audit_notes", columnDefinition = "TEXT")
    private String auditNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CalibratedDepthMeasurement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMeasurementCode() { return measurementCode; }
    public void setMeasurementCode(String measurementCode) { this.measurementCode = measurementCode; }

    public String getObservationId() { return observationId; }
    public void setObservationId(String observationId) { this.observationId = observationId; }

    public String getCorridorName() { return corridorName; }
    public void setCorridorName(String corridorName) { this.corridorName = corridorName; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Instant getMeasurementTimestamp() { return measurementTimestamp; }
    public void setMeasurementTimestamp(Instant measurementTimestamp) { this.measurementTimestamp = measurementTimestamp; }

    public String getMeasurementType() { return measurementType; }
    public void setMeasurementType(String measurementType) { this.measurementType = measurementType; }

    public Double getMeasuredDepthM() { return measuredDepthM; }
    public void setMeasuredDepthM(Double measuredDepthM) { this.measuredDepthM = measuredDepthM; }

    public Double getMeasurementUncertaintyM() { return measurementUncertaintyM; }
    public void setMeasurementUncertaintyM(Double measurementUncertaintyM) { this.measurementUncertaintyM = measurementUncertaintyM; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }

    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }

    public String getCalibrationCertificateRef() { return calibrationCertificateRef; }
    public void setCalibrationCertificateRef(String calibrationCertificateRef) { this.calibrationCertificateRef = calibrationCertificateRef; }

    public String getSourceDocumentTitle() { return sourceDocumentTitle; }
    public void setSourceDocumentTitle(String sourceDocumentTitle) { this.sourceDocumentTitle = sourceDocumentTitle; }

    public String getSourceDocumentUrl() { return sourceDocumentUrl; }
    public void setSourceDocumentUrl(String sourceDocumentUrl) { this.sourceDocumentUrl = sourceDocumentUrl; }

    public String getEvidenceStatus() { return evidenceStatus; }
    public void setEvidenceStatus(String evidenceStatus) { this.evidenceStatus = evidenceStatus; }

    public String getReviewerUsername() { return reviewerUsername; }
    public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getAuditNotes() { return auditNotes; }
    public void setAuditNotes(String auditNotes) { this.auditNotes = auditNotes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
