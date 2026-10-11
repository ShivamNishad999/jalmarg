package org.growforge.jalmarg.model;

/** Small enums kept together for readability. */
public final class Enums {
    private Enums() {}

    /** Depth a citizen picks with a body / vehicle reference. Values in feet. */
    public enum DepthLevel {
        PATCHY(0.15),   // puddles, road visible
        ANKLE(0.35),    // ankle deep, below bike silencer
        SHIN(0.8),      // mid-shin, half a bike tyre, car tyre half covered
        KNEE(1.5),      // knee deep, bike engine under water
        WAIST(3.0);     // waist deep or more

        public final double feet;
        DepthLevel(double feet) { this.feet = feet; }
    }

    public enum Severity { LOW, MODERATE, HIGH, SEVERE }

    /** Where the depth number came from. */
    public enum DepthSource { AI, CITIZEN, AI_AND_CITIZEN, DEMO }

    public enum ReportStatus { ACTIVE, CLEARED, EXPIRED }

    /** Autonomous risk assessment levels. */
    public enum RiskLevel { LOW, MODERATE, HIGH, CRITICAL, UNKNOWN }

    /** Distinction between actual confirmed waterlogging and forecast-based potential risk. */
    public enum RiskStatus { OBSERVED_FLOODING, PREDICTED_RISK, NO_IMMEDIATE_RISK, UNKNOWN }

    /** Data quality state of underlying weather/telemetry. */
    public enum DataQuality { FRESH, STALE, MISSING_TELEMETRY }
}
