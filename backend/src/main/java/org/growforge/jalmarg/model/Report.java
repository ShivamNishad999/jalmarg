package org.growforge.jalmarg.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.model.Enums.*;

import java.time.Instant;

/** One citizen report of waterlogging at a spot. */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_report_status", columnList = "status"),
        @Index(name = "idx_report_hotspot", columnList = "hotspot_id")
})
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private double latitude;
    private double longitude;

    private String locationName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hotspot_id")
    private Hotspot hotspot;

    /** S3 key or local file name of the photo, null if no photo. */
    private String photoKey;

    @Enumerated(EnumType.STRING)
    private DepthLevel citizenDepthLevel;

    /** Final depth estimate in feet (AI, citizen, or both combined). */
    private double depthFt;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    private DepthSource depthSource;

    private boolean walkSafe;
    private boolean bikeSafe;
    private boolean carSafe;

    /** AI explanation, e.g. "Water covers half the scooter tyre". */
    @Column(length = 1000)
    private String aiSummary;

    @Column(length = 1000)
    private String aiSummaryHi;

    /** 0..1, null when AI was not used. */
    private Double aiConfidence;

    @Column(length = 500)
    private String note;

    /** How many other people said "still flooded". */
    private int confirmations;

    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.ACTIVE;

    @Column(name = "verification_status", length = 64)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_notes", length = 500)
    private String verificationNotes;

    @Column(name = "parent_report_id")
    private Long parentReportId;

    @Column(name = "is_duplicate_merged")
    private Boolean isDuplicateMerged = Boolean.FALSE;

    @Column(name = "image_prediction", length = 64)
    private String imagePrediction;

    @Column(name = "image_confidence")
    private Double imageConfidence;

    @Column(name = "image_inference_status", length = 32)
    private String imageInferenceStatus;

    private Instant createdAt;
    private Instant lastConfirmedAt;
    private Instant clearedAt;

    public Report() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public Hotspot getHotspot() { return hotspot; }
    public void setHotspot(Hotspot hotspot) { this.hotspot = hotspot; }
    public String getPhotoKey() { return photoKey; }
    public void setPhotoKey(String photoKey) { this.photoKey = photoKey; }
    public DepthLevel getCitizenDepthLevel() { return citizenDepthLevel; }
    public void setCitizenDepthLevel(DepthLevel citizenDepthLevel) { this.citizenDepthLevel = citizenDepthLevel; }
    public double getDepthFt() { return depthFt; }
    public void setDepthFt(double depthFt) { this.depthFt = depthFt; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public DepthSource getDepthSource() { return depthSource; }
    public void setDepthSource(DepthSource depthSource) { this.depthSource = depthSource; }
    public boolean isWalkSafe() { return walkSafe; }
    public void setWalkSafe(boolean walkSafe) { this.walkSafe = walkSafe; }
    public boolean isBikeSafe() { return bikeSafe; }
    public void setBikeSafe(boolean bikeSafe) { this.bikeSafe = bikeSafe; }
    public boolean isCarSafe() { return carSafe; }
    public void setCarSafe(boolean carSafe) { this.carSafe = carSafe; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getAiSummaryHi() { return aiSummaryHi; }
    public void setAiSummaryHi(String aiSummaryHi) { this.aiSummaryHi = aiSummaryHi; }
    public Double getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(Double aiConfidence) { this.aiConfidence = aiConfidence; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getConfirmations() { return confirmations; }
    public void setConfirmations(int confirmations) { this.confirmations = confirmations; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public String getVerificationStatus() { return verificationStatus != null ? verificationStatus : "UNVERIFIED"; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = verificationNotes; }
    public Long getParentReportId() { return parentReportId; }
    public void setParentReportId(Long parentReportId) { this.parentReportId = parentReportId; }
    public boolean isDuplicateMerged() { return isDuplicateMerged != null && isDuplicateMerged; }
    public void setDuplicateMerged(Boolean duplicateMerged) { this.isDuplicateMerged = duplicateMerged != null && duplicateMerged; }
    public String getImagePrediction() { return imagePrediction; }
    public void setImagePrediction(String imagePrediction) { this.imagePrediction = imagePrediction; }
    public Double getImageConfidence() { return imageConfidence; }
    public void setImageConfidence(Double imageConfidence) { this.imageConfidence = imageConfidence; }
    public String getImageInferenceStatus() { return imageInferenceStatus; }
    public void setImageInferenceStatus(String imageInferenceStatus) { this.imageInferenceStatus = imageInferenceStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastConfirmedAt() { return lastConfirmedAt; }
    public void setLastConfirmedAt(Instant lastConfirmedAt) { this.lastConfirmedAt = lastConfirmedAt; }
    public Instant getClearedAt() { return clearedAt; }
    public void setClearedAt(Instant clearedAt) { this.clearedAt = clearedAt; }
}
