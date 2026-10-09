package org.growforge.jalmarg.service;

import org.growforge.jalmarg.model.Enums.DepthLevel;
import org.growforge.jalmarg.model.Enums.DepthSource;
import org.growforge.jalmarg.model.Enums.ReportStatus;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reports;
    private final HotspotService hotspotService;
    private final StorageService storage;
    private final AiDepthService ai;
    private final int ttlHours;

    public ReportService(ReportRepository reports, HotspotService hotspotService,
                         StorageService storage, AiDepthService ai,
                         @Value("${jalmarg.reports.ttl-hours}") int ttlHours) {
        this.reports = reports;
        this.hotspotService = hotspotService;
        this.storage = storage;
        this.ai = ai;
        this.ttlHours = ttlHours;
    }

    public int getTtlHours() {
        return ttlHours;
    }

    /** Input for a new report (photo optional, depth level optional, but at least one needed). */
    public record NewReport(double latitude, double longitude, String locationName, String area,
                            DepthLevel depthLevel, String note,
                            byte[] photo, String photoContentType) {}

    @Transactional
    public Report create(NewReport in) throws IOException {
        if (in.photo() == null && in.depthLevel() == null) {
            throw new IllegalArgumentException("Send a photo or pick a depth level");
        }
        if (Math.abs(in.latitude()) > 90 || Math.abs(in.longitude()) > 180) {
            throw new IllegalArgumentException("Invalid location");
        }

        Instant now = Instant.now();
        Report r = new Report();
        r.setLatitude(in.latitude());
        r.setLongitude(in.longitude());
        r.setLocationName(trim(in.locationName(), 200));
        r.setNote(trim(in.note(), 500));
        r.setCitizenDepthLevel(in.depthLevel());
        r.setCreatedAt(now);
        r.setLastConfirmedAt(now);
        r.setStatus(ReportStatus.ACTIVE);

        if (in.photo() != null && in.photo().length > 0) {
            r.setPhotoKey(storage.save(in.photo(), in.photoContentType()));
        }

        // Depth: combine the AI estimate with what the citizen picked.
        AiDepthService.AiResult aiResult = ai.analyze(in.photo(), in.photoContentType());
        fuseDepth(r, in.depthLevel(), aiResult);
        SafetyRules.apply(r);

        Hotspot h = hotspotService.attach(in.latitude(), in.longitude(), in.locationName(), in.area(), now);
        r.setHotspot(h);
        if (r.getLocationName() == null || r.getLocationName().isBlank()) r.setLocationName(h.getName());

        Report saved = reports.save(r);
        log.info("New report #{} at {} depth={}ft source={} severity={}",
                saved.getId(), saved.getLocationName(), saved.getDepthFt(),
                saved.getDepthSource(), saved.getSeverity());
        return saved;
    }

    /**
     * Rules:
     * - AI only            -> AI depth
     * - citizen only       -> citizen depth
     * - both, AI confident -> weighted mix (AI 60%, citizen 40%)
     * - both, AI unsure or says "no water" -> trust the citizen, keep AI note
     */
    static void fuseDepth(Report r, DepthLevel level, AiDepthService.AiResult aiResult) {
        boolean hasAi = aiResult != null && aiResult.waterVisible();
        if (aiResult != null) {
            r.setAiConfidence(aiResult.confidence());
            String ref = aiResult.reference() == null || aiResult.reference().isBlank()
                    ? "" : " (judged from: " + aiResult.reference() + ")";
            r.setAiSummary(trim(aiResult.summaryEn() + ref, 1000));
            r.setAiSummaryHi(trim(aiResult.summaryHi(), 1000));
        }

        if (hasAi && level == null) {
            r.setDepthFt(aiResult.depthFt());
            r.setDepthSource(DepthSource.AI);
        } else if (hasAi && aiResult.confidence() >= 0.5) {
            double mixed = 0.6 * aiResult.depthFt() + 0.4 * level.feet;
            r.setDepthFt(Math.round(mixed * 10) / 10.0);
            r.setDepthSource(DepthSource.AI_AND_CITIZEN);
        } else if (level != null) {
            r.setDepthFt(level.feet);
            r.setDepthSource(DepthSource.CITIZEN);
        } else {
            // photo only, AI off or saw no water: record as shallow, unverified
            r.setDepthFt(aiResult != null ? aiResult.depthFt() : 0.3);
            r.setDepthSource(aiResult != null ? DepthSource.AI : DepthSource.CITIZEN);
        }
    }

    @Transactional(readOnly = true)
    public List<Report> active() {
        return reports.findByStatusOrderByCreatedAtDesc(ReportStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Report> activeNear(double lat, double lng, double radiusKm) {
        return active().stream()
                .filter(r -> Geo.distanceM(lat, lng, r.getLatitude(), r.getLongitude()) <= radiusKm * 1000)
                .sorted(Comparator.comparingDouble(r -> Geo.distanceM(lat, lng, r.getLatitude(), r.getLongitude())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Report> recent(int days) {
        return reports.findByCreatedAtAfterOrderByCreatedAtDesc(Instant.now().minus(Duration.ofDays(days)));
    }

    @Transactional(readOnly = true)
    public Report get(long id) {
        return reports.findById(id).orElseThrow(() -> new IllegalArgumentException("Report not found"));
    }

    /** Someone passing by says "still flooded". Keeps the report alive. */
    @Transactional
    public Report confirm(long id) {
        Report r = get(id);
        r.setConfirmations(r.getConfirmations() + 1);
        r.setLastConfirmedAt(Instant.now());
        if (r.getStatus() != ReportStatus.ACTIVE) {
            r.setStatus(ReportStatus.ACTIVE);
            r.setClearedAt(null);
        }
        return reports.save(r);
    }

    /** Someone passing by says "water has cleared". */
    @Transactional
    public Report clear(long id) {
        Report r = get(id);
        r.setStatus(ReportStatus.CLEARED);
        r.setClearedAt(Instant.now());
        return reports.save(r);
    }

    /** Every 10 minutes: reports nobody confirmed for TTL hours drop off the live map. */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    public void expireStale() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(ttlHours));
        List<Report> stale = reports.findStale(ReportStatus.ACTIVE, cutoff);
        for (Report r : stale) {
            r.setStatus(ReportStatus.EXPIRED);
        }
        if (!stale.isEmpty()) {
            reports.saveAll(stale);
            log.info("Expired {} stale reports", stale.size());
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
