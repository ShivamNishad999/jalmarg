package org.growforge.jalmarg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostGisIntegrationTest {

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify Database Engine and Spatial Support Readiness")
    void testDatabaseEngineAndSpatialReadiness() {
        assertNotNull(jdbcTemplate, "JdbcTemplate must be configured");

        String dbProduct = jdbcTemplate.execute((java.sql.Connection conn) -> conn.getMetaData().getDatabaseProductName());
        assertNotNull(dbProduct);

        if ("PostgreSQL".equalsIgnoreCase(dbProduct)) {
            // Running against live PostgreSQL / PostGIS
            String version = jdbcTemplate.queryForObject("SELECT version();", String.class);
            assertNotNull(version);
            assertTrue(version.contains("PostgreSQL"), "Must be PostgreSQL engine");

            String postgisVersion = jdbcTemplate.queryForObject("SELECT PostGIS_Full_Version();", String.class);
            assertNotNull(postgisVersion);
            assertTrue(postgisVersion.contains("POSTGIS="), "PostGIS extension must be active");

            // Execute spatial calculation in SRID 4326
            String bufferWkt = jdbcTemplate.queryForObject(
                    "SELECT ST_AsText(ST_Buffer(ST_SetSRID(ST_Point(77.2150, 28.6250), 4326)::geography, 100));",
                    String.class
            );
            assertNotNull(bufferWkt);
            assertTrue(bufferWkt.startsWith("POLYGON"), "Spatial buffer must return valid WKT Polygon");
        } else {
            // Running in local H2 development mode
            assertTrue(dbProduct.contains("H2"), "Default development profile must use H2");
        }
    }
}
