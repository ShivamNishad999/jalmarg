package org.growforge.jalmarg.model;

/**
 * Categorization of environmental and crowd data streams for provenance tracking.
 */
public final class ProvenanceEnums {
    private ProvenanceEnums() {}

    /** Fundamental nature and verification state of the data point. */
    public enum SourceCategory {
        VERIFIED_SENSOR_OBSERVATION,
        UNVERIFIED_CITIZEN_REPORT,
        SATELLITE_RAINFALL_OBSERVATION,
        WEATHER_FORECAST,
        TERRAIN_RASTER_OBSERVATION,
        SYNTHETIC_DEMO,
        SYNTHETIC_REFERENCE,
        HISTORICAL_OBSERVATION,
        MODEL_DERIVED,

        // Legacy aliases
        LIVE_OBSERVATION,
        CITIZEN_REPORTED,
        SATELLITE_OBSERVATION,
        TERRAIN_OBSERVATION,
        DEMO_SYNTHETIC
    }

    /** Verified upstream provider or authority. */
    public enum ProviderName {
        OPEN_METEO,
        NASA_GES_DISC,
        COPERNICUS_ESA,
        CITIZEN_CROWDSOURCE,
        DEMO_FIXTURE,
        JALMARG_ENGINE
    }
}
