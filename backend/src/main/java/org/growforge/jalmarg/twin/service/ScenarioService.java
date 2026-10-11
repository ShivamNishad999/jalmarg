package org.growforge.jalmarg.twin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.growforge.jalmarg.model.Enums.RiskLevel;
import org.growforge.jalmarg.model.Enums.RiskStatus;
import org.growforge.jalmarg.twin.dto.TwinDtos.ScenarioRequest;
import org.growforge.jalmarg.twin.model.MunicipalZone;
import org.growforge.jalmarg.twin.model.RoadSegment;
import org.growforge.jalmarg.twin.model.ScenarioRun;
import org.growforge.jalmarg.twin.model.TwinEnums.TwinSnapshotType;
import org.growforge.jalmarg.twin.model.TwinSnapshot;
import org.growforge.jalmarg.twin.repo.MunicipalZoneRepository;
import org.growforge.jalmarg.twin.repo.RoadSegmentRepository;
import org.growforge.jalmarg.twin.repo.ScenarioRunRepository;
import org.growforge.jalmarg.twin.repo.TwinSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);
    public static final String SCENARIO_MODEL_VERSION = "JALMARG_WHATIF_V1.0_HEURISTIC";

    private final ScenarioRunRepository scenarioRepo;
    private final TwinSnapshotRepository snapshotRepo;
    private final MunicipalZoneRepository zoneRepo;
    private final RoadSegmentRepository roadRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ScenarioService(ScenarioRunRepository scenarioRepo,
                           TwinSnapshotRepository snapshotRepo,
                           MunicipalZoneRepository zoneRepo,
                           RoadSegmentRepository roadRepo) {
        this.scenarioRepo = scenarioRepo;
        this.snapshotRepo = snapshotRepo;
        this.zoneRepo = zoneRepo;
        this.roadRepo = roadRepo;
    }

    @Transactional
    public ScenarioRun runScenario(ScenarioRequest req, String createdBy) {
        Instant now = Instant.now();

        // 1. Resolve Target Zone
        MunicipalZone zone = null;
        if (req.targetZoneId() != null) {
            zone = zoneRepo.findById(req.targetZoneId()).orElse(null);
        }
        if (zone == null) {
            zone = zoneRepo.findAll().stream().findFirst().orElse(null);
        }
        String zoneName = zone != null ? zone.getName() : "Delhi NCR Regional Extent";

        // 2. Base Risk Calculation from Scenario Rainfall Inputs (Preserving established scoring)
        double rainTotal = Math.max(0.0, req.rainfallTotalMm());
        double rainIntensity = Math.max(0.0, req.rainfallIntensityMmPerHour());
        int duration = Math.max(1, Math.min(72, req.durationHours()));

        int rainPoints = 0;
        if (rainTotal >= 50.0 || rainIntensity >= 25.0) {
            rainPoints = 55;
        } else if (rainTotal >= 25.0 || rainIntensity >= 12.0) {
            rainPoints = 40;
        } else if (rainTotal >= 10.0 || rainIntensity >= 5.0) {
            rainPoints = 25;
        } else if (rainTotal > 0.0) {
            rainPoints = 12;
        }

        // Zone inherent susceptibility factor (default 20 points)
        int zoneSusceptibility = 20;

        // Baseline score (Dry weather assumption = 10)
        int baselineScore = 10;

        // 3. Drainage Capacity Mitigation Assumption (0 - 100%)
        double assumedDrainagePercent = req.assumedDrainageCapacityPercent() != null
                ? Math.max(0.0, Math.min(100.0, req.assumedDrainageCapacityPercent()))
                : 50.0; // Default 50% operational assumption if unmetered

        // Mitigation deduction: Up to 15 points reduction if drainage is 100% operational
        int drainageMitigationPoints = (int) Math.round((assumedDrainagePercent / 100.0) * 15.0);

        // 4. Closed Roads / Pump Availability Penalties
        List<String> closedRoads = req.closedRoadSegmentCodes() != null ? req.closedRoadSegmentCodes() : List.of();
        int roadClosurePenalty = Math.min(15, closedRoads.size() * 5);

        int rawScore = rainPoints + zoneSusceptibility - drainageMitigationPoints + roadClosurePenalty;
        int simulatedRiskScore = Math.max(5, Math.min(100, rawScore));
        int deltaRiskScore = simulatedRiskScore - baselineScore;

        // 5. Count Affected Critical Road Segments in target zone
        List<RoadSegment> zoneRoads = zone != null ? roadRepo.findByZoneId(zone.getId()) : roadRepo.findAll();
        int affectedRoadsCount = (simulatedRiskScore >= 50)
                ? (int) zoneRoads.stream().filter(RoadSegment::isUnderpass).count()
                : (simulatedRiskScore >= 25 ? 1 : 0);

        // 6. Detailed Factor Breakdown and Assumptions JSON
        String factorBreakdownJson = String.format(Locale.ROOT,
                "{\"simulationType\":\"WHAT_IF_SCENARIO\",\"rainfallTotalMm\":%.1f,\"intensityMmH\":%.1f,\"durationH\":%d,"
                + "\"rainPoints\":%d,\"zoneSusceptibilityPoints\":%d,\"assumedDrainagePercent\":%.1f,\"drainageMitigation\":%d,"
                + "\"closedRoadsCount\":%d,\"roadClosurePenalty\":%d,\"baselineScore\":%d,\"simulatedScore\":%d,\"delta\":%d}",
                rainTotal, rainIntensity, duration, rainPoints, zoneSusceptibility, assumedDrainagePercent,
                drainageMitigationPoints, closedRoads.size(), roadClosurePenalty, baselineScore, simulatedRiskScore, deltaRiskScore);

        String dataGapsJson = "{\"physicalDischargeCfd\":\"NOT_MODELED\",\"soilPercolationRate\":\"NOT_MODELED\","
                + "\"pipeSiltationLevel\":\"UNVERIFIED_ASSUMPTION\",\"hydrologyNote\":\"Outputs are decision-support heuristic indices, not hydraulic inundation depths.\"}";

        String roadClosuresJson = "[]";
        try {
            roadClosuresJson = mapper.writeValueAsString(closedRoads);
        } catch (Exception ignored) {}

        String resourcesJson = String.format(Locale.ROOT, "{\"availablePumps\":%d}",
                req.availablePumpCount() != null ? req.availablePumpCount() : 0);

        // 7. Persist ScenarioRun
        ScenarioRun run = new ScenarioRun(
                req.scenarioName() != null && !req.scenarioName().isBlank() ? req.scenarioName() : "Simulated Rainfall Event " + now.toString().substring(0, 16),
                "WHAT_IF_SIMULATION",
                createdBy != null ? createdBy : "AUTHORITY_PLANNER",
                rainTotal,
                rainIntensity,
                duration,
                zone != null ? zone.getId() : null,
                zoneName,
                assumedDrainagePercent,
                roadClosuresJson,
                resourcesJson,
                affectedRoadsCount,
                baselineScore,
                simulatedRiskScore,
                deltaRiskScore,
                factorBreakdownJson,
                dataGapsJson,
                SCENARIO_MODEL_VERSION,
                "COMPLETED"
        );

        ScenarioRun savedRun = scenarioRepo.save(run);

        // 8. Create and Persist an Isolated SIMULATED_SCENARIO Snapshot
        RiskLevel simLevel = simulatedRiskScore >= 76 ? RiskLevel.CRITICAL
                : (simulatedRiskScore >= 51 ? RiskLevel.HIGH
                : (simulatedRiskScore >= 26 ? RiskLevel.MODERATE : RiskLevel.LOW));

        TwinSnapshot simSnapshot = new TwinSnapshot(
                TwinSnapshotType.SIMULATED_SCENARIO,
                zone,
                zoneName,
                now,
                simulatedRiskScore,
                simLevel,
                RiskStatus.PREDICTED_RISK,
                0,
                0,
                rainTotal,
                SCENARIO_MODEL_VERSION,
                factorBreakdownJson,
                dataGapsJson,
                "User Scenario Simulation Inputs: " + rainTotal + "mm rain (" + assumedDrainagePercent + "% drainage)",
                "SIMULATION ONLY: Not a physical flood measurement.",
                "SIM_RUN_" + savedRun.getId()
        );
        snapshotRepo.save(simSnapshot);

        log.info("Completed What-If Scenario #{} ({}) -> simulatedScore={}, delta={}",
                savedRun.getId(), savedRun.getScenarioName(), simulatedRiskScore, deltaRiskScore);

        return savedRun;
    }

    @Transactional(readOnly = true)
    public List<ScenarioRun> getAllRuns() {
        return scenarioRepo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public ScenarioRun getRunById(Long id) {
        return scenarioRepo.findById(id).orElse(null);
    }
}
