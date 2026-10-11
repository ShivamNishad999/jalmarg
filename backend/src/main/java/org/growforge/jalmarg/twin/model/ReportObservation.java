package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.model.Enums.DepthLevel;

import java.time.Instant;

/**
 * Immutable record of every individual citizen submission and photo evidence,
 * even when reports are merged into a cluster, preserving full provenance.
 */
@Entity
@Table(name = "twin_report_observations", indexes = {
        @Index(name = "idx_observation_report_id", columnList = "report_id"),
        @Index(name = "idx_observation_submitted_at", columnList = "submitted_at")
})
public class ReportObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "citizen_depth_level", length = 32)
    private DepthLevel citizenDepthLevel;

    @Column(name = "depth_ft")
    private Double depthFt;

    @Column(name = "photo_key", length = 256)
    private String photoKey;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(length = 500)
    private String note;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    public ReportObservation() {}

    public ReportObservation(Long reportId, double latitude, double longitude, DepthLevel citizenDepthLevel,
                             Double depthFt, String photoKey, String clientIp, String note) {
        this.reportId = reportId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.citizenDepthLevel = citizenDepthLevel;
        this.depthFt = depthFt;
        this.photoKey = photoKey;
        this.clientIp = clientIp;
        this.note = note;
        this.submittedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public DepthLevel getCitizenDepthLevel() { return citizenDepthLevel; }
    public void setCitizenDepthLevel(DepthLevel citizenDepthLevel) { this.citizenDepthLevel = citizenDepthLevel; }

    public Double getDepthFt() { return depthFt; }
    public void setDepthFt(Double depthFt) { this.depthFt = depthFt; }

    public String getPhotoKey() { return photoKey; }
    public void setPhotoKey(String photoKey) { this.photoKey = photoKey; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
