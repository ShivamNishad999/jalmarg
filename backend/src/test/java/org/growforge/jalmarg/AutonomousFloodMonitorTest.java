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
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AutonomousFloodMonitorTest {

    private static void setEntityId(Object entity, long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // In-memory mock repositories
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

        @Override
        public List<FloodRiskAssessment> findAll() { return new ArrayList<>(store); }

        // Remaining unused JpaRepository stubs
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

    private static class StubWeatherService extends WeatherService {
        RainForecast stubForecast = new RainForecast(0.0, 0.0, "NONE");
        boolean shouldThrow = false;

        @Override
        public RainForecast forecast(double lat, double lng) {
            if (shouldThrow) throw new RuntimeException("Weather API network timeout");
            return stubForecast;
        }
    }

    private InMemoryRiskRepo riskRepo;
    private InMemoryHotspotRepo hotspotRepo;
    private InMemoryReportRepo reportRepo;
    private StubWeatherService weatherService;
    private ReportService reportService;
    private HotspotService hotspotService;
    private FloodRiskAssessmentService riskService;
    private AutonomousFloodMonitorService monitorService;
    private RouteService routeService;

    @BeforeEach
    void setUp() throws IOException {
        riskRepo = new InMemoryRiskRepo();
        hotspotRepo = new InMemoryHotspotRepo();
        reportRepo = new InMemoryReportRepo();
        weatherService = new StubWeatherService();

        hotspotService = new HotspotService(hotspotRepo, reportRepo);
        AiDepthService aiDisabled = new AiDepthService(null, false, "apac.amazon.nova-lite-v1:0");
        StorageService storage = new StorageService(null, "", "./test-uploads");

        reportService = new ReportService(reportRepo, hotspotService, storage, aiDisabled, 6);
        riskService = new FloodRiskAssessmentService(weatherService, reportService, hotspotRepo, riskRepo, 60);
        monitorService = new AutonomousFloodMonitorService(hotspotRepo, riskService, riskRepo, null, true, 5);
        routeService = new RouteService(reportService, riskRepo);
    }

    // 1. Flood risk when no citizen reports exist
    @Test
    void predictiveFloodRiskWhenNoCitizenReportsExist() {
        weatherService.stubForecast = new WeatherService.RainForecast(42.0, 18.0, "VERY_HEAVY");

        Hotspot h = new Hotspot("Minto Bridge underpass", "Delhi", 28.6347, 77.2263);
        h.setTotalReports(15); // Proneness = 5
        hotspotRepo.save(h);

        FloodRiskAssessment assessment = riskService.assess(28.6347, 77.2263, "Delhi", h.getId(), h.getName());

        assertNotNull(assessment);
        assertEquals(RiskStatus.PREDICTED_RISK, assessment.getStatus(), "Must be categorized as PREDICTED_RISK when no citizen reports exist");
        assertEquals(RiskLevel.CRITICAL, assessment.getRiskLevel(), "Heavy rainfall at prone hotspot must yield CRITICAL risk");
        assertTrue(assessment.getRiskScore() >= 76);
        assertEquals(0, assessment.getActiveReportCount());
        assertTrue(assessment.getSummaryEn().contains("PREDICTED FLOOD RISK"));
    }

    // 2. Risk classification with moderate vs low weather
    @Test
    void riskClassificationWithModerateWeatherInputs() {
        weatherService.stubForecast = new WeatherService.RainForecast(12.0, 4.0, "LIGHT");

        Hotspot h = new Hotspot("Pari Chowk", "Greater Noida", 28.4660, 77.5130);
        h.setTotalReports(3); // Proneness = 1
        hotspotRepo.save(h);

        FloodRiskAssessment assessment = riskService.assess(28.4660, 77.5130, "Greater Noida", h.getId(), h.getName());

        assertEquals(RiskStatus.PREDICTED_RISK, assessment.getStatus());
        assertTrue(assessment.getRiskScore() >= 26 && assessment.getRiskScore() <= 50);
        assertEquals(RiskLevel.MODERATE, assessment.getRiskLevel());
    }

    // 3. Missing weather data returning UNKNOWN
    @Test
    void missingWeatherDataReturnsUnknownWithoutFabrication() {
        weatherService.shouldThrow = true;

        FloodRiskAssessment assessment = riskService.assess(28.5355, 77.3910, "Noida", null, "Unknown Point");

        assertEquals(RiskStatus.UNKNOWN, assessment.getStatus());
        assertEquals(RiskLevel.UNKNOWN, assessment.getRiskLevel());
        assertEquals(0, assessment.getRiskScore());
        assertEquals(0.0, assessment.getConfidence());
        assertEquals(DataQuality.MISSING_TELEMETRY, assessment.getDataQuality());
    }

    // 4. Ground observed report overrides weather forecast
    @Test
    void groundObservedReportOverridesDryWeatherForecast() throws IOException {
        // Dry forecast
        weatherService.stubForecast = new WeatherService.RainForecast(0.0, 0.0, "NONE");

        // Citizen files knee-deep flood report
        Report r = reportService.create(new ReportService.NewReport(
                28.6347, 77.2263, "Minto Bridge", "Delhi", DepthLevel.KNEE, "Underpass filled with water", null, null));

        FloodRiskAssessment assessment = riskService.assess(28.6347, 77.2263, "Delhi", null, "Minto Bridge");

        assertEquals(RiskStatus.OBSERVED_FLOODING, assessment.getStatus());
        assertEquals(RiskLevel.CRITICAL, assessment.getRiskLevel(), "Knee deep (1.5 ft) water is CRITICAL ground truth");
        assertEquals(1, assessment.getActiveReportCount());
        assertEquals(1.5, assessment.getMaxObservedDepthFt());
        assertEquals(0.95, assessment.getConfidence());
    }

    // 5. Duplicate citizen reports detection and confirmation merging
    @Test
    void duplicateCitizenReportsAreMergedAndIncrementConfirmations() throws IOException {
        Report first = reportService.create(new ReportService.NewReport(
                28.6280, 77.3690, "NH-9 underpass", "Noida", DepthLevel.ANKLE, "Water pooling", null, null));

        assertEquals(0, first.getConfirmations());
        assertEquals(0.35, first.getDepthFt());

        // Second report within 20 meters 2 minutes later with higher depth
        Report second = reportService.create(new ReportService.NewReport(
                28.6281, 77.3691, "NH-9 underpass", "Noida", DepthLevel.SHIN, "Water rising fast", null, null));

        assertEquals(first.getId(), second.getId(), "Duplicate report within 50m must merge into existing report ID");
        assertEquals(1, second.getConfirmations(), "Confirmation count should be incremented");
        assertEquals(0.8, second.getDepthFt(), "Depth should be upgraded to higher severity level");
    }

    // 6. Route ranking with observed hazards
    @Test
    void routeRankingPrefersSafeRouteOverObservedFloodedRoute() throws IOException {
        // Create an active flood report on Route 0 (lat: 28.6347, lng: 77.2263)
        reportService.create(new ReportService.NewReport(
                28.6347, 77.2263, "Minto Bridge", "Delhi", DepthLevel.KNEE, "Deep flood", null, null));

        List<List<double[]>> candidateRoutes = List.of(
                // Route 0 passes through Minto Bridge
                List.of(new double[]{28.6340, 77.2260}, new double[]{28.6350, 77.2265}),
                // Route 1 bypasses Minto Bridge (clear road)
                List.of(new double[]{28.6300, 77.2200}, new double[]{28.6310, 77.2210})
        );

        RouteEvaluateResponse response = routeService.evaluate(new RouteEvaluateRequest(candidateRoutes, 60.0));

        assertEquals(1, response.recommendedIndex(), "Route 1 must be recommended to avoid flooded Route 0");
        assertEquals(1, response.routes().get(0).hazardCount());
        assertEquals("OBSERVED", response.routes().get(0).hazards().get(0).hazardType());
        assertEquals(0, response.routes().get(1).hazardCount());
    }

    // 7. Route ranking with autonomous predicted hazards
    @Test
    void routeRankingAvoidsHighForecastRiskSpots() {
        // Save an autonomous predicted risk assessment near Route 0
        FloodRiskAssessment predicted = new FloodRiskAssessment();
        predicted.setHotspotName("High Risk Underpass");
        predicted.setLatitude(28.6280);
        predicted.setLongitude(77.3690);
        predicted.setStatus(RiskStatus.PREDICTED_RISK);
        predicted.setRiskLevel(RiskLevel.CRITICAL);
        predicted.setRiskScore(85);
        predicted.setAssessedAt(Instant.now());
        predicted.setExpiresAt(Instant.now().plusSeconds(3600));
        riskRepo.save(predicted);

        List<List<double[]>> candidateRoutes = List.of(
                // Route 0 passes directly through predicted high risk hotspot
                List.of(new double[]{28.6280, 77.3688}, new double[]{28.6280, 77.3692}),
                // Route 1 takes alternate corridor
                List.of(new double[]{28.6200, 77.3600}, new double[]{28.6210, 77.3610})
        );

        RouteEvaluateResponse response = routeService.evaluate(new RouteEvaluateRequest(candidateRoutes, 60.0));

        assertEquals(1, response.recommendedIndex(), "Route 1 must be recommended over forecast hazard on Route 0");
        assertEquals("PREDICTED", response.routes().get(0).hazards().get(0).hazardType());
    }

    // 8. Autonomous monitor cycle execution
    @Test
    void autonomousMonitorEvaluatesAllHotspotsAndUpdatesStatus() {
        hotspotRepo.save(new Hotspot("Hotspot A", "Delhi", 28.6347, 77.2263));
        hotspotRepo.save(new Hotspot("Hotspot B", "Noida", 28.5700, 77.3260));

        List<FloodRiskAssessment> results = monitorService.runEvaluationCycle();

        assertEquals(2, results.size());
        FloodRiskStatusView status = monitorService.getStatus();
        assertNotNull(status);
        assertEquals(1, status.totalCycles());
        assertEquals(2, status.evaluatedHotspotsCount());
    }
}
