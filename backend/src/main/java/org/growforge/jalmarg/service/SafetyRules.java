package org.growforge.jalmarg.service;

import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.model.Report;

/**
 * Turns a water depth into plain advice. Thresholds are conservative commuter guidance:
 * - two-wheeler: exhaust sits ~1 ft up, engines stall well before that, so > 0.5 ft is unsafe
 * - car: water above ~0.8 ft can reach the air intake / stall small cars
 * - walking: above ~1.5 ft (knee) open drains and manholes become invisible and dangerous
 */
public final class SafetyRules {
    private SafetyRules() {}

    public static final double BIKE_LIMIT_FT = 0.5;
    public static final double CAR_LIMIT_FT = 0.8;
    public static final double WALK_LIMIT_FT = 1.5;

    public static Severity severityFor(double depthFt) {
        if (depthFt < 0.3) return Severity.LOW;
        if (depthFt < 0.8) return Severity.MODERATE;
        if (depthFt < 1.5) return Severity.HIGH;
        return Severity.SEVERE;
    }

    public static void apply(Report r) {
        double d = r.getDepthFt();
        r.setSeverity(severityFor(d));
        r.setBikeSafe(d < BIKE_LIMIT_FT);
        r.setCarSafe(d < CAR_LIMIT_FT);
        r.setWalkSafe(d < WALK_LIMIT_FT);
    }

    /** Weight used to score routes: deeper water costs more. */
    public static double riskWeight(Severity s) {
        return switch (s) {
            case LOW -> 1;
            case MODERATE -> 3;
            case HIGH -> 8;
            case SEVERE -> 20;
        };
    }
}
