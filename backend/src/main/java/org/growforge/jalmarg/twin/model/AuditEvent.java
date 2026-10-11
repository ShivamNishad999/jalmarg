package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Immutable audit trail for municipal emergency dispatch, status changes, and simulations.
 */
@Entity
@Table(name = "twin_audit_events", indexes = {
        @Index(name = "idx_audit_timestamp", columnList = "eventTimestamp"),
        @Index(name = "idx_audit_actor", columnList = "actorId"),
        @Index(name = "idx_audit_target", columnList = "targetEntity, targetEntityId")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false, length = 64)
    private String actorId;

    @Column(nullable = false, length = 64)
    private String actorRole; // SYSTEM, AUTHORITY_OPERATOR, MUNICIPAL_DISPATCHER, ADMIN

    @Column(nullable = false, length = 64)
    private String actionType; // INCIDENT_CREATE, INCIDENT_ASSIGN, INCIDENT_STATUS_CHANGE, SCENARIO_RUN, etc.

    @Column(nullable = false, length = 64)
    private String targetEntity;

    @Column(length = 64)
    private String targetEntityId;

    @Column(length = 64)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    public AuditEvent() {}

    public AuditEvent(String actorId, String actorRole, String actionType, String targetEntity,
                      String targetEntityId, String ipAddress, String detailsJson) {
        this.eventTimestamp = Instant.now();
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.actionType = actionType;
        this.targetEntity = targetEntity;
        this.targetEntityId = targetEntityId;
        this.ipAddress = ipAddress;
        this.detailsJson = detailsJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }

    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
}
