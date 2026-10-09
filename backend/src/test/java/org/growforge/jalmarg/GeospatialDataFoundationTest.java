package org.growforge.jalmarg;

import org.growforge.jalmarg.model.Enums.DataQuality;
import org.growforge.jalmarg.model.Enums.DepthLevel;
import org.growforge.jalmarg.model.Enums.DepthSource;
import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.FloodRiskAssessment;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.ProvenanceEnums.ProviderName;
import org.growforge.jalmarg.model.ProvenanceEnums.SourceCategory;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.model.SourceProvenance;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class GeospatialDataFoundationTest {

    private static void setEntityId(Object entity, long id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class TestRiskRepo implements FloodRiskAssessmentRepository {
        final List<FloodRiskAssessment> store = new ArrayList<>();
        long idGen = 1;

        @Override
        public Optional<FloodRiskAssessment> findTopByHotspotIdOrderByAssessedAtDesc(Long hotspotId) {
            return store.stream().filter(a -> Objects.equals(a.getHotspotId(), hotspotId))
                    .max(Comparator.comparing(FloodRiskAssessment::getAssessedAt));
        }

        @Override
        public List<FloodRiskAssessment> findByExpiresAtAfterOrderByRiskScoreDesc(Instant now) {
            return store.stream().filter(a -> a.getExpiresAt().isAfter(now))
                    .sorted(Comparator.comparingInt(FloodRiskAssessment::getRiskScore).reversed()).toList();
        }

        @Override
        public List<FloodRiskAssessment> findByRiskLevelAndExpiresAtAfter(org.growforge.jalmarg.model.Enums.RiskLevel riskLevel, Instant now) {
            return store.stream().filter(a -> a.getRiskLevel() == riskLevel && a.getExpiresAt().isAfter(now)).toList();
        }

        @Override public void deleteByExpiresAtBefore(Instant cutoff) { store.removeIf(a -> a.getExpiresAt().isBefore(cutoff)); }
        @Override public <S extends FloodRiskAssessment> S save(S entity) {
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

    private static class TestHotspotRepo implements HotspotRepository {
        final List<Hotspot> store = new ArrayList<>();
        long idGen = 1;

        @Override
        public List<Hotspot> findByLatitudeBetweenAndLongitudeBetween(double latMin, double latMax, double lngMin, double lngMax) {
            return store.stream().filter(h -> h.getLatitude() >= latMin && h.getLatitude() <= latMax
                    && h.getLongitude() >= lngMin && h.getLongitude() <= lngMax).toList();
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

        @Override public Optional<Hotspot> findById(Long id) { return store.stream().filter(h -> Objects.equals(h.getId(), id)).findFirst(); }
        @Override public List<Hotspot> findAll() { return new ArrayList<>(store); }
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

    private static class TestReportRepo implements ReportRepository {
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

        @Override public Optional<Report> findById(Long id) { return store.stream().filter(r -> Objects.equals(r.getId(), id)).findFirst(); }
        @Override public List<Report> findAll() { return new ArrayList<>(store); }
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

    private static class StubWeather extends WeatherService {
        RainForecast stub = new RainForecast(18.0, 6.0, "HEAVY");
        @Override
        public RainForecast forecast(double lat, double lng) {
            return stub;
        }
    }

    private TestRiskRepo riskRepo;
    private TestHotspotRepo hotspotRepo;
    private TestReportRepo reportRepo;
    private StubWeather weatherService;
    private NasaImergService nasaImergService;
    private CopernicusDemService copernicusDemService;
    private ReportService reportService;
    private HotspotService hotspotService;
    private FloodRiskAssessmentService riskService;

    @BeforeEach
    void setUp() throws IOException {
        riskRepo = new TestRiskRepo();
        hotspotRepo = new TestHotspotRepo();
        reportRepo = new TestReportRepo();
        weatherService = new StubWeather();

        nasaImergService = new NasaImergService(false, "", "");
        copernicusDemService = new CopernicusDemService(true);

        hotspotService = new HotspotService(hotspotRepo, reportRepo);
        AiDepthService aiDisabled = new AiDepthService(null, false, "apac.amazon.nova-lite-v1:0");
        StorageService storage = new StorageService(null, "", "./test-uploads");

        reportService = new ReportService(reportRepo, hotspotService, storage, aiDisabled, 6, false);
        riskService = new FloodRiskAssessmentService(weatherService, reportService, hotspotRepo, riskRepo,
                nasaImergService, copernicusDemService, 60);
    }

    // 1. Copernicus DEM Real Extraction & Unmounted Raster Fallback
    @Test
    void copernicusDemExtractsRealRasterElevationAtMintoBridge() {
        // Needs the 41 MB tile in backend/data/dem/ (not in git); skipped when absent, e.g. in CI.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new java.io.File("data/dem/Copernicus_DSM_COG_10_N28_00_E077_00_DEM.tif").isFile(),
                "Copernicus DEM tile not present; skipping real-raster check");
        CopernicusDemService.TerrainRiskProfile profile =
                copernicusDemService.getElevationProfile(28.6347, 77.2263);

        assertTrue(profile.available(), "Real Copernicus DEM raster should be available and decoded");
        assertEquals(211.08, profile.elevationMetersAmsl(), 0.1, "Extracted elevation at Minto Bridge (28.6347, 77.2263) is ~211.08m AMSL");
        assertTrue(profile.surroundingAvgElevationMeters() > 211.0, "Surrounding radial terrain should be higher than underpass dip");
        
        SourceProvenance prov = profile.provenance();
        assertNotNull(prov);
        assertEquals(ProviderName.COPERNICUS_ESA, prov.provider());
        assertEquals("COP_DEM_GLO_30", prov.product());
        assertEquals(SourceCategory.TERRAIN_RASTER_OBSERVATION, prov.sourceCategory());
        assertEquals("meters_egm2008", prov.unit());
        assertFalse(prov.isSynthetic(), "Real raster observation must not be flagged as synthetic");
        assertEquals(DataQuality.FRESH, prov.dataQuality());
    }

    @Test
    void copernicusDemUnmountedRasterReturnsUnavailableWithoutFabricatingMeasurements() {
        CopernicusDemService unmountedService = new CopernicusDemService(true, "./non-existent/dem.tif");
        CopernicusDemService.TerrainRiskProfile profile =
                unmountedService.getElevationProfile(28.6347, 77.2263);

        // When raster tile is unmounted on disk, must not fake elevation measurements
        assertFalse(profile.available());
        assertEquals(0.0, profile.elevationMetersAmsl());
        SourceProvenance prov = profile.provenance();
        assertNotNull(prov);
        assertEquals(ProviderName.COPERNICUS_ESA, prov.provider());
        assertEquals(DataQuality.MISSING_TELEMETRY, prov.dataQuality());
        assertTrue(prov.derivedValue().contains("DATA_UNAVAILABLE"));
    }

    @Test
    void copernicusDemBenchmarkExplicitlyMarkedSyntheticReference() {
        CopernicusDemService.TerrainRiskProfile benchmark =
                copernicusDemService.getBenchmarkProfile(28.6347, 77.2263);

        assertTrue(benchmark.available());
        assertEquals(214.0, benchmark.elevationMetersAmsl());
        assertEquals(218.5, benchmark.surroundingAvgElevationMeters());
        assertEquals(4.5, benchmark.relativeDepressionMeters(), 0.01);
        assertTrue(benchmark.isLowLyingDepression());

        SourceProvenance prov = benchmark.provenance();
        assertNotNull(prov);
        assertEquals(ProviderName.DEMO_FIXTURE, prov.provider());
        assertEquals(SourceCategory.SYNTHETIC_REFERENCE, prov.sourceCategory());
        assertTrue(prov.isSynthetic(), "Benchmark fixtures must be explicitly marked synthetic");
    }

    // 2. NASA GPM IMERG Offline & Disabled Fallback Safety
    @Test
    void nasaImergDisabledReturnsMissingTelemetryWithoutFabrication() {
        NasaImergService.SatellitePrecipitationObservation obs =
                nasaImergService.fetchRecentObservation(28.6347, 77.2263);

        assertFalse(obs.available());
        assertEquals(0.0, obs.precipitationRateMmPerHr());
        assertEquals(DataQuality.MISSING_TELEMETRY, obs.dataQuality());
        assertEquals(ProviderName.NASA_GES_DISC, obs.provenance().provider());
        assertEquals("GPM_3IMERGHHE.07", obs.provenance().product());
    }

    // 3. Demo Mode Isolation: Production vs Demo Separation
    @Test
    void demoReportsAreIsolatedWhenDemoIsolationModeIsActive() throws IOException {
        // Create a real citizen report
        Report real = reportService.create(new ReportService.NewReport(
                28.6347, 77.2263, "Real Minto Flood", "Delhi", DepthLevel.KNEE, "Real waterlogging", null, null));

        // Create a synthetic demo report
        Report demo = new Report();
        demo.setLatitude(28.5355);
        demo.setLongitude(77.3910);
        demo.setLocationName("Demo Spot");
        demo.setDepthFt(1.2);
        demo.setDepthSource(DepthSource.DEMO);
        demo.setStatus(ReportStatus.ACTIVE);
        demo.setCreatedAt(Instant.now());
        demo.setLastConfirmedAt(Instant.now());
        reportRepo.save(demo);

        // When isolated mode = false (default dev/demo mode)
        ReportService devReportService = new ReportService(reportRepo, hotspotService, null, null, 6, false);
        assertEquals(2, devReportService.active().size(), "Both real and demo active in dev mode");

        // When isolated mode = true (production safety mode)
        ReportService prodReportService = new ReportService(reportRepo, hotspotService, null, null, 6, true);
        List<Report> prodActive = prodReportService.active();
        assertEquals(1, prodActive.size(), "Only real citizen reports returned in isolated mode");
        assertEquals(real.getId(), prodActive.get(0).getId());
        assertEquals(DepthSource.CITIZEN, prodActive.get(0).getDepthSource());
    }

    // 4. End-to-End Environmental Factor Breakdown with Multi-Source Provenance
    @Test
    void riskAssessmentCombinesForecastTerrainAndProvenance() {
        Hotspot h = new Hotspot("Minto Bridge underpass, Connaught Place", "Delhi", 28.6347, 77.2263);
        h.setTotalReports(18);
        hotspotRepo.save(h);

        FloodRiskAssessment assessment = riskService.assess(28.6347, 77.2263, "Delhi", h.getId(), h.getName());

        assertNotNull(assessment);
        assertEquals(org.growforge.jalmarg.model.Enums.RiskStatus.PREDICTED_RISK, assessment.getStatus());
        assertTrue(assessment.getFactorBreakdown().contains("COP_DEM_GLO_30"));
        assertTrue(assessment.getFactorBreakdown().contains("ECMWF_IFS_OPENMETEO"));
        assertTrue(assessment.getFactorBreakdown().contains("GPM_3IMERGHHE.07"));
    }
}
