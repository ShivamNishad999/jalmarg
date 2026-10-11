package org.growforge.jalmarg.twin.model;

public final class TwinEnums {
    private TwinEnums() {}

    public enum BoundaryType {
        VERIFIED_POLYGON,
        PLANNING_BOUNDING_BOX,
        UNAVAILABLE
    }

    public enum RoadType {
        PRIMARY_ARTERIAL,
        SECONDARY_ARTERIAL,
        UNDERPASS,
        LOCAL_STREET,
        HIGHWAY
    }

    public enum DrainageAssetType {
        STORM_WATER_DRAIN,
        PUMPING_STATION,
        RETENTION_BASIN,
        SLUICE_GATE,
        CULVERT
    }

    public enum AssetStatus {
        OPERATIONAL,
        OFFLINE,
        UNDER_MAINTENANCE,
        UNVERIFIED
    }

    public enum ObservationType {
        RAINFALL_MEASUREMENT,
        WATER_LEVEL_GAUGE,
        CITIZEN_PHOTO,
        SATELLITE_PRECIPITATION,
        RADAR_REFLECTIVITY
    }

    public enum SourceClassification {
        VERIFIED_OBSERVATION,
        PROVIDER_FORECAST,
        DERIVED_ESTIMATE,
        USER_REPORTED,
        SIMULATED,
        UNAVAILABLE
    }

    public enum TwinSnapshotType {
        CURRENT_OPERATIONAL,
        HISTORICAL_REPLAY,
        SIMULATED_SCENARIO
    }

    public enum IncidentType {
        WATERLOGGING_CHOKE,
        UNDERPASS_INUNDATION,
        PUMP_FAILURE,
        TRAFFIC_GRIDLOCK,
        STRUCTURAL_HAZARD
    }

    public enum IncidentStatus {
        NEW,
        TRIAGED,
        ASSIGNED,
        IN_PROGRESS,
        RESOLVED,
        VERIFIED_CLOSED,
        REJECTED
    }

    public enum ResourceType {
        MOBILE_DEWATERING_PUMP,
        QUICK_RESPONSE_TEAM,
        TRAFFIC_POLICE_UNIT,
        EMERGENCY_RESCUE_BOAT
    }

    public enum ResourceStatus {
        AVAILABLE,
        DEPLOYED,
        OUT_OF_SERVICE
    }

    public enum VerificationStatus {
        OFFICIAL_PRIMARY,
        COMMUNITY_CROWDSOURCED,
        NUMERICAL_MODEL,
        UNVERIFIED
    }
}
