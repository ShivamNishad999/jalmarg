package org.growforge.jalmarg.service;

import org.growforge.jalmarg.dto.Dtos.HotspotView;
import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.growforge.jalmarg.repo.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class HotspotService {

    /** Reports within this distance belong to the same hotspot. */
    public static final double MERGE_RADIUS_M = 250;

    private final HotspotRepository hotspots;
    private final ReportRepository reports;

    public HotspotService(HotspotRepository hotspots, ReportRepository reports) {
        this.hotspots = hotspots;
        this.reports = reports;
    }

    /** Nearest hotspot within MERGE_RADIUS_M, or null. */
    public Hotspot findNear(double lat, double lng) {
        double dLat = Geo.metersToLatDeg(MERGE_RADIUS_M);
        double dLng = Geo.metersToLngDeg(MERGE_RADIUS_M, lat);
        return hotspots.findByLatitudeBetweenAndLongitudeBetween(lat - dLat, lat + dLat, lng - dLng, lng + dLng)
                .stream()
                .filter(h -> Geo.distanceM(lat, lng, h.getLatitude(), h.getLongitude()) <= MERGE_RADIUS_M)
                .min(Comparator.comparingDouble(h -> Geo.distanceM(lat, lng, h.getLatitude(), h.getLongitude())))
                .orElse(null);
    }

    /** Attach a report to a hotspot (existing or new) and bump its counters. */
    @Transactional
    public Hotspot attach(double lat, double lng, String name, String area, Instant at) {
        Hotspot h = findNear(lat, lng);
        if (h == null) {
            String n = (name == null || name.isBlank())
                    ? String.format("Spot %.4f, %.4f", lat, lng) : name.trim();
            h = new Hotspot(n, area == null || area.isBlank() ? guessArea(lat, lng) : area, lat, lng);
            h.setFirstSeen(at);
        }
        h.setTotalReports(h.getTotalReports() + 1);
        if (h.getLastSeen() == null || at.isAfter(h.getLastSeen())) h.setLastSeen(at);
        if (h.getFirstSeen() == null || at.isBefore(h.getFirstSeen())) h.setFirstSeen(at);
        return hotspots.save(h);
    }

    @Transactional(readOnly = true)
    public List<HotspotView> all() {
        return hotspots.findAllByOrderByTotalReportsDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public HotspotView view(Hotspot h) {
        Instant monthAgo = Instant.now().minus(Duration.ofDays(30));
        List<Report> rs = reports.findByHotspotIdOrderByCreatedAtDesc(h.getId());
        int last30 = (int) rs.stream().filter(r -> r.getCreatedAt().isAfter(monthAgo)).count();
        double maxDepth = rs.stream().mapToDouble(Report::getDepthFt).max().orElse(0);
        boolean active = rs.stream().anyMatch(r -> r.getStatus() == ReportStatus.ACTIVE);
        return new HotspotView(h.getId(), h.getName(), h.getArea(), h.getLatitude(), h.getLongitude(),
                h.getTotalReports(), last30, maxDepth, active, h.getFirstSeen(), h.getLastSeen());
    }

    /**
     * Very rough NCR area guess from coordinates, used only when the client
     * does not send an area. Good enough to route a complaint to the right body.
     */
    public static String guessArea(double lat, double lng) {
        if (lng < 77.13 && lat < 28.52) return "Gurugram";
        if (lng > 77.40 && lat < 28.52) return "Greater Noida";
        if (lng > 77.40) return "Ghaziabad";
        if (lng > 77.30 && lat < 28.64) return "Noida";
        return "Delhi";
    }
}
