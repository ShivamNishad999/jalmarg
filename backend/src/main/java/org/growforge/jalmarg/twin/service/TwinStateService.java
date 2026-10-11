package org.growforge.jalmarg.twin.service;

import jakarta.annotation.PostConstruct;
import org.growforge.jalmarg.model.Enums.RiskLevel;
import org.growforge.jalmarg.model.Enums.RiskStatus;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.service.FloodRiskAssessmentService;
import org.growforge.jalmarg.service.ReportService;
import org.growforge.jalmarg.service.WeatherService;
import org.growforge.jalmarg.twin.model.DrainageAsset;
import org.growforge.jalmarg.twin.model.MunicipalZone;
import org.growforge.jalmarg.twin.model.RoadSegment;
import org.growforge.jalmarg.twin.model.TwinEnums.AssetStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.BoundaryType;
import org.growforge.jalmarg.twin.model.TwinEnums.DrainageAssetType;
import org.growforge.jalmarg.twin.model.TwinEnums.IncidentStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.RoadType;
import org.growforge.jalmarg.twin.model.TwinEnums.TwinSnapshotType;
import org.growforge.jalmarg.twin.model.TwinSnapshot;
import org.growforge.jalmarg.twin.repo.DrainageAssetRepository;
import org.growforge.jalmarg.twin.repo.MunicipalIncidentRepository;
import org.growforge.jalmarg.twin.repo.MunicipalZoneRepository;
import org.growforge.jalmarg.twin.repo.RoadSegmentRepository;
import org.growforge.jalmarg.twin.repo.TwinSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class TwinStateService {

    private static final Logger log = LoggerFactory.getLogger(TwinStateService.class);
    public static final String TWIN_MODEL_VERSION = "JALMARG_TWIN_V1.0_PROVENANCE_HEURISTIC";

    private final MunicipalZoneRepository zoneRepo;
    private final RoadSegmentRepository roadRepo;
    private final DrainageAssetRepository drainRepo;
    private final TwinSnapshotRepository snapshotRepo;
    private final MunicipalIncidentRepository incidentRepo;
    private final ReportService reportService;
    private final FloodRiskAssessmentService riskService;
    private final WeatherService weatherService;

    public TwinStateService(MunicipalZoneRepository zoneRepo,
                            RoadSegmentRepository roadRepo,
                            DrainageAssetRepository drainRepo,
                            TwinSnapshotRepository snapshotRepo,
                            MunicipalIncidentRepository incidentRepo,
                            ReportService reportService,
                            FloodRiskAssessmentService riskService,
                            WeatherService weatherService) {
        this.zoneRepo = zoneRepo;
        this.roadRepo = roadRepo;
        this.drainRepo = drainRepo;
        this.snapshotRepo = snapshotRepo;
        this.incidentRepo = incidentRepo;
        this.reportService = reportService;
        this.riskService = riskService;
        this.weatherService = weatherService;
    }

    @PostConstruct
    @Transactional
    public void initFoundationData() {
        if (zoneRepo.count() == 0) {
            seedMunicipalZones();
        }
        if (roadRepo.count() == 0) {
            seedRoadSegments();
        }
        if (drainRepo.count() == 0) {
            seedDrainageAssets();
        }
        if (snapshotRepo.count() == 0) {
            generateOperationalSnapshot();
        }
    }

    private void seedMunicipalZones() {
        List<MunicipalZone> zones = List.of(
                new MunicipalZone("ZONE_NDMC_CENTRAL", "Central Delhi (Connaught Place & Lutyens)",
                        "New Delhi Municipal Council", BoundaryType.PLANNING_BOUNDING_BOX,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[77.20,28.61],[77.24,28.61],[77.24,28.65],[77.20,28.65],[77.20,28.61]]]}",
                        28.6304, 77.2177, 42.7, 300000.0, "UNAVAILABLE"),

                new MunicipalZone("ZONE_MCD_SOUTH", "South Delhi (AIIMS, Lajpat Nagar, Saket)",
                        "Municipal Corporation of Delhi", BoundaryType.PLANNING_BOUNDING_BOX,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[77.18,28.50],[77.26,28.50],[77.26,28.58],[77.18,28.58],[77.18,28.50]]]}",
                        28.5400, 77.2200, 115.0, 2500000.0, "UNAVAILABLE"),

                new MunicipalZone("ZONE_MCD_NORTH_WEST", "North-West Delhi (Zakhira, Azadpur, Rohini)",
                        "Municipal Corporation of Delhi", BoundaryType.PLANNING_BOUNDING_BOX,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[77.08,28.66],[77.18,28.66],[77.18,28.76],[77.08,28.76],[77.08,28.66]]]}",
                        28.7000, 77.1300, 130.0, 3000000.0, "UNAVAILABLE"),

                new MunicipalZone("ZONE_NOIDA_URBAN", "Noida Urban Core (Sector 18, Sector 62)",
                        "New Okhla Industrial Development Authority", BoundaryType.PLANNING_BOUNDING_BOX,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[77.30,28.54],[77.40,28.54],[77.40,28.62],[77.30,28.62],[77.30,28.54]]]}",
                        28.5700, 77.3500, 203.0, 1000000.0, "UNAVAILABLE"),

                new MunicipalZone("ZONE_GURUGRAM_CENTRAL", "Gurugram Cyber City & Golf Course Rd",
                        "Municipal Corporation of Gurugram", BoundaryType.PLANNING_BOUNDING_BOX,
                        "{\"type\":\"Polygon\",\"coordinates\":[[[77.00,28.44],[77.10,28.44],[77.10,28.50],[77.00,28.50],[77.00,28.44]]]}",
                        28.4700, 77.0500, 230.0, 1500000.0, "UNAVAILABLE")
        );
        zoneRepo.saveAll(zones);
        log.info("Seeded {} authoritative municipal planning zones.", zones.size());
    }

    private void seedRoadSegments() {
        MunicipalZone central = zoneRepo.findByZoneCode("ZONE_NDMC_CENTRAL").orElse(null);
        MunicipalZone south = zoneRepo.findByZoneCode("ZONE_MCD_SOUTH").orElse(null);
        MunicipalZone nw = zoneRepo.findByZoneCode("ZONE_MCD_NORTH_WEST").orElse(null);

        List<RoadSegment> roads = List.of(
                new RoadSegment("RD_MINTO_UNDERPASS", "Minto Road Underpass Corridor", RoadType.UNDERPASS,
                        28.6340, 77.2260, 28.6355, 77.2268,
                        "{\"type\":\"LineString\",\"coordinates\":[[77.2260,28.6340],[77.2264,28.6347],[77.2268,28.6355]]}",
                        true, 280.0, 211.2, 215.8, central),

                new RoadSegment("RD_TILAK_BRIDGE", "Tilak Bridge Railway Underpass", RoadType.UNDERPASS,
                        28.6240, 77.2380, 28.6260, 77.2410,
                        "{\"type\":\"LineString\",\"coordinates\":[[77.2380,28.6240],[77.2395,28.6250],[77.2410,28.6260]]}",
                        true, 320.0, 212.0, 216.5, central),

                new RoadSegment("RD_AIIMS_FLYOVER_UNDERPASS", "AIIMS Ring Road Underpass", RoadType.UNDERPASS,
                        28.5670, 77.2080, 28.5700, 77.2120,
                        "{\"type\":\"LineString\",\"coordinates\":[[77.2080,28.5670],[77.2100,28.5685],[77.2120,28.5700]]}",
                        true, 450.0, 215.0, 222.0, south),

                new RoadSegment("RD_PUL_PRAHLADPUR", "Pul Prahladpur Railway Underpass", RoadType.UNDERPASS,
                        28.5020, 77.2910, 28.5050, 77.2940,
                        "{\"type\":\"LineString\",\"coordinates\":[[77.2910,28.5020],[77.2925,28.5030],[77.2940,28.5050]]}",
                        true, 380.0, 206.5, 212.0, south),

                new RoadSegment("RD_ZAKHIRA_UNDERPASS", "Zakhira Rohtak Road Underpass", RoadType.UNDERPASS,
                        28.6680, 77.1550, 28.6710, 77.1580,
                        "{\"type\":\"LineString\",\"coordinates\":[[77.1550,28.6680],[77.1565,28.6695],[77.1580,28.6710]]}",
                        true, 410.0, 213.0, 218.0, nw),

                new RoadSegment("RD_CONNAUGHT_OUTER", "Connaught Place Outer Circle Arterial", RoadType.PRIMARY_ARTERIAL,
                        28.6290, 77.2150, 28.6340, 77.2220,
                        "{\"type\":\"LineString\",\"coordinates\":[[77.2150,28.6290],[77.2185,28.6315],[77.2220,28.6340]]}",
                        false, 1100.0, 215.5, 216.5, central)
        );
        roadRepo.saveAll(roads);
        log.info("Seeded {} critical transportation & underpass road segments.", roads.size());
    }

    @Transactional
    public TwinSnapshot generateOperationalSnapshot() {
        MunicipalZone centralZone = zoneRepo.findByZoneCode("ZONE_NDMC_CENTRAL").orElse(null);
        Instant now = Instant.now();

        List<Report> activeReports = reportService.active();
        long activeIncidents = incidentRepo.countByStatus(IncidentStatus.IN_PROGRESS) +
                incidentRepo.countByStatus(IncidentStatus.ASSIGNED) +
                incidentRepo.countByStatus(IncidentStatus.NEW);

        WeatherService.RainForecast forecast;
        try {
            forecast = weatherService.forecast(28.6347, 77.2263);
        } catch (Exception e) {
            forecast = new WeatherService.RainForecast(0, 0, "UNKNOWN");
        }

        double rain6h = forecast.next6hMm();
        int activeCount = activeReports.size();

        RiskLevel level;
        RiskStatus status;
        int overallScore;

        if (activeCount > 0) {
            status = RiskStatus.OBSERVED_FLOODING;
            double maxDepth = activeReports.stream().mapToDouble(Report::getDepthFt).max().orElse(0.0);
            overallScore = Math.min(100, 60 + (int) (maxDepth * 15));
            level = maxDepth >= 0.8 ? RiskLevel.HIGH : RiskLevel.MODERATE;
        } else if (rain6h >= 15.0) {
            status = RiskStatus.PREDICTED_RISK;
            overallScore = 75;
            level = RiskLevel.HIGH;
        } else if (rain6h >= 5.0) {
            status = RiskStatus.PREDICTED_RISK;
            overallScore = 40;
            level = RiskLevel.MODERATE;
        } else {
            status = RiskStatus.NO_IMMEDIATE_RISK;
            overallScore = 10;
            level = RiskLevel.LOW;
        }

        String evidenceJson = String.format(Locale.ROOT,
                "{\"activeReportsCount\":%d,\"activeIncidentsCount\":%d,\"rain6hMm\":%.1f,\"forecastLevel\":\"%s\",\"primarySources\":[\"OPEN_METEO\",\"CITIZEN_CROWD\",\"COPERNICUS_DEM\"]}",
                activeCount, activeIncidents, rain6h, forecast.level());

        String missingDataJson = "{\"drainageTelemetry\":\"UNAVAILABLE\",\"underpassPumpFlow\":\"UNMETERED\",\"satelliteLatencyHours\":4}";
        String provenance = "Open-Meteo ECMWF IFS + Copernicus DEM GLO-30 + Citizen Ground Corroboration";
        String disclaimer = "Digital Twin state represents multi-source evidence and vulnerability heuristics; not physical hydrodynamic CFD.";

        String hashContent = now.toString() + overallScore + status.name() + activeCount + rain6h;
        String immutableHash = computeSha256(hashContent);

        TwinSnapshot snapshot = new TwinSnapshot(
                TwinSnapshotType.CURRENT_OPERATIONAL,
                centralZone,
                centralZone != null ? centralZone.getName() : "Delhi NCR Region",
                now,
                overallScore,
                level,
                status,
                activeCount,
                (int) activeIncidents,
                rain6h,
                TWIN_MODEL_VERSION,
                evidenceJson,
                missingDataJson,
                provenance,
                disclaimer,
                immutableHash
        );

        return snapshotRepo.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<TwinSnapshot> getSnapshotsByType(TwinSnapshotType type) {
        return snapshotRepo.findBySnapshotTypeOrderBySnapshotTimestampDesc(type);
    }

    @Transactional(readOnly = true)
    public TwinSnapshot getLatestOperationalSnapshot() {
        return snapshotRepo.findTopBySnapshotTypeOrderBySnapshotTimestampDesc(TwinSnapshotType.CURRENT_OPERATIONAL)
                .orElseGet(this::generateOperationalSnapshot);
    }

    @Transactional(readOnly = true)
    public TwinSnapshot getSnapshotById(Long id) {
        return snapshotRepo.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MunicipalZone> getAllZones() {
        return zoneRepo.findAll();
    }

    private void seedDrainageAssets() {
        MunicipalZone south = zoneRepo.findByZoneCode("ZONE_MCD_SOUTH").orElse(null);
        MunicipalZone nw = zoneRepo.findByZoneCode("ZONE_MCD_NORTH_WEST").orElse(null);
        MunicipalZone central = zoneRepo.findByZoneCode("ZONE_NDMC_CENTRAL").orElse(null);

        List<DrainageAsset> assets = List.of(
                new DrainageAsset("DRAIN_BARAPULLAH_01", DrainageAssetType.SLUICE_GATE, "Barapullah Outfall Drain Sluice A",
                        "Nizamuddin Outfall", 28.5890, 77.2510, south, 12500.0, AssetStatus.OPERATIONAL, false,
                        "Primary south basin outfall discharging to Yamuna."),

                new DrainageAsset("DRAIN_NAJAFGARH_01", DrainageAssetType.SLUICE_GATE, "Najafgarh Basin Trunk Drain Sluice 01",
                        "Timarpur / Wazirabad Outfall", 28.7120, 77.2180, nw, 25000.0, AssetStatus.OPERATIONAL, false,
                        "Largest natural drainage channel draining West & North-West Delhi."),

                new DrainageAsset("DRAIN_MINTO_PUMP_01", DrainageAssetType.PUMPING_STATION, "Minto Road Dewatering Station",
                        "Minto Road Underpass Sump", 28.6348, 77.2264, central, 450.0, AssetStatus.OPERATIONAL, false,
                        "Automatic submersible sump pump station with 3x high-discharge units.")
        );
        drainRepo.saveAll(assets);
        log.info("Seeded {} authoritative stormwater drainage assets.", assets.size());
    }

    @Transactional(readOnly = true)
    public List<DrainageAsset> getAllDrainageAssets() {
        return drainRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<RoadSegment> getAllRoadSegments() {
        return roadRepo.findAll();
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "HASH_" + Long.toHexString(System.currentTimeMillis());
        }
    }
}
