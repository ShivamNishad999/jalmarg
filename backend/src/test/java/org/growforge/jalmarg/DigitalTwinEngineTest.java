package org.growforge.jalmarg;

import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.twin.dto.TwinDtos.IncidentAssignRequest;
import org.growforge.jalmarg.twin.dto.TwinDtos.IncidentStatusUpdateRequest;
import org.growforge.jalmarg.twin.dto.TwinDtos.MunicipalIncidentRequest;
import org.growforge.jalmarg.twin.dto.TwinDtos.ScenarioRequest;
import org.growforge.jalmarg.twin.model.*;
import org.growforge.jalmarg.twin.model.TwinEnums.*;
import org.growforge.jalmarg.twin.repo.*;
import org.growforge.jalmarg.twin.service.MunicipalIncidentService;
import org.growforge.jalmarg.twin.service.ScenarioService;
import org.growforge.jalmarg.twin.service.TwinSourceRegistryService;
import org.growforge.jalmarg.twin.service.TwinStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DigitalTwinEngineTest {

    @Autowired
    private MunicipalZoneRepository zoneRepo;

    @Autowired
    private RoadSegmentRepository roadRepo;

    @Autowired
    private DataSourceRegistryRepository sourceRepo;

    @Autowired
    private TwinSnapshotRepository snapshotRepo;

    @Autowired
    private ScenarioRunRepository scenarioRepo;

    @Autowired
    private MunicipalIncidentRepository incidentRepo;

    @Autowired
    private ResponseResourceRepository resourceRepo;

    @Autowired
    private AuditEventRepository auditRepo;

    @Autowired
    private TwinSourceRegistryService sourceService;

    @Autowired
    private TwinStateService stateService;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private MunicipalIncidentService incidentService;

    @BeforeEach
    void setup() {
        sourceService.seedDefaultSources();
        stateService.initFoundationData();
        incidentService.seedInitialResources();
    }

    @Test
    @DisplayName("Stage 1: Municipal Zones and Critical Road Corridors Seeded and Geometry Valid")
    void testMunicipalZonesAndRoadsFoundation() {
        List<MunicipalZone> zones = stateService.getAllZones();
        assertFalse(zones.isEmpty(), "Municipal zones must not be empty");
        assertTrue(zones.size() >= 5, "Must have at least 5 standard Delhi NCR zones");

        for (MunicipalZone z : zones) {
            assertTrue(z.getCentroidLat() >= 28.0 && z.getCentroidLat() <= 29.2, "Latitude must be within Delhi NCR");
            assertTrue(z.getCentroidLng() >= 76.5 && z.getCentroidLng() <= 77.8, "Longitude must be within Delhi NCR");
            assertEquals("UNAVAILABLE", z.getDrainageNetworkStatus(), "Unmetered drainage must be labeled UNAVAILABLE");
        }

        List<RoadSegment> roads = stateService.getAllRoadSegments();
        assertFalse(roads.isEmpty(), "Road segments must not be empty");
        assertTrue(roads.stream().anyMatch(RoadSegment::isUnderpass), "Underpass corridors must be indexed");
        assertTrue(roads.stream().anyMatch(r -> r.getRoadName().contains("Minto")), "Minto underpass must be present");
    }

    @Test
    @DisplayName("Stage 2: Authoritative Data Source Registry and Provenance Classifications")
    void testDataSourceRegistryProvenance() {
        List<DataSourceRegistry> sources = sourceService.getAllSources();
        assertFalse(sources.isEmpty());

        // Verify Open-Meteo classification
        DataSourceRegistry weatherSource = sources.stream()
                .filter(s -> s.getSourceCode().equals("SRC_OPEN_METEO"))
                .findFirst().orElse(null);
        assertNotNull(weatherSource);
        assertEquals(SourceClassification.PROVIDER_FORECAST, weatherSource.getSourceClassification(),
                "Open-Meteo must be classified as PROVIDER_FORECAST, never measured observation");

        // Verify Citizen Crowd classification
        DataSourceRegistry citizenSource = sources.stream()
                .filter(s -> s.getSourceCode().equals("SRC_CITIZEN_CROWD"))
                .findFirst().orElse(null);
        assertNotNull(citizenSource);
        assertEquals(SourceClassification.USER_REPORTED, citizenSource.getSourceClassification(),
                "Crowd reports must be classified as USER_REPORTED, never independently verified");

        // Verify Drainage status classification
        DataSourceRegistry drainageSource = sources.stream()
                .filter(s -> s.getSourceCode().equals("SRC_DELHI_DRAINAGE_STATUS"))
                .findFirst().orElse(null);
        assertNotNull(drainageSource);
        assertEquals(SourceClassification.UNAVAILABLE, drainageSource.getSourceClassification(),
                "Live drainage telemetry must be explicitly UNAVAILABLE");
    }

    @Test
    @DisplayName("Stage 3: Twin State Snapshot Engine and Current vs Simulated Segregation")
    void testTwinStateSnapshotCurrentVsSimulatedSegregation() {
        TwinSnapshot operational = stateService.generateOperationalSnapshot();
        assertNotNull(operational);
        assertEquals(TwinSnapshotType.CURRENT_OPERATIONAL, operational.getSnapshotType());
        assertNotNull(operational.getImmutableHash(), "Snapshot must contain immutable SHA-256 hash");
        assertTrue(operational.getMissingDataIndicatorsJson().contains("UNAVAILABLE"));

        // Create a simulated scenario
        ScenarioRequest req = new ScenarioRequest("Heavy Cloudburst Simulation", 45.0, 20.0, 2, null, 70.0, List.of("RD_MINTO_UNDERPASS"), 2);
        ScenarioRun run = scenarioService.runScenario(req, "TEST_PLANNER");

        // Verify simulated snapshot exists and is segregated
        List<TwinSnapshot> simulatedSnapshots = stateService.getSnapshotsByType(TwinSnapshotType.SIMULATED_SCENARIO);
        assertFalse(simulatedSnapshots.isEmpty());
        TwinSnapshot sim = simulatedSnapshots.get(0);
        assertEquals(TwinSnapshotType.SIMULATED_SCENARIO, sim.getSnapshotType());
        assertTrue(sim.getUncertaintyDisclaimer().contains("SIMULATION ONLY"));

        // Current operational snapshot must not be contaminated by scenario
        TwinSnapshot current = stateService.getLatestOperationalSnapshot();
        assertEquals(TwinSnapshotType.CURRENT_OPERATIONAL, current.getSnapshotType());
    }

    @Test
    @DisplayName("Stage 4: What-If Scenario Engine Output and Drainage Mitigation Delta")
    void testWhatIfScenarioMitigationAndDeltas() {
        // Run with 0% drainage mitigation
        ScenarioRequest reqNoDrainage = new ScenarioRequest("Storm No Drainage", 50.0, 25.0, 1, null, 0.0, List.of(), 0);
        ScenarioRun runNoDrainage = scenarioService.runScenario(reqNoDrainage, "OPERATOR_A");

        // Run with 100% drainage mitigation
        ScenarioRequest reqFullDrainage = new ScenarioRequest("Storm Full Drainage", 50.0, 25.0, 1, null, 100.0, List.of(), 0);
        ScenarioRun runFullDrainage = scenarioService.runScenario(reqFullDrainage, "OPERATOR_B");

        assertTrue(runNoDrainage.getSimulatedRiskScore() > runFullDrainage.getSimulatedRiskScore(),
                "100% drainage capacity assumption must produce lower simulated risk score than 0% drainage");
        assertEquals("SIMULATION / TEST DATA", runNoDrainage.getSimulationLabel());
        assertTrue(runNoDrainage.getDataGapsJson().contains("NOT_MODELED"));
    }

    @Test
    @DisplayName("Stage 5: Municipal Incident Lifecycle, Priority Ranking and Audit Trail")
    void testMunicipalIncidentLifecycleAndAuditTrail() {
        MunicipalZone zone = zoneRepo.findByZoneCode("ZONE_NDMC_CENTRAL").orElse(null);

        // 1. Create Incident
        MunicipalIncidentRequest createReq = new MunicipalIncidentRequest(
                "Waterlogging under Minto Bridge",
                "Water accumulating rapidly after cloudburst",
                "UNDERPASS_INUNDATION",
                "HIGH",
                28.6347,
                77.2263,
                "Minto Bridge Underpass",
                zone != null ? zone.getId() : null,
                null
        );

        MunicipalIncident incident = incidentService.createIncident(createReq, "DISPATCHER_01", "192.168.1.50");
        assertNotNull(incident.getId());
        assertEquals(IncidentStatus.NEW, incident.getStatus());
        assertTrue(incident.getPriorityScore() >= 75, "Underpass inundation with HIGH severity must have priority >= 75");

        // 2. Assign Incident to Response Resource
        ResponseResource pump = resourceRepo.findAll().stream()
                .filter(r -> r.getResourceType() == ResourceType.MOBILE_DEWATERING_PUMP)
                .findFirst().orElse(null);
        assertNotNull(pump);

        IncidentAssignRequest assignReq = new IncidentAssignRequest("PWD Drainage", "Central Dewatering Crew A", pump.getId());
        MunicipalIncident assigned = incidentService.assignIncident(incident.getId(), assignReq, "DISPATCHER_01", "192.168.1.50");
        assertEquals(IncidentStatus.ASSIGNED, assigned.getStatus());
        assertEquals("PWD Drainage", assigned.getAssignedDepartment());

        // Verify pump status updated to DEPLOYED
        ResponseResource updatedPump = resourceRepo.findById(pump.getId()).orElse(null);
        assertNotNull(updatedPump);
        assertEquals(ResourceStatus.DEPLOYED, updatedPump.getStatus());

        // 3. Update Status to IN_PROGRESS then RESOLVED
        incidentService.updateIncidentStatus(incident.getId(), new IncidentStatusUpdateRequest("IN_PROGRESS", "Pump deployed and running"), "CREW_LEAD", "10.0.0.1");
        MunicipalIncident resolved = incidentService.updateIncidentStatus(incident.getId(), new IncidentStatusUpdateRequest("RESOLVED", "Water cleared, traffic restored"), "CREW_LEAD", "10.0.0.1");
        assertEquals(IncidentStatus.RESOLVED, resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());
        assertEquals("Water cleared, traffic restored", resolved.getResolutionNotes());

        // 4. Verify Immutable Audit Trail
        List<AuditEvent> audits = incidentService.getRecentAuditEvents();
        assertFalse(audits.isEmpty());
        assertTrue(audits.stream().anyMatch(a -> a.getActionType().equals("INCIDENT_CREATE")));
        assertTrue(audits.stream().anyMatch(a -> a.getActionType().equals("INCIDENT_ASSIGN")));
        assertTrue(audits.stream().anyMatch(a -> a.getActionType().equals("INCIDENT_STATUS_CHANGE")));
    }

    @Autowired
    private org.growforge.jalmarg.twin.web.AuthorityIncidentController authorityController;

    @Autowired
    private org.growforge.jalmarg.service.ReportService reportService;

    @Test
    @DisplayName("Stage 5 / Hardening: Reject Unauthorized Incident Mutation (401/403)")
    void testUnauthorizedIncidentMutationRejection() {
        MunicipalIncidentRequest req = new MunicipalIncidentRequest(
                "Unauthorized Report",
                "Testing security rejection",
                "SURFACE_FLOODING",
                "HIGH",
                28.62,
                77.21,
                "Connaught Place",
                null,
                null
        );

        // 1. Missing Key should throw UNAUTHORIZED (401)
        org.springframework.web.server.ResponseStatusException exNoKey = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> authorityController.createIncident(req, null, null, null, null, null)
        );
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exNoKey.getStatusCode());

        // 2. Invalid Key should throw FORBIDDEN (403)
        org.springframework.web.server.ResponseStatusException exBadKey = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> authorityController.createIncident(req, "completely-wrong-key", null, null, null, null)
        );
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exBadKey.getStatusCode());

        // 3. Valid Key should succeed
        var created = authorityController.createIncident(req, "jalmarg-dev-admin-key-2026", null, null, "test-officer-99", null);
        assertNotNull(created);
        assertNotNull(created.id());
    }

    @Test
    @DisplayName("Stage 4 / Hardening: Scenario Runs Never Modify Live Reports or Live Operational State")
    void testScenarioEngineDoesNotModifyLiveRiskOrCitizenReports() {
        int initialActiveReports = reportService.active(true).size();
        TwinSnapshot initialOperationalSnapshot = stateService.getLatestOperationalSnapshot();
        int initialRisk = initialOperationalSnapshot.getOverallRiskScore();

        // Run extreme scenario
        ScenarioRequest extremeReq = new ScenarioRequest(
                "Extreme Cloudburst 150mm",
                150.0,
                75.0,
                6,
                null,
                10.0,
                List.of("RD_MINTO_UNDERPASS", "RD_ZAKHIRA_UNDERPASS"),
                5
        );
        ScenarioRun run = scenarioService.runScenario(extremeReq, "DISASTER_MGMT_PLANNER");
        assertNotNull(run);
        assertEquals("SIMULATION / TEST DATA", run.getSimulationLabel());

        // Verify active reports count is completely unaffected
        assertEquals(initialActiveReports, reportService.active(true).size(),
                "Scenario simulation must NEVER create, modify or delete live citizen reports");

        // Verify latest operational snapshot is completely unaffected
        TwinSnapshot postRunOperationalSnapshot = stateService.getLatestOperationalSnapshot();
        assertEquals(initialRisk, postRunOperationalSnapshot.getOverallRiskScore(),
                "Scenario simulation must NEVER alter live operational risk score");
        assertEquals(TwinSnapshotType.CURRENT_OPERATIONAL, postRunOperationalSnapshot.getSnapshotType());
    }
}
