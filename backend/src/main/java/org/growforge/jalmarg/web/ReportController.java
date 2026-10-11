package org.growforge.jalmarg.web;

import org.growforge.jalmarg.dto.Dtos.ReportView;
import org.growforge.jalmarg.model.Enums.DepthLevel;
import org.growforge.jalmarg.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Citizen files a report. multipart/form-data:
     * photo (optional file), latitude, longitude, locationName, area, depthLevel (PATCHY|ANKLE|SHIN|KNEE|WAIST), note
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportView create(@RequestParam double latitude,
                             @RequestParam double longitude,
                             @RequestParam(required = false) String locationName,
                             @RequestParam(required = false) String area,
                             @RequestParam(required = false) String depthLevel,
                             @RequestParam(required = false) String note,
                             @RequestPart(value = "photo", required = false) MultipartFile photo,
                             jakarta.servlet.http.HttpServletRequest req) throws IOException {
        DepthLevel level = null;
        if (depthLevel != null && !depthLevel.isBlank()) {
            level = DepthLevel.valueOf(depthLevel.trim().toUpperCase(Locale.ROOT));
        }
        byte[] bytes = null;
        String type = null;
        if (photo != null && !photo.isEmpty()) {
            type = photo.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new IllegalArgumentException("Photo must be an image");
            }
            bytes = photo.getBytes();
        }
        String clientIp = resolveClientIp(req);
        return ReportView.of(reportService.create(new ReportService.NewReport(
                latitude, longitude, locationName, area, level, note, bytes, type, clientIp)));
    }

    /** All reports currently active on the map. Optional lat/lng/radiusKm to filter. */
    @GetMapping("/active")
    public List<ReportView> active(@RequestParam(required = false) Double lat,
                                   @RequestParam(required = false) Double lng,
                                   @RequestParam(defaultValue = "50") double radiusKm) {
        var list = (lat != null && lng != null)
                ? reportService.activeNear(lat, lng, radiusKm)
                : reportService.active();
        return list.stream().map(ReportView::of).toList();
    }

    /** Reports from the last N days (any status), for history views. */
    @GetMapping("/recent")
    public List<ReportView> recent(@RequestParam(defaultValue = "7") int days) {
        return reportService.recent(Math.max(1, Math.min(120, days))).stream().map(ReportView::of).toList();
    }

    @GetMapping("/{id}")
    public ReportView get(@PathVariable long id) {
        return ReportView.of(reportService.get(id));
    }

    /** "Still flooded" from someone passing by. Throttled against rapid repeat spam. */
    @PostMapping("/{id}/confirm")
    public ReportView confirm(@PathVariable long id, jakarta.servlet.http.HttpServletRequest req) {
        String clientIp = resolveClientIp(req);
        return ReportView.of(reportService.confirm(id, clientIp));
    }

    /** "Water has cleared" from someone passing by. Throttled against rapid repeat spam. */
    @PostMapping("/{id}/clear")
    public ReportView clear(@PathVariable long id, jakarta.servlet.http.HttpServletRequest req) {
        String clientIp = resolveClientIp(req);
        return ReportView.of(reportService.clear(id, clientIp));
    }

    private static String resolveClientIp(jakarta.servlet.http.HttpServletRequest req) {
        if (req == null) return "unknown";
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr() != null ? req.getRemoteAddr() : "unknown";
    }
}
