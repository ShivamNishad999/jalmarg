package org.growforge.jalmarg;

import org.growforge.jalmarg.twin.dto.TwinDtos.MunicipalIncidentRequest;
import org.growforge.jalmarg.twin.dto.TwinDtos.ScenarioRequest;
import org.growforge.jalmarg.twin.model.AuditEvent;
import org.growforge.jalmarg.twin.model.ScenarioRun;
import org.growforge.jalmarg.twin.model.TwinSnapshot;
import org.growforge.jalmarg.twin.service.MunicipalIncidentService;
import org.growforge.jalmarg.twin.service.ScenarioService;
import org.growforge.jalmarg.twin.service.TwinStateService;
import org.growforge.jalmarg.twin.web.AuthorityIncidentController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class Phase20SecurityAndDigitalTwinTest {

    @Autowired
    private AuthorityIncidentController authorityController;

    @Autowired
    private MunicipalIncidentService incidentService;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private TwinStateService stateService;

    @Test
    void testUnauthorizedIncidentCreationRejected() {
        MunicipalIncidentRequest req = new MunicipalIncidentRequest(
                "Flooding at Minto Road", "Underpass submerged", "UNDERPASS_INUNDATION",
                "HIGH", 28.634, 77.226, "Minto Road Underpass", null, null
        );

        // Missing key -> 401 UNAUTHORIZED
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
                authorityController.createIncident(req, null, null, null, "Hacker", null)
        );
        assertEquals(401, ex1.getStatusCode().value());

        // Invalid key -> 403 FORBIDDEN
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
                authorityController.createIncident(req, "invalid-secret-key", null, null, "Hacker", null)
        );
        assertEquals(403, ex2.getStatusCode().value());

        // Backdoor "change-me" must be rejected -> 403 FORBIDDEN
        ResponseStatusException ex3 = assertThrows(ResponseStatusException.class, () ->
                authorityController.createIncident(req, "change-me", null, null, "Hacker", null)
        );
        assertEquals(403, ex3.getStatusCode().value());
    }

    @Test
    void testInputValidationOnIncidentCreation() {
        // Empty title
        MunicipalIncidentRequest blankTitleReq = new MunicipalIncidentRequest(
                "  ", "Underpass submerged", "UNDERPASS_INUNDATION",
                "HIGH", 28.634, 77.226, "Minto Road", null, null
        );
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
                authorityController.createIncident(blankTitleReq, "jalmarg-dev-admin-key-2026", null, null, "Admin", null)
        );
        assertEquals(400, ex1.getStatusCode().value());

        // Coordinates out of Delhi NCR bounds
        MunicipalIncidentRequest outOfBoundsReq = new MunicipalIncidentRequest(
                "Valid Title", "Description", "WATERLOGGING_CHOKE",
                "MODERATE", 19.0760, 72.8777, "Mumbai Area", null, null // Mumbai coordinates
        );
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
                authorityController.createIncident(outOfBoundsReq, "jalmarg-dev-admin-key-2026", null, null, "Admin", null)
        );
        assertEquals(400, ex2.getStatusCode().value());
    }

    @Test
    void testOperatorSanitizationAndAuditLogging() {
        MunicipalIncidentRequest req = new MunicipalIncidentRequest(
                "Submerged Traffic Junction", "Water 40cm deep", "WATERLOGGING_CHOKE",
                "MODERATE", 28.625, 77.215, "Connaught Place Outer", null, null
        );

        // Malicious operator string with CRLF injection attempt
        String dirtyOperator = "ADMIN_USER\r\nINJECTED_HEADER: true\t\"attacker\"";
        var view = authorityController.createIncident(req, "jalmarg-dev-admin-key-2026", null, null, dirtyOperator, null);

        assertNotNull(view);
        assertNotNull(view.incidentNumber());

        // Check that audit trail recorded clean sanitized operator
        List<AuditEvent> auditEvents = incidentService.getRecentAuditEvents();
        assertFalse(auditEvents.isEmpty());
        AuditEvent latest = auditEvents.get(0);
        assertFalse(latest.getActorId().contains("\r"));
        assertFalse(latest.getActorId().contains("\n"));
        assertFalse(latest.getActorId().contains("\""));
    }

    @Test
    void testWhatIfScenarioIsolationAndProvenance() {
        ScenarioRequest req = new ScenarioRequest(
                "Extreme Cloudburst 80mm", 80.0, 60.0, 2, null, 40.0, List.of("ROAD_MINTO_BRIDGE"), 2
        );

        ScenarioRun run = scenarioService.runScenario(req, "CHIEF_PLANNER");
        assertNotNull(run);
        assertEquals("WHAT_IF_SIMULATION", run.getRunType());
        assertTrue(run.getSimulatedRiskScore() > run.getBaselineRiskScore());
        assertEquals("SIMULATION / TEST DATA", run.getSimulationLabel());

        // Ensure data gaps disclaimer is explicitly present
        assertNotNull(run.getDataGapsJson());
        assertTrue(run.getDataGapsJson().contains("NOT_MODELED"));
        assertTrue(run.getDataGapsJson().contains("decision-support heuristic"));

        // Verify operational snapshot is not corrupted by simulation
        TwinSnapshot operational = stateService.getLatestOperationalSnapshot();
        assertNotEquals("SIMULATED_SCENARIO", operational.getSnapshotType().name());
    }
}
