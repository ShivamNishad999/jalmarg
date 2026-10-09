package org.growforge.jalmarg.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A signed-in device. The client holds a random bearer token; the server keeps only its
 * SHA-256 hash, so a database leak does not leak usable tokens. Deleting the row = logout.
 */
@Entity
@Table(name = "auth_sessions", uniqueConstraints = @UniqueConstraint(name = "uk_session_token", columnNames = "tokenHash"))
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;

    @Column(length = 200)
    private String userAgent;

    public AuthSession() {}

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
