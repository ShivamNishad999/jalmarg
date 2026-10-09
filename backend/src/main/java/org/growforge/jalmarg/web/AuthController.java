package org.growforge.jalmarg.web;

import jakarta.servlet.http.HttpServletRequest;
import org.growforge.jalmarg.model.AppUser;
import org.growforge.jalmarg.model.Enums.OtpPurpose;
import org.growforge.jalmarg.model.Enums.Role;
import org.growforge.jalmarg.security.AuthUser;
import org.growforge.jalmarg.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Account API (see docs/API.md). Every request body is validated again in AuthService.
 * Responses never contain passwords, hashes or OTP codes.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record RegisterReq(String name, String email, String password) {}
    public record VerifyReq(String email, String code) {}
    public record EmailReq(String email, String purpose) {}
    public record LoginReq(String email, String password) {}
    public record ResetReq(String email, String code, String newPassword) {}
    public record RoleReq(String email, String role) {}

    public record UserView(Long id, String name, String email, String role, boolean emailVerified, Instant createdAt) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isEmailVerified(), u.getCreatedAt());
        }
    }

    public record LoginView(String token, Instant expiresAt, UserView user) {}

    private static final Map<String, Object> CODE_SENT = Map.of("status", "CODE_SENT",
            "message", "If this email can be used, a 6-digit code has been sent. It expires in 5 minutes.");

    @PostMapping("/auth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterReq r, HttpServletRequest req) {
        auth.register(r.name(), r.email(), r.password(), ip(req));
        return ResponseEntity.accepted().body(CODE_SENT);
    }

    @PostMapping("/auth/verify-email")
    public Map<String, Object> verify(@RequestBody VerifyReq r, HttpServletRequest req) {
        auth.verifyEmail(r.email(), r.code(), ip(req));
        return Map.of("status", "VERIFIED", "message", "Email verified. You can sign in now.");
    }

    @PostMapping("/auth/resend-code")
    public ResponseEntity<Map<String, Object>> resend(@RequestBody EmailReq r, HttpServletRequest req) {
        OtpPurpose p = "RESET_PASSWORD".equalsIgnoreCase(r.purpose()) ? OtpPurpose.RESET_PASSWORD : OtpPurpose.VERIFY_EMAIL;
        auth.resend(r.email(), p, ip(req));
        return ResponseEntity.accepted().body(CODE_SENT);
    }

    @PostMapping("/auth/login")
    public LoginView login(@RequestBody LoginReq r, HttpServletRequest req) {
        AuthService.LoginResult res = auth.login(r.email(), r.password(), ip(req), req.getHeader("User-Agent"));
        return new LoginView(res.token(), res.expiresAt(), UserView.of(res.user()));
    }

    @GetMapping("/auth/me")
    public UserView me() {
        return UserView.of(auth.get(AuthUser.current().id()));
    }

    /** Deletes this device's session on the server. */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        AuthUser u = AuthUser.current();
        auth.logout(u == null ? null : u.sessionId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Map<String, Object>> forgot(@RequestBody EmailReq r, HttpServletRequest req) {
        auth.forgot(r.email(), ip(req));
        return ResponseEntity.accepted().body(CODE_SENT);
    }

    @PostMapping("/auth/reset-password")
    public Map<String, Object> reset(@RequestBody ResetReq r, HttpServletRequest req) {
        auth.reset(r.email(), r.code(), r.newPassword(), ip(req));
        return Map.of("status", "PASSWORD_RESET", "message", "Password changed. Sign in with the new password.");
    }

    /** ADMIN only (SecurityConfig): make someone an AUTHORITY, ADMIN or back to CITIZEN. */
    @PostMapping("/admin/users/role")
    public UserView setRole(@RequestBody RoleReq r) {
        Role role;
        try {
            role = Role.valueOf(r.role() == null ? "" : r.role().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_INVALID", "Role must be CITIZEN, AUTHORITY or ADMIN.");
        }
        return UserView.of(auth.setRole(r.email(), role));
    }

    /** Client IP for rate limiting (first X-Forwarded-For hop behind CloudFront / a proxy). */
    static String ip(HttpServletRequest req) {
        String f = req.getHeader("X-Forwarded-For");
        if (f != null && !f.isBlank()) return f.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
