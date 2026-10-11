package org.growforge.jalmarg.model;

import org.growforge.jalmarg.model.Enums.DataQuality;
import org.growforge.jalmarg.model.ProvenanceEnums.ProviderName;
import org.growforge.jalmarg.model.ProvenanceEnums.SourceCategory;

import java.time.Instant;

/**
 * Immutable environmental source provenance record.
 * Tracks every observation or forecast input to ensure scientific integrity and traceability.
 */
public record SourceProvenance(
        ProviderName provider,
        String product,
        SourceCategory sourceCategory,
        Instant observedAt,
        Instant forecastValidAt,
        Instant fetchedAt,
        double latitude,
        double longitude,
        double coverageRadiusMeters,
        String unit,
        Double rawValue,
        String derivedValue,
        DataQuality dataQuality,
        boolean isSynthetic,
        String expirationPolicy
) {
    public static SourceProvenance syntheticDemo(String product, double lat, double lng, String unit, double value) {
        Instant now = Instant.now();
        return new SourceProvenance(
                ProviderName.DEMO_FIXTURE,
                product,
                SourceCategory.DEMO_SYNTHETIC,
                now,
                null,
                now,
                lat,
                lng,
                100.0,
                unit,
                value,
                "DEMO_ILLUSTRATIVE",
                DataQuality.FRESH,
                true,
                "SESSION_ONLY"
        );
    }
}
