package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.twin.model.TwinEnums.ResourceStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.ResourceType;

import java.time.Instant;

/**
 * Tracks field response equipment and municipal emergency crews.
 */
@Entity
@Table(name = "twin_response_resources", indexes = {
        @Index(name = "idx_resource_code", columnList = "resourceCode", unique = true),
        @Index(name = "idx_resource_status", columnList = "status")
})
public class ResponseResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String resourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType resourceType;

    @Column(nullable = false, length = 128)
    private String name;

    private double currentLatitude;
    private double currentLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status; // AVAILABLE, DEPLOYED, OUT_OF_SERVICE

    private Long assignedIncidentId;

    @Column(length = 128)
    private String contactInfo;

    private Instant lastUpdated;

    public ResponseResource() {}

    public ResponseResource(String resourceCode, ResourceType resourceType, String name,
                            double currentLatitude, double currentLongitude, ResourceStatus status,
                            Long assignedIncidentId, String contactInfo) {
        this.resourceCode = resourceCode;
        this.resourceType = resourceType;
        this.name = name;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.status = status;
        this.assignedIncidentId = assignedIncidentId;
        this.contactInfo = contactInfo;
        this.lastUpdated = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getResourceCode() { return resourceCode; }
    public void setResourceCode(String resourceCode) { this.resourceCode = resourceCode; }

    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(double currentLatitude) { this.currentLatitude = currentLatitude; }

    public double getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(double currentLongitude) { this.currentLongitude = currentLongitude; }

    public ResourceStatus getStatus() { return status; }
    public void setStatus(ResourceStatus status) { this.status = status; }

    public Long getAssignedIncidentId() { return assignedIncidentId; }
    public void setAssignedIncidentId(Long assignedIncidentId) { this.assignedIncidentId = assignedIncidentId; }

    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
