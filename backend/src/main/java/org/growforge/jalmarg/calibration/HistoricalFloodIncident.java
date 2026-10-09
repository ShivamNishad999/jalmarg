package org.growforge.jalmarg.calibration;

import org.growforge.jalmarg.model.ProvenanceEnums.SourceCategory;

import java.time.LocalDate;

/**
 * Immutable historical flood benchmark incident for hydrological model calibration.
 * Represents verified empirical events with documented rainfall and municipal observations.
 */
public record HistoricalFloodIncident(
        String incidentId,
        String locationName,
        String area,
        double latitude,
        double longitude,
        LocalDate eventDate,
        String sourceAgency,
        String sourceCitationUrl,
        String incidentReportRef,
        Double reportedWaterDepthFt,
        String waterDepthMeasurementType, // MEASURED_GAUGE_TELEMETRY, QUALITATIVE_MUNICIPAL_ESTIMATE, UNVERIFIED
        Double observedRainfallMm,
        String rainfallMeasurementType,   // MEASURED_AWS_TELEMETRY, ESTIMATED, UNVERIFIED
        String rainfallStation,
        double copernicusDemElevationMetersAmsl,
        boolean isUnderpassStructure,
        SourceCategory provenanceCategory,
        boolean isCalibratedGroundTruth,
        String verificationNotes
) {}
