package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_municipal_dry_road_inspections", indexes = {
        @Index(name = "idx_ml_dry_insp_code", columnList = "inspection_code"),
        @Index(name = "idx_ml_dry_insp_status", columnList = "review_status"),
        @Index(name = "idx_ml_dry_insp_time", columnList = "inspection_timestamp")
})
public class MunicipalDryRoadInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inspection_code", nullable = false, unique = true, length = 64)
    private String inspectionCode;

    @Column(name = "inspector_username", nullable = false, length = 64)
    private String inspectorUsername;

    @Column(name = "inspector_name", nullable = false, length = 128)
    private String inspectorName;

    @Column(nullable = false, length = 128)
    private String department;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "location_description", nullable = false, length = 512)
    private String locationDescription;

    @Column(name = "corridor_name", length = 128)
    private String corridorName;

    @Column(name = "inspection_timestamp", nullable = false)
    private Instant inspectionTimestamp;

    @Column(name = "gps_accuracy_m")
    private Double gpsAccuracyM;

    @Column(name = "road_condition", nullable = false, length = 64)
    private String roadCondition = "DRY";

    @Column(name = "water_present", nullable = false)
    private Boolean waterPresent = false;

    @Column(name = "standing_water_depth_cm")
    private Double standingWaterDepthCm = 0.0;

    @Column(name = "photo_evidence_url", length = 512)
    private String photoEvidenceUrl;

    @Column(name = "evidence_file_reference", length = 512)
    private String evidenceFileReference;

    @Column(name = "inspector_notes", columnDefinition = "TEXT")
    private String inspectorNotes;

    @Column(name = "review_status", nullable = false, length = 32)
    private String reviewStatus = "PENDING_REVIEW"; // PENDING_REVIEW, VERIFIED_NEGATIVE, REJECTED

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MunicipalDryRoadInspection() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInspectionCode() { return inspectionCode; }
    public void setInspectionCode(String inspectionCode) { this.inspectionCode = inspectionCode; }

    public String getInspectorUsername() { return inspectorUsername; }
    public void setInspectorUsername(String inspectorUsername) { this.inspectorUsername = inspectorUsername; }

    public String getInspectorName() { return inspectorName; }
    public void setInspectorName(String inspectorName) { this.inspectorName = inspectorName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }

    public String getCorridorName() { return corridorName; }
    public void setCorridorName(String corridorName) { this.corridorName = corridorName; }

    public Instant getInspectionTimestamp() { return inspectionTimestamp; }
    public void setInspectionTimestamp(Instant inspectionTimestamp) { this.inspectionTimestamp = inspectionTimestamp; }

    public Double getGpsAccuracyM() { return gpsAccuracyM; }
    public void setGpsAccuracyM(Double gpsAccuracyM) { this.gpsAccuracyM = gpsAccuracyM; }

    public String getRoadCondition() { return roadCondition; }
    public void setRoadCondition(String roadCondition) { this.roadCondition = roadCondition; }

    public Boolean getWaterPresent() { return waterPresent; }
    public void setWaterPresent(Boolean waterPresent) { this.waterPresent = waterPresent; }

    public Double getStandingWaterDepthCm() { return standingWaterDepthCm; }
    public void setStandingWaterDepthCm(Double standingWaterDepthCm) { this.standingWaterDepthCm = standingWaterDepthCm; }

    public String getPhotoEvidenceUrl() { return photoEvidenceUrl; }
    public void setPhotoEvidenceUrl(String photoEvidenceUrl) { this.photoEvidenceUrl = photoEvidenceUrl; }

    public String getEvidenceFileReference() { return evidenceFileReference; }
    public void setEvidenceFileReference(String evidenceFileReference) { this.evidenceFileReference = evidenceFileReference; }

    public String getInspectorNotes() { return inspectorNotes; }
    public void setInspectorNotes(String inspectorNotes) { this.inspectorNotes = inspectorNotes; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
