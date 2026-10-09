package org.growforge.jalmarg.service;

import org.growforge.jalmarg.dto.Dtos.*;
import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.growforge.jalmarg.repo.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Numbers for the authority dashboard. */
@Service
public class DashboardService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ReportRepository reports;
    private final HotspotRepository hotspots;
    private final HotspotService hotspotService;

    public DashboardService(ReportRepository reports, HotspotRepository hotspots, HotspotService hotspotService) {
        this.reports = reports;
        this.hotspots = hotspots;
        this.hotspotService = hotspotService;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(String area, int days) {
        List<Report> all = reports.findAll().stream()
                .filter(r -> area == null || area.isBlank()
                        || (r.getHotspot() != null && area.equalsIgnoreCase(r.getHotspot().getArea())))
                .toList();

        Instant since = Instant.now().minus(Duration.ofDays(days));
        List<Report> window = all.stream().filter(r -> r.getCreatedAt().isAfter(since)).toList();

        // Reports per day across the window (zero-filled), in IST
        Map<LocalDate, Long> counts = window.stream().collect(
                Collectors.groupingBy(r -> r.getCreatedAt().atZone(IST).toLocalDate(), Collectors.counting()));
        List<DayCount> perDay = new ArrayList<>();
        LocalDate today = LocalDate.now(IST);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            perDay.add(new DayCount(d.toString(), counts.getOrDefault(d, 0L)));
        }

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (String s : List.of("LOW", "MODERATE", "HIGH", "SEVERE")) bySeverity.put(s, 0L);
        window.forEach(r -> bySeverity.merge(r.getSeverity().name(), 1L, Long::sum));

        Map<String, Long> byArea = window.stream()
                .filter(r -> r.getHotspot() != null)
                .collect(Collectors.groupingBy(r -> r.getHotspot().getArea(), TreeMap::new, Collectors.counting()));

        List<HotspotView> top = hotspotService.all().stream()
                .filter(h -> area == null || area.isBlank() || area.equalsIgnoreCase(h.area()))
                .limit(10).toList();

        long active = all.stream().filter(r -> r.getStatus() == ReportStatus.ACTIVE).count();
        long hotspotCount = area == null || area.isBlank() ? hotspots.count()
                : hotspots.findAll().stream().filter(h -> area.equalsIgnoreCase(h.getArea())).count();
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        long last7 = all.stream().filter(r -> r.getCreatedAt().isAfter(weekAgo)).count();

        return new DashboardSummary(all.size(), active, hotspotCount, last7, perDay, bySeverity, byArea, top);
    }
}
