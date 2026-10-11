package org.growforge.jalmarg.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {

    private static class StubDataSource implements DataSource {
        private final Connection connection;
        private final SQLException throwOnConnect;

        StubDataSource(Connection connection) {
            this.connection = connection;
            this.throwOnConnect = null;
        }

        StubDataSource(SQLException throwOnConnect) {
            this.connection = null;
            this.throwOnConnect = throwOnConnect;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (throwOnConnect != null) throw throwOnConnect;
            return connection;
        }

        @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @Test
    @DisplayName("Health endpoint returns UP when database responds")
    void testHealthEndpointReturnsUp() {
        ResultSet rs1 = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) return true;
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );

        Statement stmt = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) return rs1;
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );

        Connection conn = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) return stmt;
                    if ("close".equals(method.getName())) return null;
                    return null;
                }
        );

        DataSource ds = new StubDataSource(conn);
        HealthController controller = new HealthController(ds);

        // 1. Unauthenticated public request returns minimal liveness without internal details
        ResponseEntity<Map<String, Object>> publicResponse = controller.getHealth(null, null, null);
        assertEquals(HttpStatus.OK, publicResponse.getStatusCode());
        assertNotNull(publicResponse.getBody());
        assertEquals("UP", publicResponse.getBody().get("status"));
        assertEquals("jalmarg", publicResponse.getBody().get("application"));
        assertNotNull(publicResponse.getBody().get("timestamp"));
        // Ensure no internal details are disclosed publicly
        assertNull(publicResponse.getBody().get("database"), "Public liveness must not leak database internals");
        assertNull(publicResponse.getBody().get("flyway"), "Public liveness must not leak Flyway scripts");
        assertNull(publicResponse.getBody().get("mlGating"), "Public liveness must not leak ML gate details");

        // 2. Unauthenticated request to protected diagnostics throws 401 Unauthorized
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                controller.getOperationalDiagnostics(null, null, null));

        // 3. Authenticated request to diagnostics returns full operational details
        ResponseEntity<Map<String, Object>> diagResponse = controller.getOperationalDiagnostics(null, "admin-test-key", null);
        assertEquals(HttpStatus.OK, diagResponse.getStatusCode());
        assertNotNull(diagResponse.getBody());
        assertEquals("UP", diagResponse.getBody().get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) diagResponse.getBody().get("database");
        assertNotNull(db);
        assertEquals("UP", db.get("status"));
        assertTrue(((String) db.get("target")).contains("5433"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mlGating = (Map<String, Object>) diagResponse.getBody().get("mlGating");
        assertNotNull(mlGating);
        assertEquals("BLOCKED_UNVERIFIED_GROUND_TRUTH", mlGating.get("supervisedTrainingStatus"));
    }

    @Test
    @DisplayName("Health endpoint returns 503 SERVICE_UNAVAILABLE when database fails without leaking exception traces")
    void testHealthEndpointReturnsDownOnDbFailure() {
        DataSource ds = new StubDataSource(new SQLException("Internal connection refused to db cluster"));

        HealthController controller = new HealthController(ds);
        ResponseEntity<Map<String, Object>> response = controller.getHealth(null, null, null);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("jalmarg", response.getBody().get("application"));
        // Verify exception trace is not leaked
        assertNull(response.getBody().get("error"), "Public failure must not leak exception trace");
        assertNull(response.getBody().get("database"), "Public failure must not leak database internals");
    }
}
