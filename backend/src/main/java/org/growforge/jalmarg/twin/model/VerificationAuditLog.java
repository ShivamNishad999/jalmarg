package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_verification_audit_log", indexes = {
        @Index(name = "idx_ml_audit_obs_id", columnList = "observation_id"),
        @Index(name = "idx_ml_audit_actor", columnList = "actor_username"),
        @Index(name = "idx_ml_audit_decided_at", columnList = "decided_at")
})
public class VerificationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "observation_id", nullable = false, length = 64)
    private String observationId;

    @Column(name = "actor_username", nullable = false, length = 64)
    private String actorUsername;

    @Column(name = "actor_role", nullable = false, length = 64)
    private String actorRole;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();

    public VerificationAuditLog() {}

    public VerificationAuditLog(String observationId, String actorUsername, String actorRole,
                                String action, String previousStatus, String newStatus,
                                String reason, String clientIp) {
        this.observationId = observationId;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.action = action;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.clientIp = clientIp;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getObservationId() { return observationId; }
    public void setObservationId(String observationId) { this.observationId = observationId; }

    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
