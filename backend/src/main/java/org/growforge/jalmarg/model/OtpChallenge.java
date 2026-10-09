package org.growforge.jalmarg.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.model.Enums.OtpPurpose;

import java.time.Instant;

/**
 * One emailed 6-digit code. Only a BCrypt hash of the code is stored.
 * Single use, short expiry, limited attempts; a newer challenge invalidates older ones.
 */
@Entity
@Table(name = "otp_challenges", indexes = @Index(name = "idx_otp_user_purpose", columnList = "user_id,purpose"))
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpPurpose purpose;

    @Column(nullable = false, length = 100)
    private String codeHash;

    private Instant createdAt;
    private Instant expiresAt;
    private int attempts;
    /** Set when the code is used, superseded, or burned by too many attempts. */
    private boolean consumed;

    public OtpChallenge() {}

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public OtpPurpose getPurpose() { return purpose; }
    public void setPurpose(OtpPurpose purpose) { this.purpose = purpose; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean consumed) { this.consumed = consumed; }
}
