package org.growforge.jalmarg;

import org.growforge.jalmarg.twin.dto.TwinDtos.*;
import org.growforge.jalmarg.twin.model.DrainageAsset;
import org.growforge.jalmarg.twin.model.MunicipalZone;
import org.growforge.jalmarg.twin.model.RoadSegment;
import org.growforge.jalmarg.twin.service.TwinSourceRegistryService;
import org.growforge.jalmarg.twin.service.TwinStateService;
import org.growforge.jalmarg.twin.web.TwinApiController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class Phase21VerifiedGeospatialIntegrationTest {

    @Autowired
    private TwinStateService stateService;

    @Autowired
    private TwinSourceRegistryService sourceService;

    @Autowired
    private TwinApiController twinApiController;

    @BeforeEach
    void setUp() {
        sourceService.seedDefaultSources();
        stateService.initFoundationData();
    }

    @Test
    @DisplayName("Phase 21: Verify Municipal Zones Geographic Coverage and Bounding Box Constraints")
    void testMunicipalZonesCoverage() {
        List<MunicipalZone> zones = stateService.getAllZones();
        assertFalse(zones.isEmpty(), "Municipal zones must not be empty");
        assertTrue(zones.size() >= 5, "Must have comprehensive Delhi NCR municipal zones");

        for (MunicipalZone z : zones) {
            assertNotNull(z.getZoneCode());
            assertNotNull(z.getName());
            assertNotNull(z.getMunicipalityName());
            assertTrue(z.getCentroidLat() >= 28.0 && z.getCentroidLat() <= 29.2, "Centroid latitude must be within Delhi NCR: " + z.getZoneCode());
            assertTrue(z.getCentroidLng() >= 76.5 && z.getCentroidLng() <= 77.8, "Centroid longitude must be within Delhi NCR: " + z.getZoneCode());
            assertNotNull(z.getBoundaryType());
            if (z.getBoundaryGeoJson() != null) {
                assertTrue(z.getBoundaryGeoJson().contains("Polygon"), "GeoJSON must be a polygon");
            }
        }
    }

    @Test
    @DisplayName("Phase 21: Verify Critical Transportation and Underpass Road Segments")
    void testRoadSegmentsAndUnderpasses() {
        List<RoadSegment> roads = stateService.getAllRoadSegments();
        assertFalse(roads.isEmpty(), "Road segments must not be empty");

        boolean hasMinto = roads.stream().anyMatch(r -> r.getSegmentCode().contains("MINTO") && r.isUnderpass());
        boolean hasTilak = roads.stream().anyMatch(r -> r.getSegmentCode().contains("TILAK") && r.isUnderpass());
        boolean hasAiims = roads.stream().anyMatch(r -> r.getSegmentCode().contains("AIIMS") && r.isUnderpass());

        assertTrue(hasMinto, "Minto Road Underpass must be indexed");
        assertTrue(hasTilak, "Tilak Bridge Underpass must be indexed");
        assertTrue(hasAiims, "AIIMS Underpass must be indexed");

        for (RoadSegment r : roads) {
            assertTrue(r.getStartLat() >= 28.0 && r.getStartLat() <= 29.2, "Road start lat in Delhi NCR");
            assertTrue(r.getEndLat() >= 28.0 && r.getEndLat() <= 29.2, "Road end lat in Delhi NCR");
            assertTrue(r.getLengthMeters() > 0, "Road length must be positive");
            if (r.isUnderpass()) {
                assertNotNull(r.getElevationMinMeters(), "Underpass must have minimum DEM elevation");
            }
        }
    }

    @Test
    @DisplayName("Phase 21: Verify Stormwater Drainage Infrastructure and Telemetry Transparency")
    void testDrainageAssetsAndTelemetryStatus() {
        List<DrainageAsset> drains = stateService.getAllDrainageAssets();
        assertFalse(drains.isEmpty(), "Drainage assets must be seeded");

        for (DrainageAsset d : drains) {
            assertNotNull(d.getAssetCode());
            assertNotNull(d.getAssetType());
            assertNotNull(d.getName());
            assertTrue(d.getLatitude() >= 28.0 && d.getLatitude() <= 29.2, "Drain latitude within Delhi NCR");
            assertTrue(d.getLongitude() >= 76.5 && d.getLongitude() <= 77.8, "Drain longitude within Delhi NCR");
            // Transparently reflect unmetered telemetry status
            assertFalse(d.isTelemetryConnected(), "Telemetry should truthfully reflect unmetered status unless verified IoT connected");
        }
    }

    @Test
    @DisplayName("Phase 21: Verify Twin API Controller Endpoints for Zones, Roads, Drainage, and Status")
    void testTwinApiControllerEndpoints() {
        TwinStatusView status = twinApiController.getSystemStatus();
        assertNotNull(status);
        assertEquals("OPERATIONAL", status.systemStatus());
        assertTrue(status.totalZonesCount() >= 5);
        assertTrue(status.totalRoadSegmentsCount() >= 5);
        assertTrue(status.totalDrainageAssetsCount() >= 3);
        assertTrue(status.totalRegisteredSourcesCount() >= 6);

        List<MunicipalZoneView> zoneViews = twinApiController.getZones();
        assertFalse(zoneViews.isEmpty());

        List<RoadSegmentView> roadViews = twinApiController.getRoadSegments();
        assertFalse(roadViews.isEmpty());

        List<DrainageAssetView> drainViews = twinApiController.getDrainageAssets();
        assertFalse(drainViews.isEmpty());

        List<DataSourceView> sourceViews = twinApiController.getDataSources();
        assertFalse(sourceViews.isEmpty());
    }
}
