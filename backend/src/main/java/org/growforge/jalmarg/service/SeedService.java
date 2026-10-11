package org.growforge.jalmarg.service;

import org.growforge.jalmarg.model.Enums.DepthSource;
import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.growforge.jalmarg.repo.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Demo data so the map and dashboard are not empty on first run.
 * Locations are well-known recurring waterlogging spots in Delhi NCR (coordinates approximate).
 * Report counts and depths are ILLUSTRATIVE, generated for the demo, and every seeded
 * report is tagged DepthSource.DEMO so the UI can label it.
 */
@Service
public class SeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** name, area, lat, lng, how flood-prone (1..5) */
    private record Spot(String name, String area, double lat, double lng, int proneness) {}

    private static final List<Spot> SPOTS = List.of(
            new Spot("Minto Bridge underpass, Connaught Place", "Delhi", 28.6347, 77.2263, 5),
            new Spot("Pul Prahladpur underpass, MB Road", "Delhi", 28.5019, 77.2873, 5),
            new Spot("Zakhira underpass", "Delhi", 28.6612, 77.1455, 4),
            new Spot("Azad Market underpass", "Delhi", 28.6667, 77.2064, 4),
            new Spot("ITO crossing, Vikas Marg", "Delhi", 28.6289, 77.2410, 3),
            new Spot("Dhaula Kuan", "Delhi", 28.5917, 77.1611, 3),
            new Spot("Mehrauli-Badarpur Road, Saket", "Delhi", 28.5246, 77.2134, 3),
            new Spot("Moolchand underpass, Ring Road", "Delhi", 28.5640, 77.2340, 3),
            new Spot("Rohtak Road, Nangloi", "Delhi", 28.6810, 77.0640, 3),
            new Spot("Anand Vihar ISBT road", "Delhi", 28.6469, 77.3160, 2),
            new Spot("NH-9 underpass near Sector 62", "Noida", 28.6280, 77.3690, 4),
            new Spot("Sector 18 / Atta Market", "Noida", 28.5700, 77.3260, 3),
            new Spot("Sector 52 metro station road", "Noida", 28.5865, 77.3630, 2),
            new Spot("Mahamaya flyover underpass", "Noida", 28.5455, 77.3340, 3),
            new Spot("Pari Chowk", "Greater Noida", 28.4660, 77.5130, 2),
            new Spot("Knowledge Park II main road", "Greater Noida", 28.4625, 77.4905, 3),
            new Spot("Meerut Road, near Hindon", "Ghaziabad", 28.6780, 77.4400, 2),
            new Spot("Hero Honda Chowk", "Gurugram", 28.4296, 77.0118, 5),
            new Spot("Narsinghpur, NH-48 service road", "Gurugram", 28.4120, 76.9880, 4),
            new Spot("Golf Course Road, Sector 54", "Gurugram", 28.4350, 77.1050, 3)
    );

    /** Spots that are shown as "flooded right now" in the demo, with depth in feet. */
    private static final double[][] LIVE_DEMO = {
            // index into SPOTS, depth ft
            {0, 2.2}, {1, 1.6}, {15, 0.7}, {11, 0.6}, {17, 1.9}, {10, 1.1}
    };

    private final HotspotRepository hotspots;
    private final ReportRepository reports;
    private final boolean enabled;

    public SeedService(HotspotRepository hotspots, ReportRepository reports,
                       @Value("${jalmarg.seed.enabled}") boolean enabled) {
        this.hotspots = hotspots;
        this.reports = reports;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || hotspots.count() > 0) return;
        Random rnd = new Random(42);
        List<Hotspot> created = new ArrayList<>();
        List<Report> rs = new ArrayList<>();

        // Historical reports across this monsoon (1 Jul - 25 Sep), more for flood-prone spots
        LocalDate start = LocalDate.of(LocalDate.now(IST).getYear(), 7, 1);
        int seasonDays = 86;
        for (Spot s : SPOTS) {
            Hotspot h = new Hotspot(s.name(), s.area(), s.lat(), s.lng());
            int n = 2 + s.proneness() * 2 + rnd.nextInt(3);
            Instant first = null, last = null;
            for (int i = 0; i < n; i++) {
                LocalDateTime t = start.plusDays(rnd.nextInt(seasonDays))
                        .atTime(6 + rnd.nextInt(16), rnd.nextInt(60));
                Instant at = t.atZone(IST).toInstant();
                double depth = Math.round((0.3 + rnd.nextDouble() * (0.4 + s.proneness() * 0.45)) * 10) / 10.0;
                Report r = demoReport(s, depth, at, ReportStatus.EXPIRED, rnd);
                r.setHotspot(h);
                rs.add(r);
                if (first == null || at.isBefore(first)) first = at;
                if (last == null || at.isAfter(last)) last = at;
            }
            h.setTotalReports(n);
            h.setFirstSeen(first);
            h.setLastSeen(last);
            created.add(h);
        }
        hotspots.saveAll(created);
        reports.saveAll(rs);
        refreshLiveDemo();
        log.info("Seeded {} demo hotspots and {} demo reports", created.size(), rs.size() + LIVE_DEMO.length);
    }

    /** Puts a handful of "live" demo reports on the map (used on first start and by /api/admin/demo-refresh). */
    @Transactional
    public int refreshLiveDemo() {
        // close any older live demo reports
        List<Report> oldLive = reports.findByStatusOrderByCreatedAtDesc(ReportStatus.ACTIVE).stream()
                .filter(r -> r.getDepthSource() == DepthSource.DEMO).toList();
        oldLive.forEach(r -> r.setStatus(ReportStatus.EXPIRED));
        reports.saveAll(oldLive);

        Random rnd = new Random();
        List<Hotspot> all = hotspots.findAll();
        int added = 0;
        for (double[] live : LIVE_DEMO) {
            Spot s = SPOTS.get((int) live[0]);
            Hotspot h = all.stream().filter(x -> x.getName().equals(s.name())).findFirst().orElse(null);
            if (h == null) continue;
            Instant at = Instant.now().minus(Duration.ofMinutes(10 + rnd.nextInt(80)));
            Report r = demoReport(s, live[1], at, ReportStatus.ACTIVE, rnd);
            r.setHotspot(h);
            r.setConfirmations(rnd.nextInt(6));
            h.setTotalReports(h.getTotalReports() + 1);
            h.setLastSeen(at);
            hotspots.save(h);
            reports.save(r);
            added++;
        }
        return added;
    }

    private static Report demoReport(Spot s, double depth, Instant at, ReportStatus status, Random rnd) {
        Report r = new Report();
        // jitter within ~40 m so markers don't sit exactly on top of each other
        r.setLatitude(s.lat() + (rnd.nextDouble() - 0.5) * 0.0007);
        r.setLongitude(s.lng() + (rnd.nextDouble() - 0.5) * 0.0007);
        r.setLocationName(s.name());
        r.setDepthFt(depth);
        r.setDepthSource(DepthSource.DEMO);
        r.setCreatedAt(at);
        r.setLastConfirmedAt(at);
        r.setStatus(status);
        r.setNote("Demo data");
        SafetyRules.apply(r);
        return r;
    }
}
