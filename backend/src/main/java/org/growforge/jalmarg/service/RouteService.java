package org.growforge.jalmarg.service;

import org.growforge.jalmarg.dto.Dtos.*;
import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.model.Report;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The frontend asks an open router (OSRM) for up to 3 alternative routes, then sends
 * their geometry here. We check every active flood report against every route and
 * recommend the one with the least risk.
 */
@Service
public class RouteService {

    private final ReportService reportService;
    private final org.growforge.jalmarg.repo.FloodRiskAssessmentRepository riskRepo;

    public RouteService(ReportService reportService, org.growforge.jalmarg.repo.FloodRiskAssessmentRepository riskRepo) {
        this.reportService = reportService;
        this.riskRepo = riskRepo;
    }

    public RouteEvaluateResponse evaluate(RouteEvaluateRequest req) {
        double buffer = req.bufferMeters() == null ? 60 : Math.max(20, Math.min(300, req.bufferMeters()));
        List<Report> active = reportService.active();
        List<org.growforge.jalmarg.model.FloodRiskAssessment> activeRisks =
                riskRepo.findByExpiresAtAfterOrderByRiskScoreDesc(java.time.Instant.now());

        List<RouteResult> results = new ArrayList<>();
        List<List<double[]>> routes = req.routes() == null ? List.of() : req.routes();

        for (int i = 0; i < routes.size(); i++) {
            List<double[]> pts = routes.get(i);
            List<RouteHazard> hazards = new ArrayList<>();
            double routeRiskScore = 0.0;
            double maxDepth = 0.0;
            java.util.Set<Long> observedHotspots = new java.util.HashSet<>();

            // 1. Evaluate Observed Ground Hazards (Active Reports)
            for (Report r : active) {
                if (r.getSeverity() == Severity.LOW) continue; // Shallow puddles don't block routes
                double d = distanceToPolyline(r.getLatitude(), r.getLongitude(), pts);
                if (d <= buffer) {
                    if (r.getHotspot() != null) {
                        observedHotspots.add(r.getHotspot().getId());
                    }
                    hazards.add(new RouteHazard(
                            r.getId(),
                            r.getLocationName(),
                            r.getLatitude(),
                            r.getLongitude(),
                            r.getDepthFt(),
                            r.getSeverity().name(),
                            Math.round(d),
                            "OBSERVED"));

                    double baseWeight = SafetyRules.riskWeight(r.getSeverity());
                    double confirmMultiplier = 1.0 + Math.min(r.getConfirmations(), 5) * 0.1;
                    routeRiskScore += baseWeight * confirmMultiplier;
                    maxDepth = Math.max(maxDepth, r.getDepthFt());
                }
            }

            // 2. Evaluate Autonomous Predicted Risks along Route (without double-counting observed spots)
            for (org.growforge.jalmarg.model.FloodRiskAssessment ra : activeRisks) {
                if (ra.getStatus() != org.growforge.jalmarg.model.Enums.RiskStatus.PREDICTED_RISK) continue;
                if (ra.getRiskLevel() != org.growforge.jalmarg.model.Enums.RiskLevel.HIGH
                        && ra.getRiskLevel() != org.growforge.jalmarg.model.Enums.RiskLevel.CRITICAL) continue;
                if (ra.getHotspotId() != null && observedHotspots.contains(ra.getHotspotId())) continue;

                double d = distanceToPolyline(ra.getLatitude(), ra.getLongitude(), pts);
                if (d <= buffer) {
                    String sev = ra.getRiskLevel() == org.growforge.jalmarg.model.Enums.RiskLevel.CRITICAL ? "HIGH" : "MODERATE";
                    hazards.add(new RouteHazard(
                            null,
                            "[Forecast Risk] " + ra.getHotspotName(),
                            ra.getLatitude(),
                            ra.getLongitude(),
                            0.0,
                            sev,
                            Math.round(d),
                            "PREDICTED"));

                    // Predicted risk adds calibrated hazard weight
                    routeRiskScore += (ra.getRiskLevel() == org.growforge.jalmarg.model.Enums.RiskLevel.CRITICAL ? 4.0 : 2.0);
                }
            }

            hazards.sort(Comparator.comparingDouble(RouteHazard::depthFt).reversed()
                    .thenComparing(RouteHazard::distanceFromRouteM));

            results.add(new RouteResult(
                    i,
                    hazards.size(),
                    Math.round(routeRiskScore * 10.0) / 10.0,
                    maxDepth < SafetyRules.BIKE_LIMIT_FT,
                    maxDepth < SafetyRules.CAR_LIMIT_FT,
                    hazards));
        }

        // Lowest risk wins; on a tie the router's own order (fastest first) wins.
        int best = results.stream()
                .min(Comparator.comparingDouble(RouteResult::riskScore).thenComparingInt(RouteResult::index))
                .map(RouteResult::index)
                .orElse(-1);

        return new RouteEvaluateResponse(best, results);
    }

    static double distanceToPolyline(double lat, double lng, List<double[]> pts) {
        if (pts == null || pts.isEmpty()) return Double.MAX_VALUE;
        if (pts.size() == 1) return Geo.distanceM(lat, lng, pts.get(0)[0], pts.get(0)[1]);
        double best = Double.MAX_VALUE;
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            // quick reject: skip segments far away (> ~2 km box)
            if (Math.abs(a[0] - lat) > 0.02 && Math.abs(b[0] - lat) > 0.02
                    && Math.signum(a[0] - lat) == Math.signum(b[0] - lat)) continue;
            best = Math.min(best, Geo.pointToSegmentM(lat, lng, a[0], a[1], b[0], b[1]));
        }
        return best;
    }
}
