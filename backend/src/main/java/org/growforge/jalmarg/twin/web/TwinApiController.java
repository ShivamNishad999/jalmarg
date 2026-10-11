package org.growforge.jalmarg.twin.web;

import org.growforge.jalmarg.twin.dto.TwinDtos.*;
import org.growforge.jalmarg.twin.model.ScenarioRun;
import org.growforge.jalmarg.twin.model.TwinEnums.TwinSnapshotType;
import org.growforge.jalmarg.twin.model.TwinSnapshot;
import org.growforge.jalmarg.twin.service.ScenarioService;
import org.growforge.jalmarg.twin.service.TwinSourceRegistryService;
import org.growforge.jalmarg.twin.service.TwinStateService;
import org.growforge.jalmarg.twin.model.AuditEvent;
import org.growforge.jalmarg.twin.model.MunicipalUser.Role;
import org.growforge.jalmarg.twin.repo.AuditEventRepository;
import org.growforge.jalmarg.twin.service.MunicipalAuthService;
import org.growforge.jalmarg.twin.service.MunicipalAuthService.MunicipalPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/twin")
public class TwinApiController {

    private final TwinStateService stateService;
    private final TwinSourceRegistryService sourceService;
    private final ScenarioService scenarioService;
    private final MunicipalAuthService authService;
    private final AuditEventRepository auditRepo;

    public TwinApiController(TwinStateService stateService,
                             TwinSourceRegistryService sourceService,
                             ScenarioService scenarioService,
                             MunicipalAuthService authService,
                             AuditEventRepository auditRepo) {
        this.stateService = stateService;
        this.sourceService = sourceService;
        this.scenarioService = scenarioService;
        this.authService = authService;
        this.auditRepo = auditRepo;
    }

    @GetMapping("/status")
    public TwinStatusView getSystemStatus() {
        TwinSnapshot latest = stateService.getLatestOperationalSnapshot();
        return new TwinStatusView(
                "OPERATIONAL",
                stateService.getAllZones().size(),
                stateService.getAllRoadSegments().size(),
                stateService.getAllDrainageAssets().size(),
                sourceService.getAllSources().size(),
                latest.getActiveIncidentCount(),
                stateService.getSnapshotsByType(TwinSnapshotType.CURRENT_OPERATIONAL).size(),
                latest.getSnapshotTimestamp(),
                "Real-time municipal stormwater pipe and pump discharge telemetry is currently UNAVAILABLE."
        );
    }

    @GetMapping("/zones")
    public List<MunicipalZoneView> getZones() {
        return stateService.getAllZones().stream().map(MunicipalZoneView::of).toList();
    }

    @GetMapping("/roads")
    public List<RoadSegmentView> getRoadSegments() {
        return stateService.getAllRoadSegments().stream().map(RoadSegmentView::of).toList();
    }

    @GetMapping("/drainage")
    public List<DrainageAssetView> getDrainageAssets() {
        return stateService.getAllDrainageAssets().stream().map(DrainageAssetView::of).toList();
    }

    @GetMapping("/sources")
    public List<DataSourceView> getDataSources() {
        return sourceService.getAllSources().stream().map(DataSourceView::of).toList();
    }

    @GetMapping("/snapshots")
    public List<TwinSnapshotView> getSnapshots(@RequestParam(defaultValue = "CURRENT_OPERATIONAL") String type) {
        TwinSnapshotType snapshotType;
        try {
            snapshotType = TwinSnapshotType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            snapshotType = TwinSnapshotType.CURRENT_OPERATIONAL;
        }
        return stateService.getSnapshotsByType(snapshotType).stream().map(TwinSnapshotView::of).toList();
    }

    @GetMapping("/snapshots/{id}")
    public ResponseEntity<TwinSnapshotView> getSnapshotById(@PathVariable Long id) {
        TwinSnapshot s = stateService.getSnapshotById(id);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(TwinSnapshotView.of(s));
    }

    @PostMapping("/scenarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioRunView runScenario(
            @RequestBody ScenarioRequest req,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestHeader(value = "X-Jalmarg-Key", required = false) String jalmargKey,
            @RequestHeader(value = "X-Operator-Id", required = false) String operatorId,
            jakarta.servlet.http.HttpServletRequest request
    ) {
        MunicipalPrincipal principal = authService.authenticate(authHeader, adminKey, jalmargKey);
        authService.enforceRole(principal, Role.MUNICIPAL_OPERATOR);
        String operator = principal.isTokenAuthenticated() ? principal.username() : (operatorId != null && !operatorId.isBlank() ? operatorId : principal.username());
        String clientIp = extractClientIp(request);
        ScenarioRun run = scenarioService.runScenario(req, operator);
        if (auditRepo != null) {
            auditRepo.save(new AuditEvent(
                    operator,
                    principal.role().name(),
                    "SCENARIO_RUN",
                    "ScenarioRun",
                    run.getId().toString(),
                    clientIp,
                    String.format(java.util.Locale.ROOT, "{\"scenarioName\":\"%s\",\"simulatedScore\":%d,\"delta\":%d}",
                            run.getScenarioName() != null ? run.getScenarioName().replace("\"", "\\\"") : "",
                            run.getSimulatedRiskScore(), run.getDeltaRiskScore())
            ));
        }
        return ScenarioRunView.of(run);
    }

    @GetMapping("/scenarios")
    public List<ScenarioRunView> getScenarioRuns() {
        return scenarioService.getAllRuns().stream().map(ScenarioRunView::of).toList();
    }

    @GetMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioRunView> getScenarioById(@PathVariable Long id) {
        ScenarioRun run = scenarioService.getRunById(id);
        if (run == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ScenarioRunView.of(run));
    }

    private static String extractClientIp(jakarta.servlet.http.HttpServletRequest req) {
        if (req == null) return "127.0.0.1";
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "127.0.0.1";
    }
}
