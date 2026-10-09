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
    private final boolean demoIsolatedMode;

    @org.springframework.beans.factory.annotation.Autowired
    public ReportService(ReportRepository reports, HotspotService hotspotService,
                         StorageService storage, AiDepthService ai,
                         @Value("${jalmarg.reports.ttl-hours}") int ttlHours,
                         @Value("${jalmarg.demo.isolated-mode:false}") boolean demoIsolatedMode) {
        this.reports = reports;
        this.hotspotService = hotspotService;
        this.storage = storage;
        this.ai = ai;
        this.ttlHours = ttlHours;
        this.demoIsolatedMode = demoIsolatedMode;
    }

    public ReportService(ReportRepository reports, HotspotService hotspotService,
                         StorageService storage, AiDepthService ai,
                         int ttlHours) {
        this(reports, hotspotService, storage, ai, ttlHours, false);
    }

    public int getTtlHours() {
        return ttlHours;
    }

    public boolean isDemoIsolatedMode() {
        return demoIsolatedMode;
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

        // Duplicate report detection: within 50m and 20 mins of an existing active report
        List<Report> existingNearby = activeNear(in.latitude(), in.longitude(), 0.05); // 50 meters
        Report duplicateCandidate = existingNearby.stream()
                .filter(ex -> ex.getLastConfirmedAt().isAfter(now.minus(Duration.ofMinutes(20))))
                .findFirst()
                .orElse(null);

        if (duplicateCandidate != null) {
            duplicateCandidate.setConfirmations(duplicateCandidate.getConfirmations() + 1);
            duplicateCandidate.setLastConfirmedAt(now);
            if (in.photo() != null && in.photo().length > 0 && duplicateCandidate.getPhotoKey() == null) {
                duplicateCandidate.setPhotoKey(storage.save(in.photo(), in.photoContentType()));
            }
            if (in.depthLevel() != null && in.depthLevel().feet > duplicateCandidate.getDepthFt()) {
                duplicateCandidate.setDepthFt(in.depthLevel().feet);
                duplicateCandidate.setCitizenDepthLevel(in.depthLevel());
                SafetyRules.apply(duplicateCandidate);
            }
            log.info("Merged duplicate report near {} into report #{}", duplicateCandidate.getLocationName(), duplicateCandidate.getId());
            return reports.save(duplicateCandidate);
        }

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
    /**
     * Rules:
     * - AI only            -> AI depth
     * - citizen only       -> citizen depth
     * - both, AI confident -> weighted mix (AI 60%, citizen 40%)
     * - both, AI unsure or says "no water" -> trust the citizen, keep AI note
     * - photo only, AI off or uncertain   -> 0.0 ft unmeasured visual report (never fabricate 0.3 ft)
     */
    public static void fuseDepth(Report r, DepthLevel level, AiDepthService.AiResult aiResult) {
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
            // Photo only, AI off or saw no water: record as 0.0 unmeasured visual report
            // Does NOT misrepresent an unknown depth as a measured 0.3 ft value
            double depth = (aiResult != null && aiResult.depthFt() > 0) ? aiResult.depthFt() : 0.0;
            r.setDepthFt(depth);
            r.setDepthSource(aiResult != null ? DepthSource.AI : DepthSource.CITIZEN);
            if (r.getAiSummary() == null || r.getAiSummary().isBlank()) {
                r.setAiSummary("Visual observation: depth unmeasured (photo uploaded without depth selection).");
            }
            if (r.getAiSummaryHi() == null || r.getAiSummaryHi().isBlank()) {
                r.setAiSummaryHi("दृश्य अवलोकन: गहराई अनमापी (गहराई चयन के बिना फोटो अपलोड)।");
            }
        }
    }

    private final java.util.Map<String, Instant> actionRateLimit = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<Report> active() {
        return active(false);
    }

    @Transactional(readOnly = true)
    public List<Report> active(boolean forceIncludeDemo) {
        var list = reports.findByStatusOrderByCreatedAtDesc(ReportStatus.ACTIVE);
        if (demoIsolatedMode && !forceIncludeDemo) {
            return list.stream().filter(r -> r.getDepthSource() != DepthSource.DEMO).toList();
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Report> activeNear(double lat, double lng, double radiusKm) {
        return activeNear(lat, lng, radiusKm, false);
    }

    @Transactional(readOnly = true)
    public List<Report> activeNear(double lat, double lng, double radiusKm, boolean forceIncludeDemo) {
        return active(forceIncludeDemo).stream()
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

    /** Someone passing by says "still flooded". Throttles repeat clicks from the same client. */
    @Transactional
    public Report confirm(long id, String clientKey) {
        String key = "confirm:" + id + ":" + (clientKey != null ? clientKey : "anon");
        Instant now = Instant.now();
        Instant last = actionRateLimit.get(key);
        if (last != null && last.isAfter(now.minus(Duration.ofSeconds(15)))) {
            log.warn("Rate-limited confirmation on report #{} from {}", id, clientKey);
            return get(id); // Return existing report without duplicate confirmation
        }
        actionRateLimit.put(key, now);

        Report r = get(id);
        r.setConfirmations(r.getConfirmations() + 1);
        r.setLastConfirmedAt(now);
        if (r.getStatus() != ReportStatus.ACTIVE) {
            r.setStatus(ReportStatus.ACTIVE);
            r.setClearedAt(null);
        }
        return reports.save(r);
    }

    public Report confirm(long id) {
        return confirm(id, "local");
    }

    /** Someone passing by says "water has cleared". Throttles repeat clicks. */
    @Transactional
    public Report clear(long id, String clientKey) {
        String key = "clear:" + id + ":" + (clientKey != null ? clientKey : "anon");
        Instant now = Instant.now();
        Instant last = actionRateLimit.get(key);
        if (last != null && last.isAfter(now.minus(Duration.ofSeconds(15)))) {
            log.warn("Rate-limited clearance on report #{} from {}", id, clientKey);
            return get(id);
        }
        actionRateLimit.put(key, now);

        Report r = get(id);
        r.setStatus(ReportStatus.CLEARED);
        r.setClearedAt(now);
        return reports.save(r);
    }

    public Report clear(long id) {
        return clear(id, "local");
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
