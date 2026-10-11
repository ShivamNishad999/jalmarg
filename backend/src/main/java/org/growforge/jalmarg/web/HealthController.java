package org.growforge.jalmarg.web;

import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production Health and Observability Endpoints for JalMarg.
 * 
 * Public Liveness (/api/health, /api/v1/health):
 * Provides minimal UP/DOWN status for load balancers and deployment scripts without
 * leaking database identity, port numbers, Flyway scripts, or internal infrastructure details.
 * 
 * Protected Operational Diagnostics (/api/v1/health/diagnostics, /api/health/diagnostics):
 * Requires authenticated administrative or operator credentials to inspect database readiness,
 * Flyway migration status, PostGIS target, and ML safety gate details.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;
    private final MunicipalAuthService authService;

    @Autowired
    public HealthController(DataSource dataSource, @Autowired(required = false) MunicipalAuthService authService) {
        this.dataSource = dataSource;
        this.authService = authService;
    }

    public HealthController(DataSource dataSource) {
        this(dataSource, null);
    }

    @GetMapping({"/health", "/v1/health"})
    public ResponseEntity<Map<String, Object>> getHealth(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey
    ) {
        boolean isAuthorized = checkAuthorizationSafely(authHeader, adminKey, jalmargKey);

        boolean dbOk = checkDatabaseConnectivity();
        if (!dbOk) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "DOWN");
            body.put("application", "jalmarg");
            body.put("timestamp", Instant.now().toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", "jalmarg");
        body.put("timestamp", Instant.now().toString());

        // Only include operational diagnostics if the caller provides authenticated admin credentials
        if (isAuthorized) {
            populateOperationalDiagnostics(body);
        }

        return ResponseEntity.ok(body);
    }

    @GetMapping({"/health/diagnostics", "/v1/health/diagnostics"})
    public ResponseEntity<Map<String, Object>> getOperationalDiagnostics(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey
    ) {
        if (authService != null) {
            authService.authenticate(authHeader, adminKey, jalmargKey);
        } else {
            // Fallback for standalone/mock unit testing when authService is not registered
            if ((adminKey == null || adminKey.isBlank()) &&
                (jalmargKey == null || jalmargKey.isBlank()) &&
                (authHeader == null || authHeader.isBlank())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized: Authentication required for operational diagnostics.");
            }
        }

        boolean dbOk = checkDatabaseConnectivity();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbOk ? "UP" : "DOWN");
        body.put("application", "jalmarg");
        body.put("timestamp", Instant.now().toString());

        populateOperationalDiagnostics(body);

        if (!dbOk) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private boolean checkDatabaseConnectivity() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private void populateOperationalDiagnostics(Map<String, Object> body) {
        Map<String, Object> dbInfo = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            dbInfo.put("status", "UP");
            dbInfo.put("target", "jalmarg_twin (isolated port 5433)");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
                if (rs.next()) {
                    Map<String, Object> flywayInfo = new LinkedHashMap<>();
                    flywayInfo.put("status", "UP");
                    flywayInfo.put("latestVersion", rs.getString("version"));
                    flywayInfo.put("latestScript", rs.getString("script"));
                    flywayInfo.put("success", rs.getBoolean("success"));
                    body.put("flyway", flywayInfo);
                }
            } catch (Exception ignored) {
                // Flyway table might differ or be disabled in certain test profiles
            }
        } catch (Exception e) {
            dbInfo.put("status", "DOWN");
        }
        body.put("database", dbInfo);

        Map<String, Object> mlGating = new LinkedHashMap<>();
        mlGating.put("supervisedTrainingStatus", "BLOCKED_UNVERIFIED_GROUND_TRUTH");
        mlGating.put("operationalRiskEngine", "HEURISTIC_ACTIVE");
        mlGating.put("cvAbstentionPolicy", "ACTIVE");
        body.put("mlGating", mlGating);
    }

    private boolean checkAuthorizationSafely(String authHeader, String adminKey, String jalmargKey) {
        if (authService == null) return false;
        try {
            MunicipalPrincipal principal = authService.authenticate(authHeader, adminKey, jalmargKey);
            return principal != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
