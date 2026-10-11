package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_ground_truth_observations", indexes = {
        @Index(name = "idx_ml_gt_obs_id", columnList = "observation_id"),
        @Index(name = "idx_ml_gt_obs_status", columnList = "waterlogging_status"),
        @Index(name = "idx_ml_gt_obs_vstatus", columnList = "verification_status"),
        @Index(name = "idx_ml_gt_obs_queue", columnList = "review_queue_status")
})
public class GroundTruthObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "observation_id", nullable = false, unique = true, length = 64)
    private String observationId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "location_description", nullable = false, length = 512)
    private String locationDescription;

    @Column(name = "observation_timestamp", nullable = false)
    private Instant observationTimestamp;

    @Column(name = "observation_timezone", nullable = false, length = 32)
    private String observationTimezone = "Asia/Kolkata";

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "event_start_time")
    private Instant eventStartTime;

    @Column(name = "event_end_time")
    private Instant eventEndTime;

    @Column(name = "publication_timestamp")
    private Instant publicationTimestamp;

    @Column(name = "waterlogging_status", nullable = false, length = 32)
    private String waterloggingStatus;

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "verification_authority", length = 128)
    private String verificationAuthority;

    @Column(name = "verified_by_user", length = 64)
    private String verifiedByUser;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "source_organization", nullable = false, length = 128)
    private String sourceOrganization;

    @Column(name = "source_reference", nullable = false, length = 512)
    private String sourceReference;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "evidence_type", nullable = false, length = 64)
    private String evidenceType;

    @Column(name = "evidence_file_reference", length = 512)
    private String evidenceFileReference;

    @Column(name = "license_code", nullable = false, length = 64)
    private String licenseCode = "GOI_OPEN_DATA";

    @Column(name = "permitted_usage", nullable = false, length = 128)
    private String permittedUsage = "RESEARCH_AND_MUNICIPAL_OPERATIONS";

    @Column(name = "spatial_accuracy_m", nullable = false)
    private Double spatialAccuracyM = 50.0;

    @Column(name = "is_approximate_location", nullable = false)
    private Boolean isApproximateLocation = false;

    @Column(name = "temporal_accuracy_minutes", nullable = false)
    private Integer temporalAccuracyMinutes = 60;

    @Column(name = "measured_water_depth_m")
    private Double measuredWaterDepthM;

    @Column(name = "depth_measurement_method", length = 64)
    private String depthMeasurementMethod;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @Column(name = "provenance_classification", nullable = false, length = 64)
    private String provenanceClassification;

    @Column(name = "alignment_status", nullable = false, length = 32)
    private String alignmentStatus = "PENDING";

    @Column(name = "rainfall_1h_mm")
    private Double rainfall1hMm;

    @Column(name = "rainfall_3h_mm")
    private Double rainfall3hMm;

    @Column(name = "rainfall_6h_mm")
    private Double rainfall6hMm;

    @Column(name = "rainfall_24h_mm")
    private Double rainfall24hMm;

    @Column(name = "antecedent_rainfall_index")
    private Double antecedentRainfallIndex;

    @Column(name = "elevation_m")
    private Double elevationM;

    @Column(name = "slope_degrees")
    private Double slopeDegrees;

    @Column(name = "relative_depression_m")
    private Double relativeDepressionM;

    @Column(name = "nearest_drain_distance_m")
    private Double nearestDrainDistanceM;

    @Column(name = "corridor_name", length = 128)
    private String corridorName;

    @Column(name = "related_report_id")
    private Long relatedReportId;

    @Column(name = "related_incident_id")
    private Long relatedIncidentId;

    @Column(name = "review_queue_status", nullable = false, length = 32)
    private String reviewQueueStatus = "RESOLVED";

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public GroundTruthObservation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getObservationId() { return observationId; }
    public void setObservationId(String observationId) { this.observationId = observationId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }

    public Instant getObservationTimestamp() { return observationTimestamp; }
    public void setObservationTimestamp(Instant observationTimestamp) { this.observationTimestamp = observationTimestamp; }

    public String getObservationTimezone() { return observationTimezone; }
    public void setObservationTimezone(String observationTimezone) { this.observationTimezone = observationTimezone; }

    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }

    public Instant getEventStartTime() { return eventStartTime; }
    public void setEventStartTime(Instant eventStartTime) { this.eventStartTime = eventStartTime; }

    public Instant getEventEndTime() { return eventEndTime; }
    public void setEventEndTime(Instant eventEndTime) { this.eventEndTime = eventEndTime; }

    public Instant getPublicationTimestamp() { return publicationTimestamp; }
    public void setPublicationTimestamp(Instant publicationTimestamp) { this.publicationTimestamp = publicationTimestamp; }

    public String getWaterloggingStatus() { return waterloggingStatus; }
    public void setWaterloggingStatus(String waterloggingStatus) { this.waterloggingStatus = waterloggingStatus; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getVerificationAuthority() { return verificationAuthority; }
    public void setVerificationAuthority(String verificationAuthority) { this.verificationAuthority = verificationAuthority; }

    public String getVerifiedByUser() { return verifiedByUser; }
    public void setVerifiedByUser(String verifiedByUser) { this.verifiedByUser = verifiedByUser; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getSourceOrganization() { return sourceOrganization; }
    public void setSourceOrganization(String sourceOrganization) { this.sourceOrganization = sourceOrganization; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }

    public String getEvidenceFileReference() { return evidenceFileReference; }
    public void setEvidenceFileReference(String evidenceFileReference) { this.evidenceFileReference = evidenceFileReference; }

    public String getLicenseCode() { return licenseCode; }
    public void setLicenseCode(String licenseCode) { this.licenseCode = licenseCode; }

    public String getPermittedUsage() { return permittedUsage; }
    public void setPermittedUsage(String permittedUsage) { this.permittedUsage = permittedUsage; }

    public Double getSpatialAccuracyM() { return spatialAccuracyM; }
    public void setSpatialAccuracyM(Double spatialAccuracyM) { this.spatialAccuracyM = spatialAccuracyM; }

    public Boolean getIsApproximateLocation() { return isApproximateLocation; }
    public void setIsApproximateLocation(Boolean isApproximateLocation) { this.isApproximateLocation = isApproximateLocation; }

    public Integer getTemporalAccuracyMinutes() { return temporalAccuracyMinutes; }
    public void setTemporalAccuracyMinutes(Integer temporalAccuracyMinutes) { this.temporalAccuracyMinutes = temporalAccuracyMinutes; }

    public Double getMeasuredWaterDepthM() { return measuredWaterDepthM; }
    public void setMeasuredWaterDepthM(Double measuredWaterDepthM) { this.measuredWaterDepthM = measuredWaterDepthM; }

    public String getDepthMeasurementMethod() { return depthMeasurementMethod; }
    public void setDepthMeasurementMethod(String depthMeasurementMethod) { this.depthMeasurementMethod = depthMeasurementMethod; }

    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = verificationNotes; }

    public String getProvenanceClassification() { return provenanceClassification; }
    public void setProvenanceClassification(String provenanceClassification) { this.provenanceClassification = provenanceClassification; }

    public String getAlignmentStatus() { return alignmentStatus; }
    public void setAlignmentStatus(String alignmentStatus) { this.alignmentStatus = alignmentStatus; }

    public Double getRainfall1hMm() { return rainfall1hMm; }
    public void setRainfall1hMm(Double rainfall1hMm) { this.rainfall1hMm = rainfall1hMm; }

    public Double getRainfall3hMm() { return rainfall3hMm; }
    public void setRainfall3hMm(Double rainfall3hMm) { this.rainfall3hMm = rainfall3hMm; }

    public Double getRainfall6hMm() { return rainfall6hMm; }
    public void setRainfall6hMm(Double rainfall6hMm) { this.rainfall6hMm = rainfall6hMm; }

    public Double getRainfall24hMm() { return rainfall24hMm; }
    public void setRainfall24hMm(Double rainfall24hMm) { this.rainfall24hMm = rainfall24hMm; }

    public Double getAntecedentRainfallIndex() { return antecedentRainfallIndex; }
    public void setAntecedentRainfallIndex(Double antecedentRainfallIndex) { this.antecedentRainfallIndex = antecedentRainfallIndex; }

    public Double getElevationM() { return elevationM; }
    public void setElevationM(Double elevationM) { this.elevationM = elevationM; }

    public Double getSlopeDegrees() { return slopeDegrees; }
    public void setSlopeDegrees(Double slopeDegrees) { this.slopeDegrees = slopeDegrees; }

    public Double getRelativeDepressionM() { return relativeDepressionM; }
    public void setRelativeDepressionM(Double relativeDepressionM) { this.relativeDepressionM = relativeDepressionM; }

    public Double getNearestDrainDistanceM() { return nearestDrainDistanceM; }
    public void setNearestDrainDistanceM(Double nearestDrainDistanceM) { this.nearestDrainDistanceM = nearestDrainDistanceM; }

    public String getCorridorName() { return corridorName; }
    public void setCorridorName(String corridorName) { this.corridorName = corridorName; }

    public Long getRelatedReportId() { return relatedReportId; }
    public void setRelatedReportId(Long relatedReportId) { this.relatedReportId = relatedReportId; }

    public Long getRelatedIncidentId() { return relatedIncidentId; }
    public void setRelatedIncidentId(Long relatedIncidentId) { this.relatedIncidentId = relatedIncidentId; }

    public String getReviewQueueStatus() { return reviewQueueStatus; }
    public void setReviewQueueStatus(String reviewQueueStatus) { this.reviewQueueStatus = reviewQueueStatus; }

    public Instant getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(Instant ingestedAt) { this.ingestedAt = ingestedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
