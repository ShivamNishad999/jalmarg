package org.growforge.jalmarg.web;

import org.growforge.jalmarg.dto.Dtos.*;
import org.growforge.jalmarg.service.*;
import org.growforge.jalmarg.security.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Routes, alerts, authority dashboard, photos, weather and app info. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final RouteService routeService;
    private final AlertService alertService;
    private final HotspotService hotspotService;
    private final DashboardService dashboardService;
    private final PdfReportService pdfReportService;
    private final StorageService storage;
    private final WeatherService weather;
    private final AiDepthService ai;
    private final ReportService reportService;
    private final SeedService seedService;
    private final MailService mailService;
    private final String runKey;
    private final String aiModel;

    public ApiController(RouteService routeService, AlertService alertService, HotspotService hotspotService,
                         DashboardService dashboardService, PdfReportService pdfReportService,
                         StorageService storage, WeatherService weather, AiDepthService ai,
                         ReportService reportService, SeedService seedService, MailService mailService,
                         @Value("${jalmarg.alerts.run-key}") String runKey,
                         @Value("${jalmarg.ai.model-id}") String aiModel) {
        this.routeService = routeService;
        this.alertService = alertService;
        this.hotspotService = hotspotService;
        this.dashboardService = dashboardService;
        this.pdfReportService = pdfReportService;
        this.storage = storage;
        this.weather = weather;
        this.ai = ai;
        this.reportService = reportService;
        this.seedService = seedService;
        this.mailService = mailService;
        this.runKey = runKey;
        this.aiModel = aiModel;
    }

    // ------------------------------------------------------------- meta

    /**
     * Health check used by the app's "Test connection". Each part is reported separately so the
     * app can tell "server down" from "database down" from "AI not configured".
     */
    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        String db;
        try {
            reportService.countAll();
            db = "UP";
        } catch (Exception e) {
            db = "DOWN";
        }
        Health h = new Health("UP".equals(db) ? "UP" : "DEGRADED", db,
                ai.isEnabled() ? "ENABLED" : "OFF",
                storage.usingS3() ? "S3" : "LOCAL",
                alertService.snsEnabled() ? "SNS" : "LOG_ONLY",
                mailService.mode(), java.time.Instant.now());
        return ResponseEntity.status("UP".equals(db) ? 200 : 503).body(h);
    }

    @GetMapping("/meta")
    public Meta meta() {
        return new Meta(ai.isEnabled(), ai.isEnabled() ? aiModel : null, storage.usingS3(),
                alertService.snsEnabled(), reportService.getTtlHours());
    }

    // ------------------------------------------------------------- routes

    /** Body: {"routes": [[[lat,lng],[lat,lng],...], ...], "bufferMeters": 60} */
    @PostMapping("/routes/evaluate")
    public RouteEvaluateResponse evaluate(@RequestBody RouteEvaluateRequest req) {
        return routeService.evaluate(req);
    }

    // ------------------------------------------------------------- weather + alerts

    @GetMapping("/weather")
    public WeatherService.RainForecast weather(@RequestParam double lat, @RequestParam double lng) {
        return weather.forecast(lat, lng);
    }

    @GetMapping("/alerts/feed")
    public AlertFeed feed(@RequestParam double lat, @RequestParam double lng,
                          @RequestParam(defaultValue = "3") double radiusKm,
                          @RequestParam(defaultValue = "en") String lang) {
        return alertService.feed(lat, lng, Math.max(0.5, Math.min(25, radiusKm)), lang);
    }

    /** Alerts always go to the signed-in account's own (verified) email. */
    @PostMapping("/alerts/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView subscribe(@RequestBody SubscribeRequest req) {
        AuthUser u = AuthUser.current();
        return alertService.subscribe(new SubscribeRequest(u.name(), u.email(), req.label(),
                req.latitude(), req.longitude(), req.radiusKm(), req.lang()));
    }

    @GetMapping("/alerts/subscriptions")
    public List<SubscriptionView> mySubscriptions() {
        return alertService.byEmail(AuthUser.current().email());
    }

    @DeleteMapping("/alerts/subscriptions/{id}")
    public Map<String, Object> unsubscribe(@PathVariable long id) {
        alertService.unsubscribe(id, AuthUser.current().email());
        return Map.of("ok", true);
    }

    /** Called by Amazon EventBridge Scheduler (API destination) with header X-Jalmarg-Key. */
    @PostMapping("/alerts/run")
    public ResponseEntity<Map<String, Object>> runAlerts(@RequestHeader(value = "X-Jalmarg-Key", required = false) String key) {
        if (key == null || !key.equals(runKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad key"));
        }
        return ResponseEntity.ok(Map.of("sent", alertService.runAll()));
    }

    // ------------------------------------------------------------- authority

    @GetMapping("/hotspots")
    public List<HotspotView> hotspots() {
        return hotspotService.all();
    }

    /** AUTHORITY / ADMIN: mark a report resolved (CLEARED) or active again, with a note. */
    @PatchMapping("/authority/reports/{id}")
    public ReportView updateStatus(@PathVariable long id, @RequestBody StatusUpdate body) {
        return ReportView.of(reportService.authorityUpdate(id, body.status(), body.note()));
    }

    @GetMapping("/authority/summary")
    public DashboardSummary summary(@RequestParam(required = false) String area,
                                    @RequestParam(defaultValue = "30") int days) {
        return dashboardService.summary(area, Math.max(1, Math.min(120, days)));
    }

    @GetMapping("/authority/report.pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) String area,
                                      @RequestParam(required = false) Long hotspotId) {
        byte[] pdf = pdfReportService.build(area, hotspotId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jalmarg-waterlogging-report.pdf\"")
                .body(pdf);
    }

    // ------------------------------------------------------------- photos

    @GetMapping("/photos/**")
    public ResponseEntity<byte[]> photo(jakarta.servlet.http.HttpServletRequest request) {
        String key = request.getRequestURI().substring(request.getRequestURI().indexOf("/api/photos/") + 12);
        byte[] bytes = storage.load(key);
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StorageService.contentTypeFor(key)))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic())
                .body(bytes);
    }

    // ------------------------------------------------------------- demo helper

    /** Re-creates the "live now" demo reports (for recording the demo video). */
    @PostMapping("/admin/demo-refresh")
    public ResponseEntity<Map<String, Object>> demoRefresh(@RequestHeader(value = "X-Jalmarg-Key", required = false) String key) {
        if (key == null || !key.equals(runKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad key"));
        }
        return ResponseEntity.ok(Map.of("added", seedService.refreshLiveDemo()));
    }
}
