package org.growforge.jalmarg.dto;

import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;

import java.time.Instant;
import java.util.List;

/** Request / response shapes for the REST API (see docs/API.md). */
public final class Dtos {
    private Dtos() {}

    public record ReportView(
            Long id,
            double latitude,
            double longitude,
            String locationName,
            Long hotspotId,
            String hotspotName,
            String area,
            String photoUrl,
            double depthFt,
            String citizenDepthLevel,
            String severity,
            String depthSource,
            boolean walkSafe,
            boolean bikeSafe,
            boolean carSafe,
            String aiSummary,
            String aiSummaryHi,
            Double aiConfidence,
            String note,
            int confirmations,
            String status,
            Instant createdAt,
            Instant lastConfirmedAt,
            boolean demo,
            String authorityNote,
            Instant statusUpdatedAt,
            int clearVotes,
            int clearVotesNeeded) {

        public static ReportView of(Report r) {
            Hotspot h = r.getHotspot();
            return new ReportView(
                    r.getId(), r.getLatitude(), r.getLongitude(), r.getLocationName(),
                    h == null ? null : h.getId(),
                    h == null ? null : h.getName(),
                    h == null ? null : h.getArea(),
                    r.getPhotoKey() == null ? null : "/api/photos/" + r.getPhotoKey(),
                    r.getDepthFt(),
                    r.getCitizenDepthLevel() == null ? null : r.getCitizenDepthLevel().name(),
                    r.getSeverity() == null ? null : r.getSeverity().name(),
                    r.getDepthSource() == null ? null : r.getDepthSource().name(),
                    r.isWalkSafe(), r.isBikeSafe(), r.isCarSafe(),
                    r.getAiSummary(), r.getAiSummaryHi(), r.getAiConfidence(),
                    r.getNote(), r.getConfirmations(),
                    r.getStatus().name(), r.getCreatedAt(), r.getLastConfirmedAt(),
                    r.getDepthSource() == org.growforge.jalmarg.model.Enums.DepthSource.DEMO,
                    r.getAuthorityNote(), r.getStatusUpdatedAt(),
                    r.getClearVotes(), org.growforge.jalmarg.service.ReportService.clearVotesNeeded());
        }
    }

    public record HotspotView(
            Long id, String name, String area, double latitude, double longitude,
            int totalReports, int reportsLast30Days, double maxDepthFt,
            boolean activeNow, Instant firstSeen, Instant lastSeen) {}

    /** One candidate route from the frontend router: list of [lat, lng] points. */
    public record RouteEvaluateRequest(List<List<double[]>> routes, Double bufferMeters) {}

    public record RouteHazard(Long reportId, String locationName, double latitude, double longitude,
                              double depthFt, String severity, double distanceFromRouteM) {}

    public record RouteResult(int index, int hazardCount, double riskScore,
                              boolean safeForBike, boolean safeForCar, List<RouteHazard> hazards) {}

    public record RouteEvaluateResponse(int recommendedIndex, List<RouteResult> routes) {}

    public record SubscribeRequest(String name, String email, String label,
                                   double latitude, double longitude,
                                   Double radiusKm, String lang) {}

    public record SubscriptionView(Long id, String name, String email, String label,
                                   double latitude, double longitude, double radiusKm,
                                   String lang, boolean emailAlertsActive) {}

    public record AlertItem(String level, String title, String message, Long reportId,
                            Double latitude, Double longitude) {}

    public record AlertFeed(double rainNext6hMm, double maxHourlyMm, String rainLevel,
                            List<AlertItem> alerts) {}

    public record DayCount(String date, long count) {}

    public record DashboardSummary(
            long totalReports, long activeNow, long hotspots, long reportsLast7Days,
            List<DayCount> perDay, java.util.Map<String, Long> bySeverity,
            java.util.Map<String, Long> byArea, List<HotspotView> topHotspots) {}

    public record StatusUpdate(String status, String note) {}

    public record Health(String status, String database, String ai, String storage, String alerts,
                         String email, Instant time) {}

    public record Meta(boolean aiEnabled, String aiModel, boolean s3Storage,
                       boolean snsAlerts, int reportTtlHours) {}
}
