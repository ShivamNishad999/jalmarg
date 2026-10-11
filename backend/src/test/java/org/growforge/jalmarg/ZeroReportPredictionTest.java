package org.growforge.jalmarg;

import org.growforge.jalmarg.dto.Dtos.*;
import org.growforge.jalmarg.model.Enums.*;
import org.growforge.jalmarg.model.FloodRiskAssessment;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.FloodRiskAssessmentRepository;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.growforge.jalmarg.repo.ReportRepository;
import org.growforge.jalmarg.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 17: Zero-Report Flood Prediction Integration Tests.
 *
 * Verifies that JalMarg autonomously calculates predictive flood risk and
 * recommends lower-risk alternative routes when ZERO active citizen reports
 * exist in the target area.
 *
 * Track 02 — Heat & Water requirement.
 */
class ZeroReportPredictionTest {

    private static void setEntityId(Object entity, long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class InMemoryRiskRepo implements FloodRiskAssessmentRepository {
        final List<FloodRiskAssessment> store = new ArrayList<>();
        long idGen = 1;

        @Override
        public Optional<FloodRiskAssessment> findTopByHotspotIdOrderByAssessedAtDesc(Long hotspotId) {
            return store.stream()
                    .filter(a -> Objects.equals(a.getHotspotId(), hotspotId))
                    .max(Comparator.comparing(FloodRiskAssessment::getAssessedAt));
        }

        @Override
        public List<FloodRiskAssessment> findByExpiresAtAfterOrderByRiskScoreDesc(Instant now) {
            return store.stream()
                    .filter(a -> a.getExpiresAt().isAfter(now))
                    .sorted(Comparator.comparingInt(FloodRiskAssessment::getRiskScore).reversed())
                    .toList();
        }

        @Override
        public List<FloodRiskAssessment> findByRiskLevelAndExpiresAtAfter(RiskLevel riskLevel, Instant now) {
            return store.stream()
                    .filter(a -> a.getRiskLevel() == riskLevel && a.getExpiresAt().isAfter(now))
                    .toList();
        }

        @Override
        public void deleteByExpiresAtBefore(Instant cutoff) {
            store.removeIf(a -> a.getExpiresAt().isBefore(cutoff));
        }

        @Override
        public <S extends FloodRiskAssessment> S save(S entity) {
            if (entity.getId() == null) setEntityId(entity, idGen++);
            store.removeIf(a -> Objects.equals(a.getId(), entity.getId()));
            store.add(entity);
            return entity;
        }

        @Override public List<FloodRiskAssessment> findAll() { return new ArrayList<>(store); }
        @Override public void flush() {}
        @Override public <S extends FloodRiskAssessment> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends FloodRiskAssessment> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<FloodRiskAssessment> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public FloodRiskAssessment getOne(Long aLong) { return null; }
        @Override public FloodRiskAssessment getById(Long aLong) { return null; }
        @Override public FloodRiskAssessment getReferenceById(Long aLong) { return null; }
        @Override public <S extends FloodRiskAssessment> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends FloodRiskAssessment> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends FloodRiskAssessment> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends FloodRiskAssessment> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends FloodRiskAssessment> long count(Example<S> example) { return 0; }
        @Override public <S extends FloodRiskAssessment> boolean exists(Example<S> example) { return false; }
        @Override public <S extends FloodRiskAssessment, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends FloodRiskAssessment> List<S> saveAll(Iterable<S> entities) { entities.forEach(this::save); return null; }
        @Override public Optional<FloodRiskAssessment> findById(Long aLong) { return store.stream().filter(a -> Objects.equals(a.getId(), aLong)).findFirst(); }
        @Override public boolean existsById(Long aLong) { return findById(aLong).isPresent(); }
        @Override public List<FloodRiskAssessment> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long aLong) { store.removeIf(a -> Objects.equals(a.getId(), aLong)); }
        @Override public void delete(FloodRiskAssessment entity) { store.remove(entity); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends FloodRiskAssessment> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public List<FloodRiskAssessment> findAll(Sort sort) { return findAll(); }
        @Override public Page<FloodRiskAssessment> findAll(Pageable pageable) { return null; }
    }

    private static class InMemoryHotspotRepo implements HotspotRepository {
        final List<Hotspot> store = new ArrayList<>();
        long idGen = 1;

        @Override
        public List<Hotspot> findByLatitudeBetweenAndLongitudeBetween(double latMin, double latMax, double lngMin, double lngMax) {
            return store.stream()
                    .filter(h -> h.getLatitude() >= latMin && h.getLatitude() <= latMax
                            && h.getLongitude() >= lngMin && h.getLongitude() <= lngMax)
                    .toList();
        }

        @Override
        public List<Hotspot> findAllByOrderByTotalReportsDesc() {
            return store.stream().sorted(Comparator.comparingInt(Hotspot::getTotalReports).reversed()).toList();
        }

        @Override
        public <S extends Hotspot> S save(S entity) {
            if (entity.getId() == null) setEntityId(entity, idGen++);
            store.removeIf(h -> Objects.equals(h.getId(), entity.getId()));
            store.add(entity);
            return entity;
        }

        @Override
        public Optional<Hotspot> findById(Long id) {
            return store.stream().filter(h -> Objects.equals(h.getId(), id)).findFirst();
        }

        @Override
        public List<Hotspot> findAll() { return new ArrayList<>(store); }

        @Override public void flush() {}
        @Override public <S extends Hotspot> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Hotspot> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<Hotspot> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public Hotspot getOne(Long aLong) { return null; }
        @Override public Hotspot getById(Long aLong) { return null; }
        @Override public Hotspot getReferenceById(Long aLong) { return null; }
        @Override public <S extends Hotspot> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends Hotspot> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends Hotspot> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends Hotspot> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends Hotspot> long count(Example<S> example) { return 0; }
        @Override public <S extends Hotspot> boolean exists(Example<S> example) { return false; }
        @Override public <S extends Hotspot, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends Hotspot> List<S> saveAll(Iterable<S> entities) { entities.forEach(this::save); return null; }
        @Override public boolean existsById(Long aLong) { return findById(aLong).isPresent(); }
        @Override public List<Hotspot> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long aLong) { store.removeIf(h -> Objects.equals(h.getId(), aLong)); }
        @Override public void delete(Hotspot entity) { store.remove(entity); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends Hotspot> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public List<Hotspot> findAll(Sort sort) { return findAll(); }
        @Override public Page<Hotspot> findAll(Pageable pageable) { return null; }
    }

    private static class InMemoryReportRepo implements ReportRepository {
        final List<Report> store = new ArrayList<>();
        long idGen = 1;

        @Override
        public List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status) {
            return store.stream().filter(r -> r.getStatus() == status)
                    .sorted(Comparator.comparing(Report::getCreatedAt).reversed()).toList();
        }

        @Override
        public List<Report> findByCreatedAtAfterOrderByCreatedAtDesc(Instant cutoff) {
            return store.stream().filter(r -> r.getCreatedAt().isAfter(cutoff))
                    .sorted(Comparator.comparing(Report::getCreatedAt).reversed()).toList();
        }

        @Override
        public List<Report> findByHotspotIdOrderByCreatedAtDesc(Long hotspotId) {
            return store.stream().filter(r -> r.getHotspot() != null && Objects.equals(r.getHotspot().getId(), hotspotId))
                    .sorted(Comparator.comparing(Report::getCreatedAt).reversed()).toList();
        }

        @Override
        public List<Report> findStale(ReportStatus status, Instant cutoff) {
            return store.stream().filter(r -> r.getStatus() == status && r.getLastConfirmedAt().isBefore(cutoff)).toList();
        }

        @Override
        public long countByStatus(ReportStatus status) {
            return store.stream().filter(r -> r.getStatus() == status).count();
        }

        @Override
        public <S extends Report> S save(S entity) {
            if (entity.getId() == null) setEntityId(entity, idGen++);
            store.removeIf(r -> Objects.equals(r.getId(), entity.getId()));
            store.add(entity);
            return entity;
        }

        @Override
        public Optional<Report> findById(Long id) {
            return store.stream().filter(r -> Objects.equals(r.getId(), id)).findFirst();
        }

        @Override
        public List<Report> findAll() { return new ArrayList<>(store); }

        @Override public void flush() {}
        @Override public <S extends Report> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends Report> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<Report> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public Report getOne(Long aLong) { return null; }
        @Override public Report getById(Long aLong) { return null; }
        @Override public Report getReferenceById(Long aLong) { return null; }
        @Override public <S extends Report> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends Report> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends Report> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends Report> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends Report> long count(Example<S> example) { return 0; }
        @Override public <S extends Report> boolean exists(Example<S> example) { return false; }
        @Override public <S extends Report, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends Report> List<S> saveAll(Iterable<S> entities) { entities.forEach(this::save); return null; }
        @Override public boolean existsById(Long aLong) { return findById(aLong).isPresent(); }
        @Override public List<Report> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(Long aLong) { store.removeIf(r -> Objects.equals(r.getId(), aLong)); }
        @Override public void delete(Report entity) { store.remove(entity); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends Report> entities) {}
        @Override public void deleteAll() { store.clear(); }
        @Override public List<Report> findAll(Sort sort) { return findAll(); }
        @Override public Page<Report> findAll(Pageable pageable) { return null; }
    }

    private InMemoryReportRepo reportRepo;
    private InMemoryHotspotRepo hotspotRepo;
    private InMemoryRiskRepo riskRepo;
    private HotspotService hotspotService;
    private ReportService reportService;
    private CopernicusDemService demService;
    private NasaImergService nasaService;

    @BeforeEach
    void setUp() throws Exception {
        reportRepo = new InMemoryReportRepo();
        hotspotRepo = new InMemoryHotspotRepo();
        riskRepo = new InMemoryRiskRepo();
        hotspotService = new HotspotService(hotspotRepo, reportRepo);
        demService = new CopernicusDemService(true);
        nasaService = new NasaImergService(false, "", "");

        // ReportService with 6h TTL, demoIsolatedMode = true
        reportService = new ReportService(reportRepo, hotspotService,
                new StorageService(null, "", "target/test-uploads"),
                new AiDepthService(null, false, "us.amazon.nova-lite-v1:0"),
                6, true);
    }

    @Test
    @DisplayName("Zero Reports + Heavy Rain at Hotspot -> PREDICTED_RISK (CRITICAL), Not Observed Flooding")
    void testZeroReportHighRainfallPredictsElevatedRisk() {
        // Setup: Known Minto Bridge hotspot with high historical susceptibility (proneness = 5)
        Hotspot minto = new Hotspot();
        minto.setName("Minto Bridge Underpass");
        minto.setArea("Delhi");
        minto.setLatitude(28.6347);
        minto.setLongitude(77.2263);
        minto.setTotalReports(18); // proneness = min(5, 18/3) = 5 -> hotspotPoints = 30
        hotspotRepo.save(minto);

        // Controlled test weather telemetry (Heavy rain: 45mm in 6h -> rainPoints = 55)
        WeatherService mockWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                return new RainForecast(45.0, 20.0, "VERY_HEAVY");
            }
        };

        FloodRiskAssessmentService assessmentService = new FloodRiskAssessmentService(
                mockWeather, reportService, hotspotRepo, riskRepo, nasaService, demService, 60);

        // Execute: Assess risk at Minto Bridge with ZERO active citizen reports
        FloodRiskAssessment result = assessmentService.assess(28.6347, 77.2263, "Delhi", minto.getId(), minto.getName());

        // Assertions
        assertNotNull(result);
        assertEquals(0, result.getActiveReportCount(), "Must have zero active citizen reports");
        assertEquals(0.0, result.getMaxObservedDepthFt(), "Observed depth must be 0.0 when no reports exist");
        assertEquals(RiskStatus.PREDICTED_RISK, result.getStatus(), "Must be PREDICTED_RISK, never OBSERVED_FLOODING");
        assertNotEquals(RiskStatus.OBSERVED_FLOODING, result.getStatus());
        assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(), "55 rain points + 30 hotspot points = 85 (CRITICAL)");
        assertEquals(85, result.getRiskScore());
        assertEquals(DataQuality.FRESH, result.getDataQuality());
        assertTrue(result.getSummaryEn().contains("PREDICTED FLOOD RISK"), "Summary must explicitly identify prediction");
        assertTrue(result.getSummaryEn().contains("45.0 mm"), "Summary must reflect real/simulated forecast rainfall");
        assertTrue(result.getSummaryEn().contains("proneness 5/5"), "Summary must identify hotspot proneness factor");
        assertTrue(result.getFactorBreakdown().contains("\"type\":\"PREDICTED\""));
        assertTrue(result.getFactorBreakdown().contains("\"rainPoints\":55"));
        assertTrue(result.getFactorBreakdown().contains("\"hotspotPoints\":30"));
        assertTrue(result.getFactorBreakdown().contains("\"terrainProvider\":\"COPERNICUS_ESA\""));
    }

    @Test
    @DisplayName("Zero Reports + Dry Weather at Hotspot -> NO_IMMEDIATE_RISK / LOW Score")
    void testZeroReportDryWeatherPredictsLowRisk() {
        Hotspot nehru = new Hotspot();
        nehru.setName("Nehru Place Outer Ring Road");
        nehru.setArea("Delhi");
        nehru.setLatitude(28.5494);
        nehru.setLongitude(77.2529);
        nehru.setTotalReports(3); // proneness = 1 -> hotspotPoints = 6
        hotspotRepo.save(nehru);

        // Dry weather: 0 mm rain
        WeatherService mockWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                return new RainForecast(0.0, 0.0, "NONE");
            }
        };

        FloodRiskAssessmentService assessmentService = new FloodRiskAssessmentService(
                mockWeather, reportService, hotspotRepo, riskRepo, nasaService, demService, 60);

        FloodRiskAssessment result = assessmentService.assess(28.5494, 77.2529, "Delhi", nehru.getId(), nehru.getName());

        assertNotNull(result);
        assertEquals(0, result.getActiveReportCount());
        assertEquals(6, result.getRiskScore(), "0 rain points + 6 hotspot points = 6");
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertEquals(RiskStatus.NO_IMMEDIATE_RISK, result.getStatus(), "Score < 20 must return NO_IMMEDIATE_RISK");
        assertNotEquals(RiskStatus.OBSERVED_FLOODING, result.getStatus());
        assertTrue(result.getSummaryEn().contains("Low flood risk"));
    }

    @Test
    @DisplayName("Zero Reports + Missing Weather Telemetry -> UNKNOWN (Never Fakes Rain or Depth)")
    void testZeroReportMissingWeatherTelemetryReturnsUnknown() {
        WeatherService failingWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                throw new RuntimeException("Open-Meteo connection timed out");
            }
        };

        FloodRiskAssessmentService assessmentService = new FloodRiskAssessmentService(
                failingWeather, reportService, hotspotRepo, riskRepo, nasaService, demService, 60);

        FloodRiskAssessment result = assessmentService.assess(28.6347, 77.2263, "Delhi", null, "Test Point");

        assertNotNull(result);
        assertEquals(0, result.getActiveReportCount());
        assertEquals(DataQuality.MISSING_TELEMETRY, result.getDataQuality());
        assertEquals(RiskStatus.UNKNOWN, result.getStatus());
        assertEquals(RiskLevel.UNKNOWN, result.getRiskLevel());
        assertEquals(0, result.getRiskScore());
        assertEquals(0.0, result.getConfidence());
        assertTrue(result.getSummaryEn().contains("Telemetry unavailable"));
    }

    @Test
    @DisplayName("Zero Citizen Reports: Route Evaluation Penalizes Forecast Risk and Recommends Safer Detour")
    void testZeroReportRoutingAvoidsPredictedFloodRisk() {
        // 1. Create elevated forecast risk at Minto Bridge (CRITICAL)
        FloodRiskAssessment mintoRisk = new FloodRiskAssessment();
        mintoRisk.setHotspotName("Minto Bridge Underpass");
        mintoRisk.setArea("Delhi");
        mintoRisk.setLatitude(28.6347);
        mintoRisk.setLongitude(77.2263);
        mintoRisk.setStatus(RiskStatus.PREDICTED_RISK);
        mintoRisk.setRiskLevel(RiskLevel.CRITICAL);
        mintoRisk.setRiskScore(85);
        mintoRisk.setAssessedAt(Instant.now());
        mintoRisk.setExpiresAt(Instant.now().plusSeconds(3600));
        riskRepo.save(mintoRisk);

        // 2. Setup RouteService (Zero active reports in reportRepo)
        assertEquals(0, reportRepo.count());
        RouteService routeService = new RouteService(reportService, riskRepo);

        // 3. Define 2 candidate routes:
        // Route 0: Directly traverses Minto Bridge ([28.6340, 77.2260] -> [28.6350, 77.2265])
        // Route 1: Safe detour bypassing the underpass ([28.6100, 77.2000] -> [28.6200, 77.2100])
        List<double[]> routeCrossingHazard = List.of(
                new double[]{28.6340, 77.2260},
                new double[]{28.6350, 77.2265}
        );
        List<double[]> routeClearDetour = List.of(
                new double[]{28.6100, 77.2000},
                new double[]{28.6200, 77.2100}
        );

        RouteEvaluateRequest request = new RouteEvaluateRequest(
                List.of(routeCrossingHazard, routeClearDetour),
                60.0
        );

        // 4. Evaluate routes
        RouteEvaluateResponse response = routeService.evaluate(request);

        // 5. Assertions
        assertNotNull(response);
        assertEquals(2, response.routes().size());

        // Route 0 must detect the forecast risk hazard
        RouteResult result0 = response.routes().get(0);
        assertEquals(0, result0.index());
        assertEquals(1, result0.hazardCount(), "Route 0 must detect predicted hazard at Minto Bridge");
        assertTrue(result0.riskScore() > 0, "Route 0 must have elevated risk score");
        assertEquals(1, result0.hazards().size());
        RouteHazard hazard = result0.hazards().get(0);
        assertEquals("PREDICTED", hazard.hazardType(), "Hazard type must be PREDICTED");
        assertNull(hazard.reportId(), "Predicted hazard must have null reportId");
        assertEquals(0.0, hazard.depthFt(), "Predicted hazard depth must be 0.0 unmeasured");
        assertTrue(hazard.locationName().contains("[Forecast Risk]"));

        // Route 1 (Detour) must have zero hazards
        RouteResult result1 = response.routes().get(1);
        assertEquals(1, result1.index());
        assertEquals(0, result1.hazardCount());
        assertEquals(0.0, result1.riskScore());

        // RouteService must recommend Route 1 (Detour) over Route 0
        assertEquals(1, response.recommendedIndex(), "Router must recommend lower-risk Route 1");
    }

    @Test
    @DisplayName("Demo Isolation Mode Prevents Mock Demo Reports from Becoming Ground Truth Observations")
    void testDemoIsolationExcludesDemoReportsFromObservedFlooding() {
        // Create a DEMO report
        Report demoReport = new Report();
        demoReport.setLatitude(28.6347);
        demoReport.setLongitude(77.2263);
        demoReport.setLocationName("Minto Bridge Demo Spot");
        demoReport.setDepthFt(2.0);
        demoReport.setDepthSource(DepthSource.DEMO);
        demoReport.setStatus(ReportStatus.ACTIVE);
        demoReport.setCreatedAt(Instant.now());
        demoReport.setLastConfirmedAt(Instant.now());
        reportRepo.save(demoReport);

        // In demo isolated mode, reportService.active() filters out DEMO reports
        List<Report> active = reportService.active();
        assertEquals(0, active.size(), "Demo reports must be isolated when demoIsolatedMode = true");

        // Assess risk with dry weather
        WeatherService mockWeather = new WeatherService() {
            @Override
            public RainForecast forecast(double lat, double lng) {
                return new RainForecast(0.0, 0.0, "NONE");
            }
        };

        FloodRiskAssessmentService assessmentService = new FloodRiskAssessmentService(
                mockWeather, reportService, hotspotRepo, riskRepo, nasaService, demService, 60);

        FloodRiskAssessment result = assessmentService.assess(28.6347, 77.2263, "Delhi", null, "Minto Bridge");

        // Result must NOT be OBSERVED_FLOODING because demo report is isolated
        assertNotEquals(RiskStatus.OBSERVED_FLOODING, result.getStatus(),
                "Demo reports must not contaminate real operational observations");
        assertEquals(0, result.getActiveReportCount());
    }
}
