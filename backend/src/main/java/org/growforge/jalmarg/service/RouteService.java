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

    public RouteService(ReportService reportService) {
        this.reportService = reportService;
    }

    public RouteEvaluateResponse evaluate(RouteEvaluateRequest req) {
        double buffer = req.bufferMeters() == null ? 60 : Math.max(20, Math.min(300, req.bufferMeters()));
        List<Report> active = reportService.active();
        List<RouteResult> results = new ArrayList<>();

        List<List<double[]>> routes = req.routes() == null ? List.of() : req.routes();
        for (int i = 0; i < routes.size(); i++) {
            List<double[]> pts = routes.get(i);
            List<RouteHazard> hazards = new ArrayList<>();
            for (Report r : active) {
                if (r.getSeverity() == Severity.LOW) continue; // puddles don't block routes
                double d = distanceToPolyline(r.getLatitude(), r.getLongitude(), pts);
                if (d <= buffer) {
                    hazards.add(new RouteHazard(r.getId(), r.getLocationName(), r.getLatitude(),
                            r.getLongitude(), r.getDepthFt(), r.getSeverity().name(), Math.round(d)));
                }
            }
            hazards.sort(Comparator.comparingDouble(RouteHazard::depthFt).reversed());
            double score = hazards.stream()
                    .mapToDouble(h -> SafetyRules.riskWeight(Severity.valueOf(h.severity()))).sum();
            double maxDepth = hazards.stream().mapToDouble(RouteHazard::depthFt).max().orElse(0);
            results.add(new RouteResult(i, hazards.size(), score,
                    maxDepth < SafetyRules.BIKE_LIMIT_FT, maxDepth < SafetyRules.CAR_LIMIT_FT, hazards));
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
