package org.growforge.jalmarg.twin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.ActiveSession;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class MunicipalAuthController {

    private final MunicipalAuthService authService;

    public MunicipalAuthController(MunicipalAuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(String username, String password) {}

    public record AuthResponse(
            String token,
            Long userId,
            String username,
            String fullName,
            String department,
            String role,
            Instant expiresAt
    ) {
        public static AuthResponse of(String token, ActiveSession session) {
            return new AuthResponse(
                    token,
                    session.userId(),
                    session.username(),
                    session.fullName(),
                    session.department(),
                    session.role().name(),
                    session.expiresAt()
            );
        }
    }

    public record UserProfileView(
            Long userId,
            String username,
            String fullName,
            String department,
            String role,
            boolean isTokenAuthenticated
    ) {
        public static UserProfileView of(MunicipalPrincipal principal) {
            return new UserProfileView(
                    principal.userId(),
                    principal.username(),
                    principal.fullName(),
                    principal.department(),
                    principal.role().name(),
                    principal.isTokenAuthenticated()
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        var result = authService.login(req.username(), req.password(), clientIp);
        return ResponseEntity.ok(AuthResponse.of(result.token(), result.session()));
    }

    @PostMapping("/logout")
    public ResponseEntity<java.util.Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) java.util.Map<String, String> body,
            HttpServletRequest request
    ) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (body != null && body.containsKey("token")) {
            token = body.get("token");
        }
        String clientIp = extractClientIp(request);
        authService.logout(token, clientIp);
        return ResponseEntity.ok(java.util.Map.of("message", "Logged out successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileView> me(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey
    ) {
        MunicipalPrincipal principal = authService.authenticate(authHeader, adminKey, jalmargKey);
        return ResponseEntity.ok(UserProfileView.of(principal));
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    @PostMapping("/change-password")
    public ResponseEntity<java.util.Map<String, String>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChangePasswordRequest req,
            HttpServletRequest request
    ) {
        MunicipalPrincipal principal = authService.authenticate(authHeader, null, null);
        if (!principal.isTokenAuthenticated() || principal.userId() == null || principal.userId() <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden: Password change requires token-based staff authentication.");
        }
        String clientIp = extractClientIp(request);
        authService.changePassword(principal.userId(), req.currentPassword(), req.newPassword(), clientIp);
        return ResponseEntity.ok(java.util.Map.of("message", "Password changed successfully. Please log in with your new password."));
    }

    private static String extractClientIp(HttpServletRequest req) {
        if (req == null) return "127.0.0.1";
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "127.0.0.1";
    }
}
