package org.growforge.jalmarg.web;

import org.growforge.jalmarg.dto.Dtos.*;
import org.growforge.jalmarg.service.*;
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
    private final FloodRiskAssessmentService riskService;
    private final AutonomousFloodMonitorService monitorService;
    private final String runKey;
    private final String aiModel;

    public ApiController(RouteService routeService, AlertService alertService, HotspotService hotspotService,
                         DashboardService dashboardService, PdfReportService pdfReportService,
                         StorageService storage, WeatherService weather, AiDepthService ai,
                         ReportService reportService, SeedService seedService,
                         FloodRiskAssessmentService riskService,
                         AutonomousFloodMonitorService monitorService,
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
        this.riskService = riskService;
        this.monitorService = monitorService;
        this.runKey = runKey;
        this.aiModel = aiModel;
    }

    // ------------------------------------------------------------- autonomous flood risk

    @GetMapping("/flood-risk")
    public FloodRiskView getFloodRisk(@RequestParam double lat, @RequestParam double lng,
                                      @RequestParam(required = false) String area,
                                      @RequestParam(required = false) Long hotspotId) {
        return FloodRiskView.of(riskService.assess(lat, lng, area, hotspotId, null));
    }

    @GetMapping("/flood-risk/hotspots")
    public List<FloodRiskView> getHotspotsRisk() {
        return riskService.getAllActive().stream().map(FloodRiskView::of).toList();
    }

    @GetMapping("/flood-risk/status")
    public FloodRiskStatusView getMonitorStatus() {
        return monitorService.getStatus();
    }

    @PostMapping("/flood-risk/run")
    public ResponseEntity<Map<String, Object>> runMonitorCycle(@RequestHeader(value = "X-Jalmarg-Key", required = false) String key) {
        if (key == null || !key.equals(runKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "bad key"));
        }
        var results = monitorService.runEvaluationCycle();
        return ResponseEntity.ok(Map.of("evaluated", results.size(), "status", "success"));
    }

    // ------------------------------------------------------------- meta

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

    @PostMapping("/alerts/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView subscribe(@RequestBody SubscribeRequest req) {
        return alertService.subscribe(req);
    }

    @GetMapping("/alerts/subscriptions")
    public List<SubscriptionView> mySubscriptions(@RequestParam String email) {
        return alertService.byEmail(email);
    }

    @DeleteMapping("/alerts/subscriptions/{id}")
    public Map<String, Object> unsubscribe(@PathVariable long id, @RequestParam String email) {
        alertService.unsubscribe(id, email);
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
