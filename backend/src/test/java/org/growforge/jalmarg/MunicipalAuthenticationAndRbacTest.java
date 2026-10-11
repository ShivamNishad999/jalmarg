package org.growforge.jalmarg;

import org.growforge.jalmarg.twin.model.MunicipalUser;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.repo.AuditEventRepository;
import org.growforge.jalmarg.twin.repo.MunicipalUserRepository;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.ActiveSession;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.LoginResult;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MunicipalAuthenticationAndRbacTest {

    private MunicipalUserRepository userRepo;
    private AuditEventRepository auditRepo;
    private MunicipalAuthService authService;

    @BeforeEach
    void setUp() {
        userRepo = Mockito.mock(MunicipalUserRepository.class);
        auditRepo = Mockito.mock(AuditEventRepository.class);
        authService = new MunicipalAuthService(userRepo, auditRepo, "test-admin-key-2026");

        String salt = "salt_test_01";
        String hash = MunicipalAuthService.hashPassword("CorrectPass#123", salt);
        MunicipalUser operator = new MunicipalUser("operator_delhi", hash, salt, "Officer Kumar",
                "kumar@delhi.gov.in", "PWD Drainage", Role.MUNICIPAL_OPERATOR);
        operator.setId(10L);

        when(userRepo.findByUsernameIgnoreCase("operator_delhi")).thenReturn(Optional.of(operator));
        when(userRepo.findByUsernameIgnoreCase("unknown_user")).thenReturn(Optional.empty());
        when(userRepo.save(any(MunicipalUser.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Valid operator login succeeds and returns active session with token")
    void testSuccessfulLogin() {
        LoginResult result = authService.login("operator_delhi", "CorrectPass#123", "127.0.0.1");
        assertNotNull(result);
        assertNotNull(result.token());
        assertTrue(result.token().startsWith("jm_sess_"));
        assertEquals("operator_delhi", result.session().username());
        assertEquals(Role.MUNICIPAL_OPERATOR, result.session().role());
        assertEquals("PWD Drainage", result.session().department());
    }

    @Test
    @DisplayName("Invalid password rejects login with 401 Unauthorized")
    void testInvalidPasswordRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.login("operator_delhi", "WrongPassword#999", "127.0.0.1"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Unknown user rejects login with 401 Unauthorized")
    void testUnknownUserRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.login("unknown_user", "AnyPassword#123", "127.0.0.1"));
        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Brute-force protection triggers 429 Too Many Requests after 5 failed attempts")
    void testBruteForceRateLimiting() {
        String testIp = "192.168.1.105";
        for (int i = 0; i < 5; i++) {
            final int attempt = i;
            assertThrows(ResponseStatusException.class, () ->
                    authService.login("operator_delhi", "WrongPass" + attempt, testIp));
        }

        // 6th attempt should be blocked by rate limiter
        ResponseStatusException rateLimitEx = assertThrows(ResponseStatusException.class, () ->
                authService.login("operator_delhi", "CorrectPass#123", testIp));
        assertEquals(429, rateLimitEx.getStatusCode().value());
    }

    @Test
    @DisplayName("Role enforcement verifies hierarchy correctly")
    void testRoleHierarchyEnforcement() {
        MunicipalPrincipal viewer = new MunicipalPrincipal(1L, "v1", "Viewer", "Traffic", Role.MUNICIPAL_VIEWER, true);
        MunicipalPrincipal operator = new MunicipalPrincipal(2L, "o1", "Operator", "PWD", Role.MUNICIPAL_OPERATOR, true);
        MunicipalPrincipal admin = new MunicipalPrincipal(3L, "a1", "Admin", "NDMC", Role.MUNICIPAL_ADMIN, true);

        // Viewer checks
        assertTrue(viewer.hasRole(Role.MUNICIPAL_VIEWER));
        assertFalse(viewer.hasRole(Role.MUNICIPAL_OPERATOR));
        assertFalse(viewer.hasRole(Role.MUNICIPAL_ADMIN));

        // Operator checks
        assertTrue(operator.hasRole(Role.MUNICIPAL_VIEWER));
        assertTrue(operator.hasRole(Role.MUNICIPAL_OPERATOR));
        assertFalse(operator.hasRole(Role.MUNICIPAL_ADMIN));

        // Admin checks
        assertTrue(admin.hasRole(Role.MUNICIPAL_VIEWER));
        assertTrue(admin.hasRole(Role.MUNICIPAL_OPERATOR));
        assertTrue(admin.hasRole(Role.MUNICIPAL_ADMIN));

        // Enforce role throws 403 Forbidden for insufficient permissions
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                authService.enforceRole(viewer, Role.MUNICIPAL_OPERATOR));
        assertEquals(403, ex.getStatusCode().value());

        // Does not throw for permitted roles
        assertDoesNotThrow(() -> authService.enforceRole(operator, Role.MUNICIPAL_OPERATOR));
        assertDoesNotThrow(() -> authService.enforceRole(admin, Role.MUNICIPAL_OPERATOR));
    }

    @Test
    @DisplayName("Logout invalidates active session token")
    void testLogoutInvalidation() {
        LoginResult result = authService.login("operator_delhi", "CorrectPass#123", "127.0.0.1");
        String token = result.token();

        // Authenticate succeeds
        MunicipalPrincipal principal = authService.authenticate(token, null, null);
        assertNotNull(principal);
        assertEquals("operator_delhi", principal.username());

        // Perform logout
        authService.logout(token, "127.0.0.1");

        // Authenticate with logged-out token throws 401 Unauthorized
        assertThrows(ResponseStatusException.class, () -> authService.authenticate(token, null, null));
    }

    @Test
    @DisplayName("Legacy SHA-256 credentials transparently upgrade to BCrypt upon successful login")
    void testLegacyPasswordTransparentMigrationToBcrypt() {
        String legacySalt = "salt_legacy_99";
        String legacyHash = MunicipalAuthService.hashPassword("LegacySecret#2026", legacySalt);
        MunicipalUser legacyUser = new MunicipalUser("legacy_officer", legacyHash, legacySalt, "Legacy Officer",
                "legacy@delhi.gov.in", "NDMC Drainage", Role.MUNICIPAL_OPERATOR);
        legacyUser.setId(42L);

        when(userRepo.findByUsernameIgnoreCase("legacy_officer")).thenReturn(Optional.of(legacyUser));

        LoginResult result = authService.login("legacy_officer", "LegacySecret#2026", "10.0.0.1");
        assertNotNull(result);
        assertNotNull(result.token());

        // Verify the user object was upgraded to BCrypt
        assertTrue(legacyUser.getPasswordHash().startsWith("$2a$12$") || legacyUser.getPasswordHash().startsWith("$2b$12$"),
                "Password hash must be upgraded to BCrypt (cost 12): " + legacyUser.getPasswordHash());
        assertEquals("bcrypt", legacyUser.getSalt());
    }

    @Test
    @DisplayName("Pre-existing BCrypt credentials authenticate directly")
    void testBcryptUserLoginDirect() {
        String bcryptHash = authService.encodePassword("ModernSecure#2026");
        MunicipalUser bcryptUser = new MunicipalUser("modern_officer", bcryptHash, "bcrypt", "Modern Officer",
                "modern@delhi.gov.in", "Delhi Jal Board", Role.MUNICIPAL_ADMIN);
        bcryptUser.setId(55L);

        when(userRepo.findByUsernameIgnoreCase("modern_officer")).thenReturn(Optional.of(bcryptUser));

        LoginResult result = authService.login("modern_officer", "ModernSecure#2026", "10.0.0.2");
        assertNotNull(result);
        assertEquals("modern_officer", result.session().username());
        assertEquals(Role.MUNICIPAL_ADMIN, result.session().role());
    }

    @Test
    @DisplayName("Password change verifies current credentials, enforces complexity, upgrades to BCrypt, and invalidates sessions")
    void testChangePasswordLifecycle() {
        MunicipalUser user = new MunicipalUser("change_tester", authService.encodePassword("InitialPass#123"), "bcrypt",
                "Tester", "test@delhi.gov.in", "PWD", Role.MUNICIPAL_OPERATOR);
        user.setId(77L);

        when(userRepo.findById(77L)).thenReturn(Optional.of(user));
        when(userRepo.findByUsernameIgnoreCase("change_tester")).thenReturn(Optional.of(user));

        // Create an active session
        LoginResult loginResult = authService.login("change_tester", "InitialPass#123", "127.0.0.1");
        String activeToken = loginResult.token();
        assertNotNull(authService.authenticate(activeToken, null, null));

        // Attempt change with wrong current password -> 401
        assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(77L, "WrongCurrent#999", "NewStrongPass#2026", "127.0.0.1"));

        // Attempt change with weak new password (no special char) -> 400
        assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(77L, "InitialPass#123", "NoSpecial1234", "127.0.0.1"));

        // Attempt change with identical new password -> 400
        assertThrows(ResponseStatusException.class, () ->
                authService.changePassword(77L, "InitialPass#123", "InitialPass#123", "127.0.0.1"));

        // Successful password change
        assertDoesNotThrow(() ->
                authService.changePassword(77L, "InitialPass#123", "NewStrongPass#2026", "127.0.0.1"));

        // Previous session must now be invalidated
        assertThrows(ResponseStatusException.class, () ->
                authService.authenticate(activeToken, null, null));

        // Verify that new password can be used to log in
        LoginResult reLogin = authService.login("change_tester", "NewStrongPass#2026", "127.0.0.1");
        assertNotNull(reLogin);
        assertNotNull(reLogin.token());
    }
}
