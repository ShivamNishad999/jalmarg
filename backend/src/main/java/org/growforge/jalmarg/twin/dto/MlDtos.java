package org.growforge.jalmarg.twin.dto;

import org.growforge.jalmarg.twin.model.MlDataQualityReport;
import org.growforge.jalmarg.twin.model.MlDatasetRegistry;
import org.growforge.jalmarg.twin.model.MlTrainingReadiness;

import java.time.Instant;

public class MlDtos {

    public record MlDatasetView(
            Long id,
            String datasetCode,
            String datasetName,
            String sourceOrganization,
            String officialSourceUrl,
            String provenanceType,
            String spatialCoverage,
            String temporalCoverage,
            String spatialResolution,
            String temporalResolution,
            String crs,
            String units,
            String license,
            String localStoragePath,
            Long fileSizeBytes,
            String sha256Checksum,
            String validationStatus,
            String knownLimitations,
            Instant lastAcquiredAt,
            Instant updatedAt
    ) {
        public static MlDatasetView of(MlDatasetRegistry d) {
            return new MlDatasetView(
                    d.getId(), d.getDatasetCode(), d.getDatasetName(), d.getSourceOrganization(),
                    d.getOfficialSourceUrl(), d.getProvenanceType(), d.getSpatialCoverage(),
                    d.getTemporalCoverage(), d.getSpatialResolution(), d.getTemporalResolution(),
                    d.getCrs(), d.getUnits(), d.getLicense(), d.getLocalStoragePath(),
                    d.getFileSizeBytes(), d.getSha256Checksum(), d.getValidationStatus(),
                    d.getKnownLimitations(), d.getLastAcquiredAt(), d.getUpdatedAt()
            );
        }
    }

    public record MlQualityReportView(
            Long id,
            String datasetCode,
            Instant reportTimestamp,
            int totalRecordsChecked,
            int missingTimestampsCount,
            int coordinateAnomaliesCount,
            int implausibleValuesCount,
            int duplicatesCount,
            int nodataContaminationCount,
            int qualityScore,
            String qualityVerdict,
            String detailsJson
    ) {
        public static MlQualityReportView of(MlDataQualityReport q) {
            return new MlQualityReportView(
                    q.getId(), q.getDatasetCode(), q.getReportTimestamp(),
                    q.getTotalRecordsChecked(), q.getMissingTimestampsCount(),
                    q.getCoordinateAnomaliesCount(), q.getImplausibleValuesCount(),
                    q.getDuplicatesCount(), q.getNodataContaminationCount(),
                    q.getQualityScore(), q.getQualityVerdict(), q.getDetailsJson()
            );
        }
    }

    public record MlReadinessView(
            Long id,
            Instant assessmentTimestamp,
            String readinessGate,
            int usablePositiveEvents,
            int usableNegativeObservations,
            int monsoonSeasonsCovered,
            String classImbalanceRatio,
            String depthMeasurementAvailability,
            String targetLeadTimesSupported,
            int targetLeakageRisksFound,
            String summaryNotes,
            String missingRequirementsJson,
            String gapReportJson,
            String assessedBy
    ) {
        public static MlReadinessView of(MlTrainingReadiness r) {
            return new MlReadinessView(
                    r.getId(), r.getAssessmentTimestamp(), r.getReadinessGate(),
                    r.getUsablePositiveEvents(), r.getUsableNegativeObservations(),
                    r.getMonsoonSeasonsCovered(), r.getClassImbalanceRatio(),
                    r.getDepthMeasurementAvailability(), r.getTargetLeadTimesSupported(),
                    r.getTargetLeakageRisksFound(), r.getSummaryNotes(),
                    r.getMissingRequirementsJson(), r.getGapReportJson(),
                    r.getAssessedBy()
            );
        }
    }
}
