package org.growforge.jalmarg.twin.dto;

import org.growforge.jalmarg.model.Enums.Severity;
import org.growforge.jalmarg.twin.model.*;
import org.growforge.jalmarg.twin.model.TwinEnums.*;

import java.time.Instant;
import java.util.List;

public final class TwinDtos {
    private TwinDtos() {}

    public record MunicipalZoneView(
            Long id,
            String zoneCode,
            String name,
            String municipalityName,
            String boundaryType,
            String boundaryGeoJson,
            double centroidLat,
            double centroidLng,
            double areaSqKm,
            Double populationEstimate,
            String drainageNetworkStatus
    ) {
        public static MunicipalZoneView of(MunicipalZone z) {
            return new MunicipalZoneView(
                    z.getId(), z.getZoneCode(), z.getName(), z.getMunicipalityName(),
                    z.getBoundaryType().name(), z.getBoundaryGeoJson(), z.getCentroidLat(),
                    z.getCentroidLng(), z.getAreaSqKm(), z.getPopulationEstimate(),
                    z.getDrainageNetworkStatus()
            );
        }
    }

    public record RoadSegmentView(
            Long id,
            String segmentCode,
            String roadName,
            String roadType,
            double startLat,
            double startLng,
            double endLat,
            double endLng,
            String geometryGeoJson,
            boolean isUnderpass,
            double lengthMeters,
            Double elevationMinMeters,
            Double elevationMaxMeters,
            Long zoneId
    ) {
        public static RoadSegmentView of(RoadSegment r) {
            return new RoadSegmentView(
                    r.getId(), r.getSegmentCode(), r.getRoadName(), r.getRoadType().name(),
                    r.getStartLat(), r.getStartLng(), r.getEndLat(), r.getEndLng(),
                    r.getGeometryGeoJson(), r.isUnderpass(), r.getLengthMeters(),
                    r.getElevationMinMeters(), r.getElevationMaxMeters(),
                    r.getZone() != null ? r.getZone().getId() : null
            );
        }
    }

    public record DrainageAssetView(
            Long id,
            String assetCode,
            String assetType,
            String name,
            String locationName,
            double latitude,
            double longitude,
            Long zoneId,
            Double designCapacityCusecs,
            String status,
            Instant lastInspectedAt,
            boolean isTelemetryConnected,
            String operationalNotes
    ) {
        public static DrainageAssetView of(DrainageAsset a) {
            return new DrainageAssetView(
                    a.getId(), a.getAssetCode(), a.getAssetType().name(), a.getName(),
                    a.getLocationName(), a.getLatitude(), a.getLongitude(),
                    a.getZone() != null ? a.getZone().getId() : null,
                    a.getDesignCapacityCusecs(), a.getStatus().name(),
                    a.getLastInspectedAt(), a.isTelemetryConnected(), a.getOperationalNotes()
            );
        }
    }

    public record DataSourceView(
            Long id,
            String sourceCode,
            String sourceName,
            String provider,
            String originalUrl,
            String geographicCoverage,
            String crs,
            String license,
            String permittedUsage,
            String sourceClassification,
            String verificationStatus,
            String updateFrequency,
            String notes
    ) {
        public static DataSourceView of(DataSourceRegistry d) {
            return new DataSourceView(
                    d.getId(), d.getSourceCode(), d.getSourceName(), d.getProvider(),
                    d.getOriginalUrl(), d.getGeographicCoverage(), d.getCrs(),
                    d.getLicense(), d.getPermittedUsage(), d.getSourceClassification().name(),
                    d.getVerificationStatus().name(), d.getUpdateFrequency(), d.getNotes()
            );
        }
    }

    public record TwinSnapshotView(
            Long id,
            String snapshotType,
            Long zoneId,
            String zoneName,
            Instant snapshotTimestamp,
            Instant publishedAt,
            int overallRiskScore,
            String riskLevel,
            String riskStatus,
            int activeReportCount,
            int activeIncidentCount,
            double rainfall6hMm,
            String modelVersion,
            String contributingEvidenceJson,
            String missingDataIndicatorsJson,
            String provenanceSummary,
            String uncertaintyDisclaimer,
            String immutableHash
    ) {
        public static TwinSnapshotView of(TwinSnapshot s) {
            return new TwinSnapshotView(
                    s.getId(), s.getSnapshotType().name(), s.getZone() != null ? s.getZone().getId() : null,
                    s.getZoneName(), s.getSnapshotTimestamp(), s.getPublishedAt(),
                    s.getOverallRiskScore(), s.getRiskLevel().name(), s.getRiskStatus().name(),
                    s.getActiveReportCount(), s.getActiveIncidentCount(), s.getRainfall6hMm(),
                    s.getModelVersion(), s.getContributingEvidenceJson(),
                    s.getMissingDataIndicatorsJson(), s.getProvenanceSummary(),
                    s.getUncertaintyDisclaimer(), s.getImmutableHash()
            );
        }
    }

    public record ScenarioRequest(
            String scenarioName,
            double rainfallTotalMm,
            double rainfallIntensityMmPerHour,
            int durationHours,
            Long targetZoneId,
            Double assumedDrainageCapacityPercent,
            List<String> closedRoadSegmentCodes,
            Integer availablePumpCount
    ) {}

    public record ScenarioRunView(
            Long id,
            String scenarioName,
            String runType,
            String createdBy,
            Instant createdAt,
            double rainfallTotalMm,
            double rainfallIntensityMmPerHour,
            int durationHours,
            Long targetZoneId,
            String targetZoneName,
            Double assumedDrainageCapacityPercent,
            String simulationLabel,
            int affectedRoadsCount,
            int baselineRiskScore,
            int simulatedRiskScore,
            int deltaRiskScore,
            String factorBreakdownJson,
            String dataGapsJson,
            String modelVersion,
            String status
    ) {
        public static ScenarioRunView of(ScenarioRun s) {
            return new ScenarioRunView(
                    s.getId(), s.getScenarioName(), s.getRunType(), s.getCreatedBy(),
                    s.getCreatedAt(), s.getRainfallTotalMm(), s.getRainfallIntensityMmPerHour(),
                    s.getDurationHours(), s.getTargetZoneId(), s.getTargetZoneName(),
                    s.getAssumedDrainageCapacityPercent(), s.getSimulationLabel(),
                    s.getAffectedRoadsCount(), s.getBaselineRiskScore(), s.getSimulatedRiskScore(),
                    s.getDeltaRiskScore(), s.getFactorBreakdownJson(), s.getDataGapsJson(),
                    s.getModelVersion(), s.getStatus()
            );
        }
    }

    public record MunicipalIncidentRequest(
            String title,
            String description,
            String incidentType,
            String severity,
            double latitude,
            double longitude,
            String locationName,
            Long zoneId,
            Long relatedReportId
    ) {}

    public record IncidentAssignRequest(
            String assignedDepartment,
            String assignedTeam,
            Long resourceId
    ) {}

    public record IncidentStatusUpdateRequest(
            String status,
            String resolutionNotes
    ) {}

    public record MunicipalIncidentView(
            Long id,
            String incidentNumber,
            String title,
            String description,
            String incidentType,
            String severity,
            String status,
            double latitude,
            double longitude,
            String locationName,
            Long zoneId,
            String assignedDepartment,
            String assignedTeam,
            int priorityScore,
            Long relatedReportId,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            String resolutionNotes
    ) {
        public static MunicipalIncidentView of(MunicipalIncident i) {
            return new MunicipalIncidentView(
                    i.getId(), i.getIncidentNumber(), i.getTitle(), i.getDescription(),
                    i.getIncidentType().name(), i.getSeverity().name(), i.getStatus().name(),
                    i.getLatitude(), i.getLongitude(), i.getLocationName(),
                    i.getZone() != null ? i.getZone().getId() : null,
                    i.getAssignedDepartment(), i.getAssignedTeam(), i.getPriorityScore(),
                    i.getRelatedReportId(), i.getCreatedAt(), i.getUpdatedAt(),
                    i.getResolvedAt(), i.getResolutionNotes()
            );
        }
    }

    public record ResponseResourceView(
            Long id,
            String resourceCode,
            String resourceType,
            String name,
            double currentLatitude,
            double currentLongitude,
            String status,
            Long assignedIncidentId,
            String contactInfo,
            Instant lastUpdated
    ) {
        public static ResponseResourceView of(ResponseResource r) {
            return new ResponseResourceView(
                    r.getId(), r.getResourceCode(), r.getResourceType().name(), r.getName(),
                    r.getCurrentLatitude(), r.getCurrentLongitude(), r.getStatus().name(),
                    r.getAssignedIncidentId(), r.getContactInfo(), r.getLastUpdated()
            );
        }
    }

    public record AuditEventView(
            Long id,
            Instant eventTimestamp,
            String actorId,
            String actorRole,
            String actionType,
            String targetEntity,
            String targetEntityId,
            String ipAddress,
            String detailsJson
    ) {
        public static AuditEventView of(AuditEvent a) {
            return new AuditEventView(
                    a.getId(), a.getEventTimestamp(), a.getActorId(), a.getActorRole(),
                    a.getActionType(), a.getTargetEntity(), a.getTargetEntityId(),
                    a.getIpAddress(), a.getDetailsJson()
            );
        }
    }

    public record TwinStatusView(
            String systemStatus,
            int totalZonesCount,
            int totalRoadSegmentsCount,
            int totalDrainageAssetsCount,
            int totalRegisteredSourcesCount,
            int activeOperationalIncidentsCount,
            int activeTwinSnapshotsCount,
            Instant lastSnapshotTime,
            String drainageTelemetryNotice
    ) {}
}
