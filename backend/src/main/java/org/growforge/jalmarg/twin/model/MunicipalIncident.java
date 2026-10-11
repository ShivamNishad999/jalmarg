package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.twin.model.TwinEnums.IncidentStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.IncidentType;

import java.time.Instant;

/**
 * Represents an actionable municipal flood incident with lifecycle transitions and dispatch assignment.
 */
@Entity
@Table(name = "twin_municipal_incidents", indexes = {
        @Index(name = "idx_incident_number", columnList = "incidentNumber", unique = true),
        @Index(name = "idx_incident_status", columnList = "status"),
        @Index(name = "idx_incident_severity", columnList = "severity"),
        @Index(name = "idx_incident_zone", columnList = "zone_id")
})
public class MunicipalIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String incidentNumber; // e.g. "INC-2026-1001"

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status; // NEW, TRIAGED, ASSIGNED, IN_PROGRESS, RESOLVED, VERIFIED_CLOSED, REJECTED

    private double latitude;
    private double longitude;

    @Column(nullable = false, length = 256)
    private String locationName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private MunicipalZone zone;

    @Column(length = 128)
    private String assignedDepartment; // e.g. "Delhi Jal Board", "PWD Drainage", "Traffic Police"

    @Column(length = 128)
    private String assignedTeam;

    private int priorityScore; // Decision-support priority ranking (0-100)

    private Long relatedReportId;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;

    @Column(length = 1000)
    private String resolutionNotes;

    public MunicipalIncident() {}

    public MunicipalIncident(String incidentNumber, String title, String description, IncidentType incidentType,
                             Severity severity, IncidentStatus status, double latitude, double longitude,
                             String locationName, MunicipalZone zone, String assignedDepartment,
                             String assignedTeam, int priorityScore, Long relatedReportId) {
        this.incidentNumber = incidentNumber;
        this.title = title;
        this.description = description;
        this.incidentType = incidentType;
        this.severity = severity;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.zone = zone;
        this.assignedDepartment = assignedDepartment;
        this.assignedTeam = assignedTeam;
        this.priorityScore = priorityScore;
        this.relatedReportId = relatedReportId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIncidentNumber() { return incidentNumber; }
    public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public IncidentType getIncidentType() { return incidentType; }
    public void setIncidentType(IncidentType incidentType) { this.incidentType = incidentType; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public MunicipalZone getZone() { return zone; }
    public void setZone(MunicipalZone zone) { this.zone = zone; }

    public String getAssignedDepartment() { return assignedDepartment; }
    public void setAssignedDepartment(String assignedDepartment) { this.assignedDepartment = assignedDepartment; }

    public String getAssignedTeam() { return assignedTeam; }
    public void setAssignedTeam(String assignedTeam) { this.assignedTeam = assignedTeam; }

    public int getPriorityScore() { return priorityScore; }
    public void setPriorityScore(int priorityScore) { this.priorityScore = priorityScore; }

    public Long getRelatedReportId() { return relatedReportId; }
    public void setRelatedReportId(Long relatedReportId) { this.relatedReportId = relatedReportId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
