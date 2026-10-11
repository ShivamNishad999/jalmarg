package org.growforge.jalmarg;

import org.growforge.jalmarg.calibration.HistoricalCalibrationService;
import org.growforge.jalmarg.calibration.HistoricalFloodIncident;
import org.growforge.jalmarg.dto.Dtos.*;
import org.growforge.jalmarg.model.Enums.*;
import org.growforge.jalmarg.model.FloodRiskAssessment;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.FloodRiskAssessmentRepository;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.growforge.jalmarg.repo.ReportRepository;
import org.growforge.jalmarg.service.*;
import org.growforge.jalmarg.twin.dto.TwinDtos.ScenarioRequest;
import org.growforge.jalmarg.twin.model.ScenarioRun;
import org.growforge.jalmarg.twin.model.TwinEnums.TwinSnapshotType;
import org.growforge.jalmarg.twin.service.ScenarioService;
import org.growforge.jalmarg.twin.service.TwinStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 22: Comprehensive Flood Intelligence, Risk Calibration Feasibility,
 * and Explainable Safer Routing Test Suite.
 */
@SpringBootTest
class Phase22CalibrationAndIntelligenceTest {

    @Autowired
    private FloodRiskAssessmentService riskService;

    @Autowired
    private RouteService routeService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportRepository reportRepo;

    @Autowired
    private HotspotRepository hotspotRepo;

    @Autowired
    private FloodRiskAssessmentRepository riskRepo;

    @Autowired
    private HistoricalCalibrationService calibrationService;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private TwinStateService twinStateService;

    @Autowired
    private CopernicusDemService demService;

    // Test coordinates in an unseeded area with zero citizen reports (Rohini Sector 25)
    private static final double ZERO_REPORT_LAT = 28.7350;
    private static final double ZERO_REPORT_LNG = 77.0950;

    @BeforeEach
    void setUp() {
        twinStateService.initFoundationData();
    }

    @Test
    @DisplayName("Scenario 1: Zero Reports + Heavy Rainfall -> PREDICTED_RISK (Elevated, Not Observed Flooding)")
    void testZeroReportsHeavyRainfall() {
        WeatherService customWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                return new RainForecast(40.0, 18.0, "VERY_HEAVY");
            }
        };

        FloodRiskAssessmentService customRiskService = new FloodRiskAssessmentService(
                customWeather, reportService, hotspotRepo, riskRepo,
                new NasaImergService(false, "", ""), demService, 60);

        FloodRiskAssessment assessment = customRiskService.assess(ZERO_REPORT_LAT, ZERO_REPORT_LNG, "North Delhi", null, "Rohini Sector 25");

        assertNotNull(assessment);
        assertEquals(0, assessment.getActiveReportCount(), "Must have zero ground reports");
        assertEquals(RiskStatus.PREDICTED_RISK, assessment.getStatus(), "Must be PREDICTED_RISK");
        assertNotEquals(RiskStatus.OBSERVED_FLOODING, assessment.getStatus(), "Must not claim observed flooding");
        assertTrue(assessment.getRiskScore() >= 55, "Heavy rainfall must produce elevated risk score");
        assertTrue(assessment.getRiskLevel() == RiskLevel.HIGH || assessment.getRiskLevel() == RiskLevel.CRITICAL);
        assertEquals(0.70, assessment.getConfidence(), 0.01, "Predicted confidence decoupled from score");
        assertTrue(assessment.getSummaryEn().contains("PREDICTED FLOOD RISK"));
    }

    @Test
    @DisplayName("Scenario 2: Zero Reports + No Available Forecast -> UNKNOWN (Never Fabricates Rain)")
    void testZeroReportsNoForecast() {
        WeatherService failingWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                throw new RuntimeException("Weather API timeout");
            }
        };

        FloodRiskAssessmentService customRiskService = new FloodRiskAssessmentService(
                failingWeather, reportService, hotspotRepo, riskRepo,
                new NasaImergService(false, "", ""), demService, 60);

        FloodRiskAssessment assessment = customRiskService.assess(ZERO_REPORT_LAT, ZERO_REPORT_LNG, "North Delhi", null, "Rohini Unmonitored Spot");

        assertNotNull(assessment);
        assertEquals(0, assessment.getActiveReportCount());
        assertEquals(DataQuality.MISSING_TELEMETRY, assessment.getDataQuality());
        assertEquals(RiskStatus.UNKNOWN, assessment.getStatus());
        assertEquals(RiskLevel.UNKNOWN, assessment.getRiskLevel());
        assertEquals(0, assessment.getRiskScore());
        assertEquals(0.0, assessment.getConfidence());
    }

    @Test
    @DisplayName("Scenario 3: Historical Susceptibility Without Active Flooding -> Low Score / NO_IMMEDIATE_RISK")
    void testHistoricalSusceptibilityWithoutFlooding() {
        WeatherService dryWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                return new RainForecast(0.0, 0.0, "NONE");
            }
        };

        FloodRiskAssessmentService customRiskService = new FloodRiskAssessmentService(
                dryWeather, reportService, hotspotRepo, riskRepo,
                new NasaImergService(false, "", ""), demService, 60);

        FloodRiskAssessment assessment = customRiskService.assess(ZERO_REPORT_LAT, ZERO_REPORT_LNG, "North Delhi", null, "Rohini Dry Monitored");

        assertNotNull(assessment);
        assertEquals(0, assessment.getActiveReportCount());
        assertNotEquals(RiskStatus.OBSERVED_FLOODING, assessment.getStatus());
        assertEquals(RiskStatus.NO_IMMEDIATE_RISK, assessment.getStatus());
        assertEquals(RiskLevel.LOW, assessment.getRiskLevel());
        assertTrue(assessment.getRiskScore() < 25, "Dry conditions must keep risk low despite historical hotspot prior");
    }

    @Test
    @DisplayName("Scenario 4: Active Citizen Report With Recent Timestamp -> OBSERVED_FLOODING (High Confidence)")
    void testActiveCitizenReportGroundTruth() {
        Report report = new Report();
        report.setLatitude(28.7800);
        report.setLongitude(77.1200);
        report.setLocationName("Narela Active Flood Point");
        report.setDepthFt(1.6);
        report.setSeverity(Severity.SEVERE);
        report.setDepthSource(DepthSource.CITIZEN);
        report.setStatus(ReportStatus.ACTIVE);
        report.setCreatedAt(Instant.now());
        report.setLastConfirmedAt(Instant.now());
        reportRepo.save(report);

        try {
            FloodRiskAssessment assessment = riskService.assess(28.7800, 77.1200, "North Delhi", null, "Narela Junction");
            assertNotNull(assessment);
            assertEquals(RiskStatus.OBSERVED_FLOODING, assessment.getStatus());
            assertEquals(RiskLevel.CRITICAL, assessment.getRiskLevel());
            assertEquals(0.95, assessment.getConfidence(), 0.01);
            assertTrue(assessment.getActiveReportCount() >= 1);
            assertTrue(assessment.getMaxObservedDepthFt() >= 1.5);
            assertTrue(assessment.getSummaryEn().contains("OBSERVED WATERLOGGING"));
        } finally {
            reportRepo.delete(report);
        }
    }

    @Test
    @DisplayName("Scenario 5: Stale / Missing Forecast Handled Gracefully")
    void testStaleForecastHandling() {
        WeatherService customWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                return new RainForecast(0.0, 0.0, "UNKNOWN");
            }
        };

        FloodRiskAssessmentService customRiskService = new FloodRiskAssessmentService(
                customWeather, reportService, hotspotRepo, riskRepo,
                new NasaImergService(false, "", ""), demService, 60);

        FloodRiskAssessment assessment = customRiskService.assess(ZERO_REPORT_LAT, ZERO_REPORT_LNG, "Central Delhi", null, "Stale Weather Spot");
        assertEquals(RiskStatus.UNKNOWN, assessment.getStatus());
        assertEquals(DataQuality.MISSING_TELEMETRY, assessment.getDataQuality());
    }

    @Test
    @DisplayName("Scenario 6: Missing DEM Elevation Handled Gracefully")
    void testMissingDemElevationHandling() {
        CopernicusDemService disabledDem = new CopernicusDemService(false);
        CopernicusDemService.TerrainRiskProfile profile = disabledDem.getElevationProfile(28.6347, 77.2263);

        assertFalse(profile.available(), "Profile must indicate unavailable");
        assertEquals("COPERNICUS_DEM_DISABLED", profile.urbanLimitationsNote());
        assertEquals(0.0, profile.elevationMetersAmsl());
    }

    @Test
    @DisplayName("Scenario 7: Invalid or Out-Of-Coverage Coordinates Handled Safely")
    void testOutOfCoverageCoordinates() {
        CopernicusDemService realDem = new CopernicusDemService(true);
        CopernicusDemService.TerrainRiskProfile profile = realDem.getElevationProfile(35.0, 85.0); // Outside Delhi N28_E077 tile

        assertFalse(profile.available());
        assertTrue(profile.urbanLimitationsNote().contains("UNAVAILABLE") || profile.urbanLimitationsNote().contains("COORDINATES_OUTSIDE_TILE_BOUNDS"));
    }

    @Test
    @DisplayName("Scenario 8: Digital Twin What-If Scenario Isolation")
    void testDigitalTwinScenarioIsolation() {
        long snapshotCountBefore = twinStateService.getSnapshotsByType(TwinSnapshotType.SIMULATED_SCENARIO).size();
        long operationalSnapshotsBefore = twinStateService.getSnapshotsByType(TwinSnapshotType.CURRENT_OPERATIONAL).size();

        ScenarioRequest req = new ScenarioRequest(
                "Phase 22 Heavy Monsoon Stress Test",
                65.0,
                30.0,
                3,
                null,
                40.0,
                List.of("RD_MINTO_UNDERPASS"),
                2
        );

        ScenarioRun run = scenarioService.runScenario(req, "AUDIT_TESTER");
        assertNotNull(run);
        assertEquals("COMPLETED", run.getStatus());
        assertTrue(run.getSimulatedRiskScore() > run.getBaselineRiskScore());
        assertTrue(run.getFactorBreakdownJson().contains("WHAT_IF_SCENARIO"));
        assertTrue(run.getDataGapsJson().toLowerCase().contains("outputs are decision-support heuristic indices"));

        long snapshotCountAfter = twinStateService.getSnapshotsByType(TwinSnapshotType.SIMULATED_SCENARIO).size();
        long operationalSnapshotsAfter = twinStateService.getSnapshotsByType(TwinSnapshotType.CURRENT_OPERATIONAL).size();

        assertEquals(snapshotCountBefore + 1, snapshotCountAfter, "One simulated snapshot created");
        assertEquals(operationalSnapshotsBefore, operationalSnapshotsAfter, "Operational snapshots count must remain unchanged");
    }

    @Test
    @DisplayName("Scenario 9: Spatial Road-Risk Intersections and Safer Route Advisory with Predicted Risk")
    void testSpatialRoadRiskIntersections() {
        // Create an elevated forecast risk at an unseeded corridor
        FloodRiskAssessment testCorridorRisk = new FloodRiskAssessment();
        testCorridorRisk.setHotspotName("Outer Ring Road Test Corridor");
        testCorridorRisk.setArea("North Delhi");
        testCorridorRisk.setLatitude(ZERO_REPORT_LAT);
        testCorridorRisk.setLongitude(ZERO_REPORT_LNG);
        testCorridorRisk.setStatus(RiskStatus.PREDICTED_RISK);
        testCorridorRisk.setRiskLevel(RiskLevel.CRITICAL);
        testCorridorRisk.setRiskScore(85);
        testCorridorRisk.setAssessedAt(Instant.now());
        testCorridorRisk.setExpiresAt(Instant.now().plusSeconds(3600));
        riskRepo.save(testCorridorRisk);

        try {
            List<double[]> routeThroughRisk = List.of(
                    new double[]{ZERO_REPORT_LAT - 0.001, ZERO_REPORT_LNG - 0.001},
                    new double[]{ZERO_REPORT_LAT + 0.001, ZERO_REPORT_LNG + 0.001}
            );
            List<double[]> routeBypass = List.of(
                    new double[]{28.7100, 77.0500},
                    new double[]{28.7200, 77.0600}
            );

            RouteEvaluateRequest req = new RouteEvaluateRequest(
                    List.of(routeThroughRisk, routeBypass),
                    60.0
            );

            RouteEvaluateResponse resp = routeService.evaluate(req);
            assertNotNull(resp);
            assertEquals(2, resp.routes().size());
            assertEquals(1, resp.recommendedIndex(), "Bypass route must be recommended");

            RouteResult r0 = resp.routes().get(0);
            assertTrue(r0.hazardCount() >= 1);
            assertEquals("PREDICTED", r0.hazards().get(0).hazardType());
            assertNull(r0.hazards().get(0).reportId());
        } finally {
            riskRepo.delete(testCorridorRisk);
        }
    }

    @Test
    @DisplayName("Scenario 10: Risk Explanation Consistency and Provenance Attribution")
    void testRiskExplanationConsistency() {
        WeatherService controlledWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                return new RainForecast(20.0, 8.0, "HEAVY");
            }
        };

        FloodRiskAssessmentService customRiskService = new FloodRiskAssessmentService(
                controlledWeather, reportService, hotspotRepo, riskRepo,
                new NasaImergService(false, "", ""), demService, 60);

        FloodRiskAssessment assessment = customRiskService.assess(ZERO_REPORT_LAT, ZERO_REPORT_LNG, "North Delhi", null, "Rohini Prediction Area");
        assertNotNull(assessment);
        assertNotNull(assessment.getSummaryEn());
        assertNotNull(assessment.getSummaryHi());
        assertNotNull(assessment.getFactorBreakdown());

        assertTrue(assessment.getFactorBreakdown().contains("forecastProvider"));
        assertTrue(assessment.getFactorBreakdown().contains("terrainProvider"));
        assertTrue(assessment.getFactorBreakdown().contains("rainPoints"));
        assertTrue(assessment.getFactorBreakdown().contains("hotspotPoints"));
    }

    @Test
    @DisplayName("Scenario 11: Calibration Feasibility Classification BLOCKED_INSUFFICIENT_GROUND_TRUTH")
    void testCalibrationFeasibilityIntegrity() {
        List<HistoricalFloodIncident> benchmarks = calibrationService.getAllBenchmarks();
        assertEquals(4, benchmarks.size(), "Exactly 4 documented historical benchmark events in Delhi NCR");

        for (HistoricalFloodIncident incident : benchmarks) {
            assertNotNull(incident.incidentId());
            assertNotNull(incident.sourceAgency());
            assertNotNull(incident.sourceCitationUrl());
            assertFalse(incident.isCalibratedGroundTruth(),
                    "Must be explicitly excluded from continuous numerical ground truth calibration");
            assertEquals("QUALITATIVE_MUNICIPAL_ESTIMATE", incident.waterDepthMeasurementType(),
                    "Visual depth observations are qualitative estimates");
        }
    }
}
