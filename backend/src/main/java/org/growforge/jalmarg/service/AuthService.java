package org.growforge.jalmarg.service;

import org.growforge.jalmarg.model.AppUser;
import org.growforge.jalmarg.model.AuthSession;
import org.growforge.jalmarg.model.Enums.OtpPurpose;
import org.growforge.jalmarg.model.Enums.Role;
import org.growforge.jalmarg.model.OtpChallenge;
import org.growforge.jalmarg.repo.AppUserRepository;
import org.growforge.jalmarg.repo.AuthSessionRepository;
import org.growforge.jalmarg.repo.OtpChallengeRepository;
import org.growforge.jalmarg.security.RateLimiter;
import org.growforge.jalmarg.security.Tokens;
import org.growforge.jalmarg.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Accounts: sign-up with email OTP, sign-in, sign-out, password reset, roles.
 *
 * Rules
 * - Passwords: BCrypt (cost 11), 8–72 chars, at least one letter and one digit.
 * - OTP: 6 digits from SecureRandom, stored as a BCrypt hash, 5-minute expiry, single use,
 *   5 wrong tries burn it, a new code invalidates older ones, 60 s resend cooldown,
 *   at most 5 codes per hour per account.
 * - Sessions: random 256-bit bearer token, only its SHA-256 is stored, 30-day expiry,
 *   deleted on logout; all sessions deleted on password reset.
 * - Sign-in: 5 wrong passwords lock the account for 15 minutes; per-IP limits on top.
 * - Answers to register / resend / forgot do not reveal whether an email has an account.
 */
@Service
public class AuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final int OTP_MAX_ATTEMPTS = 5;
    static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int OTP_MAX_PER_HOUR = 5;
    static final int LOGIN_MAX_FAILS = 5;
    static final Duration LOGIN_LOCK = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final OtpChallengeRepository otps;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final RateLimiter limiter;
    private final int sessionDays;
    private final String adminEmail;
    private final String adminPassword;
    /** Real BCrypt hash of a random value, so a login for an unknown email costs the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, OtpChallengeRepository otps, AuthSessionRepository sessions,
                       PasswordEncoder encoder, MailService mail, RateLimiter limiter,
                       @Value("${jalmarg.auth.session-days}") int sessionDays,
                       @Value("${jalmarg.auth.admin-email}") String adminEmail,
                       @Value("${jalmarg.auth.admin-password}") String adminPassword) {
        this.users = users;
        this.otps = otps;
        this.sessions = sessions;
        this.encoder = encoder;
        this.mail = mail;
        this.limiter = limiter;
        this.sessionDays = sessionDays;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.dummyHash = encoder.encode(Tokens.newToken());
    }

    public record LoginResult(String token, Instant expiresAt, AppUser user) {}

    // ------------------------------------------------------------------ helpers

    public static String normalizeEmail(String email) {
        if (email == null) throw bad("EMAIL_INVALID", "Enter a valid email address.");
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 254 || !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) {
            throw bad("EMAIL_INVALID", "Enter a valid email address.");
        }
        return e;
    }

    static void checkPassword(String pw) {
        if (pw == null || pw.length() < 8) throw bad("PASSWORD_WEAK", "Password must be at least 8 characters.");
        if (pw.length() > 72) throw bad("PASSWORD_WEAK", "Password must be at most 72 characters.");
        if (!pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) {
            throw bad("PASSWORD_WEAK", "Password needs at least one letter and one number.");
        }
    }

    private static ApiException bad(String code, String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, msg);
    }

    private void limit(String key, int max, long seconds) {
        if (!limiter.allow(key, max, seconds)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many attempts. Please wait a minute and try again.");
        }
    }

    private void requireMail() {
        if (!mail.canSend()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED",
                    "Email codes are not set up on this server yet. Ask the team to configure email.");
        }
    }

    // ------------------------------------------------------------------ sign-up

    /** Creates or refreshes an unverified account and emails a code. Same answer whether or not the email exists. */
    @Transactional
    public void register(String name, String rawEmail, String password, String ip) {
        limit("register-ip:" + ip, 10, 3600);
        String email = normalizeEmail(rawEmail);
        String n = name == null ? "" : name.trim();
        if (n.length() < 2 || n.length() > 100) throw bad("NAME_INVALID", "Enter your name (2–100 characters).");
        checkPassword(password);
        requireMail();

        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isPresent() && existing.get().isEmailVerified()) {
            // Do not reveal that the account exists; they can sign in or reset the password.
            log.info("Sign-up attempt for an existing verified account");
            return;
        }
        AppUser u = existing.orElseGet(AppUser::new);
        u.setEmail(email);
        u.setName(n);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(u.getRole() == null ? Role.CITIZEN : u.getRole());
        if (u.getCreatedAt() == null) u.setCreatedAt(Instant.now());
        u = users.save(u);
        issueOtp(u, OtpPurpose.VERIFY_EMAIL, true);
    }

    @Transactional(noRollbackFor = ApiException.class) // keep the wrong-attempt count
    public void verifyEmail(String rawEmail, String code, String ip) {
        limit("verify-ip:" + ip, 30, 600);
        String email = normalizeEmail(rawEmail);
        AppUser u = users.findByEmail(email).orElseThrow(AuthService::invalidCode);
        if (u.isEmailVerified()) return; // already done: treat as success
        checkOtp(u, OtpPurpose.VERIFY_EMAIL, code);
        u.setEmailVerified(true);
        u.setVerifiedAt(Instant.now());
        users.save(u);
    }

    /** Sends a fresh code (verify or reset). Generic answer; enforces cooldown. */
    @Transactional
    public void resend(String rawEmail, OtpPurpose purpose, String ip) {
        limit("resend-ip:" + ip, 10, 600);
        String email = normalizeEmail(rawEmail);
        requireMail();
        users.findByEmail(email).ifPresent(u -> {
            if (purpose == OtpPurpose.VERIFY_EMAIL && u.isEmailVerified()) return;
            if (purpose == OtpPurpose.RESET_PASSWORD && !u.isEmailVerified()) return;
            issueOtp(u, purpose, true);
        });
    }

    // ------------------------------------------------------------------ sign-in / out

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(String rawEmail, String password, String ip, String userAgent) {
        limit("login-ip:" + ip, 20, 300);
        String email = normalizeEmail(rawEmail);
        AppUser u = users.findByEmail(email).orElse(null);
        Instant now = Instant.now();
        if (u == null) {
            encoder.matches(password == null ? "" : password, dummyHash); // even out timing
            throw wrongCredentials();
        }
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many wrong passwords. Try again in 15 minutes or reset your password.");
        }
        if (password == null || !encoder.matches(password, u.getPasswordHash())) {
            u.setFailedLogins(u.getFailedLogins() + 1);
            if (u.getFailedLogins() >= LOGIN_MAX_FAILS) {
                u.setLockedUntil(now.plus(LOGIN_LOCK));
                u.setFailedLogins(0);
            }
            users.save(u);
            throw wrongCredentials();
        }
        if (!u.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Verify your email first. We can send you a new code.");
        }
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        users.save(u);

        String token = Tokens.newToken();
        AuthSession s = new AuthSession();
        s.setUser(u);
        s.setTokenHash(Tokens.sha256(token));
        s.setCreatedAt(now);
        s.setLastUsedAt(now);
        s.setExpiresAt(now.plus(Duration.ofDays(sessionDays)));
        s.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(200, userAgent.length())));
        sessions.save(s);
        return new LoginResult(token, s.getExpiresAt(), u);
    }

    private static ApiException wrongCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is wrong.");
    }

    @Transactional
    public void logout(Long sessionId) {
        if (sessionId != null) sessions.deleteById(sessionId);
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Please sign in again."));
    }

    // ------------------------------------------------------------------ password reset

    @Transactional
    public void forgot(String rawEmail, String ip) {
        limit("forgot-ip:" + ip, 10, 600);
        String email = normalizeEmail(rawEmail);
        requireMail();
        users.findByEmail(email).filter(AppUser::isEmailVerified)
                .ifPresent(u -> issueOtp(u, OtpPurpose.RESET_PASSWORD, false));
    }

    @Transactional(noRollbackFor = ApiException.class) // keep the wrong-attempt count
    public void reset(String rawEmail, String code, String newPassword, String ip) {
        limit("reset-ip:" + ip, 20, 600);
        String email = normalizeEmail(rawEmail);
        checkPassword(newPassword);
        AppUser u = users.findByEmail(email).filter(AppUser::isEmailVerified).orElseThrow(AuthService::invalidCode);
        checkOtp(u, OtpPurpose.RESET_PASSWORD, code);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setPasswordChangedAt(Instant.now());
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        users.save(u);
        int revoked = sessions.deleteAllForUser(u.getId());
        log.info("Password reset for user {}: {} sessions revoked", u.getId(), revoked);
    }

    // ------------------------------------------------------------------ roles

    @Transactional
    public AppUser setRole(String rawEmail, Role role) {
        AppUser u = users.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No account with that email."));
        u.setRole(role);
        users.save(u);
        sessions.deleteAllForUser(u.getId()); // new role applies at next sign-in
        return u;
    }

    // ------------------------------------------------------------------ OTP internals

    private void issueOtp(AppUser u, OtpPurpose purpose, boolean enforceCooldown) {
        Instant now = Instant.now();
        if (enforceCooldown) {
            otps.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(u.getId(), purpose).ifPresent(last -> {
                if (last.getCreatedAt().isAfter(now.minus(OTP_RESEND_COOLDOWN))) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_COOLDOWN",
                            "Please wait a minute before asking for another code.");
                }
            });
        }
        if (otps.countByUserIdAndPurposeAndCreatedAtAfter(u.getId(), purpose, now.minus(Duration.ofHours(1))) >= OTP_MAX_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LIMIT", "Too many codes requested. Try again in an hour.");
        }
        // older codes stop working
        for (OtpChallenge old : otps.findByUserIdAndPurposeAndConsumedFalse(u.getId(), purpose)) {
            old.setConsumed(true);
            otps.save(old);
        }
        String code = Tokens.newOtp();
        OtpChallenge c = new OtpChallenge();
        c.setUser(u);
        c.setPurpose(purpose);
        c.setCodeHash(encoder.encode(code));
        c.setCreatedAt(now);
        c.setExpiresAt(now.plus(OTP_TTL));
        otps.save(c);
        try {
            mail.sendOtp(u.getEmail(), u.getName(), code, purpose);
        } catch (RuntimeException e) {
            log.warn("Could not send OTP email: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_SEND_FAILED",
                    "We could not send the email right now. Please try again in a minute.");
        }
    }

    private void checkOtp(AppUser u, OtpPurpose purpose, String code) {
        if (code == null || !code.trim().matches("\\d{6}")) throw invalidCode();
        OtpChallenge c = otps.findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(u.getId(), purpose)
                .orElseThrow(AuthService::invalidCode);
        if (c.getExpiresAt().isBefore(Instant.now())) {
            c.setConsumed(true);
            otps.save(c);
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "This code has expired. Ask for a new one.");
        }
        if (!encoder.matches(code.trim(), c.getCodeHash())) {
            c.setAttempts(c.getAttempts() + 1);
            if (c.getAttempts() >= OTP_MAX_ATTEMPTS) c.setConsumed(true);
            otps.save(c);
            throw c.isConsumed()
                    ? new ApiException(HttpStatus.BAD_REQUEST, "OTP_LOCKED", "Too many wrong codes. Ask for a new one.")
                    : invalidCode();
        }
        c.setConsumed(true);
        otps.save(c);
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "That code is not right.");
    }

    // ------------------------------------------------------------------ housekeeping

    /** Optional first admin from ADMIN_EMAIL / ADMIN_PASSWORD (never hard-coded). */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) return;
        String email = normalizeEmail(adminEmail);
        if (users.findByEmail(email).isPresent()) return;
        checkPassword(adminPassword);
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setName("Admin");
        u.setPasswordHash(encoder.encode(adminPassword));
        u.setRole(Role.ADMIN);
        u.setEmailVerified(true);
        u.setCreatedAt(Instant.now());
        u.setVerifiedAt(Instant.now());
        users.save(u);
        log.info("Created the first admin account from ADMIN_EMAIL");
    }

    @Scheduled(fixedDelay = 3600_000, initialDelay = 300_000)
    @Transactional
    public void purgeExpiredSessions() {
        sessions.deleteExpired(Instant.now());
    }
}
