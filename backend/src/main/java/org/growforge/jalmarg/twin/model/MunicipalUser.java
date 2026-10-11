package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "twin_municipal_users", indexes = {
        @Index(name = "idx_municipal_user_role", columnList = "role")
})
public class MunicipalUser {

    public enum Role {
        MUNICIPAL_VIEWER,
        MUNICIPAL_OPERATOR,
        MUNICIPAL_ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String salt;

    @Column(nullable = false, length = 128)
    private String fullName;

    @Column(nullable = false, length = 128)
    private String email;

    @Column(nullable = false, length = 128)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Role role;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    public MunicipalUser() {}

    public MunicipalUser(String username, String passwordHash, String salt, String fullName,
                         String email, String department, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.fullName = fullName;
        this.email = email;
        this.department = department;
        this.role = role;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
