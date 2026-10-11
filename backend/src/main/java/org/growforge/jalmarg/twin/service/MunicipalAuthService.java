package org.growforge.jalmarg.twin.service;

import jakarta.annotation.PostConstruct;
import org.growforge.jalmarg.twin.model.AuditEvent;
import org.growforge.jalmarg.twin.model.MunicipalUser;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.repo.AuditEventRepository;
import org.growforge.jalmarg.twin.repo.MunicipalUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MunicipalAuthService {

    private static final Logger log = LoggerFactory.getLogger(MunicipalAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MunicipalUserRepository userRepo;
    private final AuditEventRepository auditRepo;
    private final String configuredAdminKey;
    /** Password for the seeded staff accounts on a deployed server (MUNICIPAL_PASSWORD). Empty = local dev defaults. */
    private final String deployedStaffPassword;
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12);

    // Active session tokens: token -> Session
    private final Map<String, ActiveSession> sessions = new ConcurrentHashMap<>();

    // Brute-force rate limiting: key (IP or username) -> list of failed attempt timestamps
    private final Map<String, List<Instant>> failedAttempts = new ConcurrentHashMap<>();

    public record ActiveSession(
            Long userId,
            String username,
            String fullName,
            String department,
            Role role,
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record LoginResult(
            String token,
            ActiveSession session
    ) {}

    public record MunicipalPrincipal(
            Long userId,
            String username,
            String fullName,
            String department,
            Role role,
            boolean isTokenAuthenticated
    ) {
        public boolean hasRole(Role minimumRole) {
            if (role == Role.MUNICIPAL_ADMIN) return true;
            if (role == Role.MUNICIPAL_OPERATOR && (minimumRole == Role.MUNICIPAL_OPERATOR || minimumRole == Role.MUNICIPAL_VIEWER)) return true;
            return role == minimumRole;
        }
    }

    /** Tests and local tools: dev default passwords. */
    public MunicipalAuthService(MunicipalUserRepository userRepo, AuditEventRepository auditRepo, String configuredAdminKey) {
        this(userRepo, auditRepo, configuredAdminKey, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MunicipalAuthService(
            MunicipalUserRepository userRepo,
            AuditEventRepository auditRepo,
            @Value("${jalmarg.authority.admin-key:${jalmarg.alerts.run-key:jalmarg-dev-admin-key-2026}}") String configuredAdminKey,
            @Value("${jalmarg.authority.staff-password:}") String deployedStaffPassword
    ) {
        this.deployedStaffPassword = deployedStaffPassword == null ? "" : deployedStaffPassword.trim();
        this.userRepo = userRepo;
        this.auditRepo = auditRepo;
        this.configuredAdminKey = configuredAdminKey;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultAccounts() {
        if (userRepo.count() > 0) return;
        // On a public server the published dev passwords must not work: MUNICIPAL_PASSWORD replaces all three.
        boolean deployed = !deployedStaffPassword.isEmpty();
        String op = deployed ? deployedStaffPassword : "PwdOperator#2026";
        String ad = deployed ? deployedStaffPassword : "NdmcAdmin#2026";
        String vw = deployed ? deployedStaffPassword : "TrafficView#2026";

        List<MunicipalUser> defaults = List.of(
                new MunicipalUser("operator_pwd", passwordEncoder.encode(op), "bcrypt",
                        "PWD Drainage Field Operator", "pwd.operator@delhi.gov.in", "PWD Central Drainage", Role.MUNICIPAL_OPERATOR),
                new MunicipalUser("admin_ndmc", passwordEncoder.encode(ad), "bcrypt",
                        "NDMC Emergency Center Admin", "ndmc.admin@delhi.gov.in", "NDMC Control Room", Role.MUNICIPAL_ADMIN),
                new MunicipalUser("viewer_traffic", passwordEncoder.encode(vw), "bcrypt",
                        "Delhi Traffic Police Duty Officer", "traffic.duty@delhipolice.gov.in", "Delhi Traffic Police", Role.MUNICIPAL_VIEWER)
        );

        userRepo.saveAll(defaults);
        log.info("Initialized {} default municipal staff accounts with BCrypt-hashed credentials.", defaults.size());
    }

    @Transactional
    public LoginResult login(String username, String password, String clientIp) {
        String rateLimitKey = (clientIp != null ? clientIp : "local") + ":" + (username != null ? username.toLowerCase(Locale.ROOT) : "anon");
        checkRateLimit(rateLimitKey);

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            recordFailedAttempt(rateLimitKey);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required.");
        }

        MunicipalUser user = userRepo.findByUsernameIgnoreCase(username.trim())
                .orElse(null);

        if (user == null || !user.isActive()) {
            recordFailedAttempt(rateLimitKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }

        boolean passwordMatches = false;
        boolean needsUpgrade = false;

        String storedHash = user.getPasswordHash();
        if (storedHash != null && storedHash.startsWith("$2")) {
            // Adaptive BCrypt verification
            passwordMatches = passwordEncoder.matches(password, storedHash);
        } else if (storedHash != null && user.getSalt() != null) {
            // Legacy salted SHA-256 verification
            String computedHash = hashPassword(password, user.getSalt());
            if (MessageDigest.isEqual(computedHash.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8))) {
                passwordMatches = true;
                needsUpgrade = true;
            }
        }

        if (!passwordMatches) {
            recordFailedAttempt(rateLimitKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }

        // Clear failed attempts upon successful login
        failedAttempts.remove(rateLimitKey);

        // Transparently upgrade legacy SHA-256 hash to BCrypt (cost factor 12)
        if (needsUpgrade) {
            String upgradedHash = passwordEncoder.encode(password);
            user.setPasswordHash(upgradedHash);
            user.setSalt("bcrypt");
            log.info("Seamlessly upgraded legacy password hash to BCrypt for user: {}", user.getUsername());
            auditRepo.save(new AuditEvent(
                    user.getUsername(),
                    user.getRole().name(),
                    "CREDENTIAL_UPGRADE_BCRYPT",
                    "MunicipalUser",
                    user.getId().toString(),
                    clientIp,
                    "{\"reason\":\"Adaptive BCrypt (cost 12) migration on successful authentication\"}"
            ));
        }

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        // Generate cryptographically secure session token
        byte[] tokenBytes = new byte[24];
        RANDOM.nextBytes(tokenBytes);
        String token = "jm_sess_" + HexFormat.of().formatHex(tokenBytes);

        Instant expiresAt = Instant.now().plus(Duration.ofHours(8));
        ActiveSession session = new ActiveSession(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getDepartment(),
                user.getRole(),
                Instant.now(),
                expiresAt
        );

        sessions.put(token, session);

        // Audit Trail
        auditRepo.save(new AuditEvent(
                user.getUsername(),
                user.getRole().name(),
                "USER_LOGIN",
                "MunicipalUser",
                user.getId().toString(),
                clientIp,
                String.format(Locale.ROOT, "{\"department\":\"%s\",\"role\":\"%s\",\"bcryptMigrated\":%b}",
                        user.getDepartment(), user.getRole(), needsUpgrade)
        ));

        log.info("Municipal operator logged in: {} ({}, {})", user.getUsername(), user.getDepartment(), user.getRole());
        return new LoginResult(token, session);
    }

    public void logout(String token, String clientIp) {
        if (token == null) return;
        ActiveSession session = sessions.remove(token);
        if (session != null) {
            auditRepo.save(new AuditEvent(
                    session.username(),
                    session.role().name(),
                    "USER_LOGOUT",
                    "MunicipalUser",
                    session.userId().toString(),
                    clientIp,
                    "{\"status\":\"LOGGED_OUT\"}"
            ));
            log.info("Municipal operator logged out: {}", session.username());
        }
    }

    public MunicipalPrincipal authenticate(String authHeader, String adminKeyHeader, String jalmargKeyHeader) {
        // 1. Try bearer session token
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (authHeader != null && !authHeader.isBlank()) {
            token = authHeader.trim();
        }

        if (token != null && !token.isBlank()) {
            ActiveSession session = sessions.get(token);
            if (session != null) {
                if (session.expiresAt().isBefore(Instant.now())) {
                    sessions.remove(token);
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
                }
                return new MunicipalPrincipal(
                        session.userId(),
                        session.username(),
                        session.fullName(),
                        session.department(),
                        session.role(),
                        true
                );
            }
        }

        // 2. Compatibility fallback: Validate shared admin key / developer key
        String keyToTest = adminKeyHeader != null ? adminKeyHeader : jalmargKeyHeader;
        if (keyToTest != null && !keyToTest.isBlank()) {
            byte[] provided = keyToTest.getBytes(StandardCharsets.UTF_8);
            byte[] expected = configuredAdminKey.getBytes(StandardCharsets.UTF_8);
            byte[] devExpected = "jalmarg-dev-admin-key-2026".getBytes(StandardCharsets.UTF_8);

            boolean matchConfigured = MessageDigest.isEqual(provided, expected);
            boolean matchDev = MessageDigest.isEqual(provided, devExpected);

            if (matchConfigured || matchDev) {
                return new MunicipalPrincipal(
                        0L,
                        "authority-admin",
                        "Municipal System Administrator",
                        "Municipal Control Room",
                        Role.MUNICIPAL_ADMIN,
                        false
                );
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: Invalid administrative authorization key.");
        }

        if (token != null && !token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: Invalid or expired session token.");
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Missing required administrative authorization token.");
    }

    public void enforceRole(MunicipalPrincipal principal, Role minimumRole) {
        if (principal == null || !principal.hasRole(minimumRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    String.format(Locale.ROOT, "Forbidden: Action requires at least %s role permissions.", minimumRole)
            );
        }
    }

    public ActiveSession getSession(String token) {
        if (token == null) return null;
        ActiveSession s = sessions.get(token);
        if (s != null && s.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return s;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String clientIp) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user identifier.");
        }
        if (currentPassword == null || currentPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password and new password are required.");
        }

        MunicipalUser user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is inactive.");
        }

        // Verify current password
        boolean currentMatches = false;
        String storedHash = user.getPasswordHash();
        if (storedHash != null && storedHash.startsWith("$2")) {
            currentMatches = passwordEncoder.matches(currentPassword, storedHash);
        } else if (storedHash != null && user.getSalt() != null) {
            String computedHash = hashPassword(currentPassword, user.getSalt());
            currentMatches = MessageDigest.isEqual(computedHash.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
        }

        if (!currentMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password verification failed.");
        }

        // Password policy validation
        if (newPassword.equals(currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password cannot be identical to current password.");
        }
        if (newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters in length.");
        }
        boolean hasUpper = newPassword.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = newPassword.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = newPassword.chars().anyMatch(ch -> "!@#$%^&*()_+-=[]{}|;:,.<>?".indexOf(ch) >= 0);

        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password must contain uppercase, lowercase, numeric digits, and special characters.");
        }

        // Apply BCrypt hash (cost 12)
        String newHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newHash);
        user.setSalt("bcrypt");
        userRepo.save(user);

        // Invalidate all active sessions for this user
        invalidateUserSessions(userId);

        auditRepo.save(new AuditEvent(
                user.getUsername(),
                user.getRole().name(),
                "PASSWORD_CHANGED",
                "MunicipalUser",
                user.getId().toString(),
                clientIp,
                "{\"status\":\"SUCCESS\",\"algorithm\":\"BCrypt-cost12\"}"
        ));

        log.info("Password successfully changed and sessions invalidated for user: {}", user.getUsername());
    }

    public void invalidateUserSessions(Long userId) {
        if (userId == null) return;
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not available", e);
        }
    }

    private void checkRateLimit(String key) {
        List<Instant> attempts = failedAttempts.get(key);
        if (attempts != null) {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
            long recentFailed = attempts.stream().filter(t -> t.isAfter(cutoff)).count();
            if (recentFailed >= 5) {
                log.warn("Rate-limiting authentication attempts for {}", key);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Please try again after 5 minutes.");
            }
        }
    }

    private void recordFailedAttempt(String key) {
        failedAttempts.compute(key, (k, v) -> {
            List<Instant> list = v != null ? new ArrayList<>(v) : new ArrayList<>();
            list.add(Instant.now());
            // Keep only recent 10 attempts
            if (list.size() > 10) list = list.subList(list.size() - 10, list.size());
            return list;
        });
    }
}
