package org.growforge.jalmarg.twin.dto;

import org.growforge.jalmarg.twin.model.CvReliabilityAudit;
import org.growforge.jalmarg.twin.model.GroundTruthObservation;
import org.growforge.jalmarg.twin.model.HistoricalSourceRegistry;

import java.time.Instant;
import java.util.List;

public class GroundTruthDtos {

    public record GroundTruthObservationView(
            Long id,
            String observationId,
            Double latitude,
            Double longitude,
            String locationDescription,
            Instant observationTimestamp,
            String observationTimezone,
            Instant reportedAt,
            Instant eventStartTime,
            Instant eventEndTime,
            Instant publicationTimestamp,
            String waterloggingStatus,
            String verificationStatus,
            String verificationAuthority,
            String verifiedByUser,
            Instant verifiedAt,
            String sourceOrganization,
            String sourceReference,
            String sourceUrl,
            String evidenceType,
            String evidenceFileReference,
            String licenseCode,
            String permittedUsage,
            Double spatialAccuracyM,
            Boolean isApproximateLocation,
            Integer temporalAccuracyMinutes,
            Double measuredWaterDepthM,
            String depthMeasurementMethod,
            String verificationNotes,
            String provenanceClassification,
            String alignmentStatus,
            Double rainfall1hMm,
            Double rainfall3hMm,
            Double rainfall6hMm,
            Double rainfall24hMm,
            Double antecedentRainfallIndex,
            Double elevationM,
            Double slopeDegrees,
            Double relativeDepressionM,
            Double nearestDrainDistanceM,
            String corridorName,
            Long relatedReportId,
            Long relatedIncidentId,
            String reviewQueueStatus,
            Instant ingestedAt
    ) {
        public static GroundTruthObservationView of(GroundTruthObservation o) {
            return new GroundTruthObservationView(
                    o.getId(), o.getObservationId(), o.getLatitude(), o.getLongitude(),
                    o.getLocationDescription(), o.getObservationTimestamp(), o.getObservationTimezone(),
                    o.getReportedAt(), o.getEventStartTime(), o.getEventEndTime(),
                    o.getPublicationTimestamp(), o.getWaterloggingStatus(), o.getVerificationStatus(),
                    o.getVerificationAuthority(), o.getVerifiedByUser(), o.getVerifiedAt(),
                    o.getSourceOrganization(), o.getSourceReference(), o.getSourceUrl(),
                    o.getEvidenceType(), o.getEvidenceFileReference(), o.getLicenseCode(),
                    o.getPermittedUsage(), o.getSpatialAccuracyM(), o.getIsApproximateLocation(),
                    o.getTemporalAccuracyMinutes(), o.getMeasuredWaterDepthM(), o.getDepthMeasurementMethod(),
                    o.getVerificationNotes(), o.getProvenanceClassification(), o.getAlignmentStatus(),
                    o.getRainfall1hMm(), o.getRainfall3hMm(), o.getRainfall6hMm(),
                    o.getRainfall24hMm(), o.getAntecedentRainfallIndex(), o.getElevationM(),
                    o.getSlopeDegrees(), o.getRelativeDepressionM(), o.getNearestDrainDistanceM(),
                    o.getCorridorName(), o.getRelatedReportId(), o.getRelatedIncidentId(),
                    o.getReviewQueueStatus(), o.getIngestedAt()
            );
        }
    }

    public record HistoricalSourceView(
            Long id,
            String sourceCode,
            String sourceTitle,
            String publisher,
            String officialUrl,
            String dateCoverage,
            String geographicCoverage,
            String licensingStatus,
            String permittedUsage,
            String dataFormat,
            String accessMethod,
            String verificationLimitations,
            String acquisitionStatus,
            String sha256Checksum,
            Integer recordsCount
    ) {
        public static HistoricalSourceView of(HistoricalSourceRegistry s) {
            return new HistoricalSourceView(
                    s.getId(), s.getSourceCode(), s.getSourceTitle(), s.getPublisher(),
                    s.getOfficialUrl(), s.getDateCoverage(), s.getGeographicCoverage(),
                    s.getLicensingStatus(), s.getPermittedUsage(), s.getDataFormat(),
                    s.getAccessMethod(), s.getVerificationLimitations(), s.getAcquisitionStatus(),
                    s.getSha256Checksum(), s.getRecordsCount()
            );
        }
    }

    public record NegativeObservationRequest(
            Double latitude,
            Double longitude,
            String locationDescription,
            String corridorName,
            Instant inspectionTimestamp,
            Boolean noVisibleStandingWater,
            String supportingNotes,
            String inspectionPhotoReference
    ) {}

    public record ObservationDecisionRequest(
            String decision, // VERIFY, DISPUTE, REJECT, QUEUE_FOR_REVIEW
            String reason,
            String verificationNotes,
            Double measuredDepthM
    ) {}

    public record GroundTruthSummaryView(
            long totalObservations,
            long verifiedPositives,
            long verifiedNegatives,
            long pendingReview,
            long disputed,
            long depthMeasurements,
            int monsoonSeasonsCovered,
            String rainfallAlignmentCompleteness
    ) {}

    public record CvReliabilityAuditView(
            Long id,
            String modelVersion,
            Instant auditTimestamp,
            Integer totalImagesAudited,
            Integer uniqueImagesCount,
            Integer exactDuplicatesCount,
            Integer crossClassDuplicatesCount,
            Integer folderInferredNegativesCount,
            Integer humanVerifiedNegativesCount,
            String licenseStatus,
            String deploymentBlockerReason,
            Double abstentionLowerThreshold,
            Double abstentionUpperThreshold,
            String abstentionPolicyNotes,
            String failureCasesJson,
            String reliabilityVerdict,
            String auditedBy
    ) {
        public static CvReliabilityAuditView of(CvReliabilityAudit c) {
            return new CvReliabilityAuditView(
                    c.getId(), c.getModelVersion(), c.getAuditTimestamp(),
                    c.getTotalImagesAudited() != null ? c.getTotalImagesAudited() : 0,
                    c.getUniqueImagesCount() != null ? c.getUniqueImagesCount() : 0,
                    c.getExactDuplicatesCount() != null ? c.getExactDuplicatesCount() : 0,
                    c.getCrossClassDuplicatesCount() != null ? c.getCrossClassDuplicatesCount() : 0,
                    c.getFolderInferredNegativesCount() != null ? c.getFolderInferredNegativesCount() : 0,
                    c.getHumanVerifiedNegativesCount() != null ? c.getHumanVerifiedNegativesCount() : 0,
                    c.getLicenseStatus(), c.getDeploymentBlockerReason(),
                    c.getAbstentionLowerThreshold() != null ? c.getAbstentionLowerThreshold() : 0.35,
                    c.getAbstentionUpperThreshold() != null ? c.getAbstentionUpperThreshold() : 0.65,
                    c.getAbstentionPolicyNotes(), c.getFailureCasesJson(),
                    c.getReliabilityVerdict(), c.getAuditedBy()
            );
        }
    }

    // Phase 25D Evidence Verification, Forensics & Flyway Integrity DTOs
    public record EvidenceAuditSummaryView(
            long totalObservationsAudited,
            long evidenceVerifiedPositives,
            long evidenceVerifiedNegatives,
            long partiallyVerifiedObservations,
            long unverifiedObservations,
            long contradictedObservations,
            long verifiedCalibratedDepthMeasurements,
            long unverifiedDepthClaims,
            long rainfallAlignedObservations,
            long rainfallMissingObservations,
            long eligibleForClassificationTraining,
            long eligibleForDepthRegression,
            long excludedFromTraining,
            String readinessVerdict,
            List<String> readinessReasons,
            String flywayIntegrityStatus,
            String flywayRemediationStatus,
            Integer flywayAppliedChecksum,
            Integer flywayResolvedChecksum,
            String cvLicenseStatus,
            int cvFolderDerivedNegativesCount,
            String activeOperationalEngine
    ) {}

    public record ObservationEvidenceAuditView(
            String observationId,
            String corridorName,
            String locationDescription,
            String eventTimestampIso,
            String claimedWaterloggingStatus,
            Double claimedDepthM,
            String evidenceStatus,
            String evidenceRationale,
            String depthStatus,
            String depthRationale,
            String rainfallAlignmentStatus,
            String rainfallRationale,
            String trainingEligibility,
            List<String> eligibilityExclusionReasons
    ) {}

    public record FlywayIntegrityStatusView(
            int flywayVersionChecked,
            String scriptName,
            Integer appliedDbChecksum,
            Integer resolvedLocalChecksum,
            String validationStatus,
            List<String> schemaObjectsVerified,
            boolean schemaTablesPresent,
            String remediationStatus,
            String remediationPlan
    ) {}

    // ==============================================================================
    // Phase 25E-B: Historical Rainfall Coverage & Municipal Inspection Workflow DTOs
    // ==============================================================================

    public record RainfallCoverageView(
            Long id,
            String periodCode,
            String periodName,
            String sourceDataset,
            String startDate,
            String endDate,
            Integer corridorsSampled,
            Integer recordsCount,
            Integer missingTimestampsCount,
            String spatialResolution,
            String temporalResolution,
            String sha256Checksum,
            String acquisitionStatus,
            String qualityStatus,
            String qualityNotes,
            Instant createdAt
    ) {
        public static RainfallCoverageView of(org.growforge.jalmarg.twin.model.RainfallCoverageManifest m) {
            return new RainfallCoverageView(
                    m.getId(), m.getPeriodCode(), m.getPeriodName(), m.getSourceDataset(),
                    m.getStartDate() != null ? m.getStartDate().toString() : null,
                    m.getEndDate() != null ? m.getEndDate().toString() : null,
                    m.getCorridorsSampled(), m.getRecordsCount(), m.getMissingTimestampsCount(),
                    m.getSpatialResolution(), m.getTemporalResolution(), m.getSha256Checksum(),
                    m.getAcquisitionStatus(), m.getQualityStatus(), m.getQualityNotes(), m.getCreatedAt()
            );
        }
    }

    public record DryRoadInspectionRequest(
            Double latitude,
            Double longitude,
            String locationDescription,
            String corridorName,
            Instant inspectionTimestamp,
            Double gpsAccuracyM,
            String roadCondition,
            Boolean waterPresent,
            Double standingWaterDepthCm,
            String photoEvidenceUrl,
            String evidenceFileReference,
            String inspectorNotes
    ) {}

    public record DryRoadInspectionDecisionRequest(
            String decision, // VERIFY, REJECT
            String reason,
            String reviewNotes
    ) {}

    public record DryRoadInspectionView(
            Long id,
            String inspectionCode,
            String inspectorUsername,
            String inspectorName,
            String department,
            Double latitude,
            Double longitude,
            String locationDescription,
            String corridorName,
            Instant inspectionTimestamp,
            Double gpsAccuracyM,
            String roadCondition,
            Boolean waterPresent,
            Double standingWaterDepthCm,
            String photoEvidenceUrl,
            String evidenceFileReference,
            String inspectorNotes,
            String reviewStatus,
            String reviewedBy,
            Instant reviewedAt,
            String reviewNotes,
            Instant createdAt
    ) {
        public static DryRoadInspectionView of(org.growforge.jalmarg.twin.model.MunicipalDryRoadInspection i) {
            return new DryRoadInspectionView(
                    i.getId(), i.getInspectionCode(), i.getInspectorUsername(), i.getInspectorName(),
                    i.getDepartment(), i.getLatitude(), i.getLongitude(), i.getLocationDescription(),
                    i.getCorridorName(), i.getInspectionTimestamp(), i.getGpsAccuracyM(),
                    i.getRoadCondition(), i.getWaterPresent(), i.getStandingWaterDepthCm(),
                    i.getPhotoEvidenceUrl(), i.getEvidenceFileReference(), i.getInspectorNotes(),
                    i.getReviewStatus(), i.getReviewedBy(), i.getReviewedAt(), i.getReviewNotes(),
                    i.getCreatedAt()
            );
        }
    }

    public record CalibratedDepthMeasurementRequest(
            String observationId,
            String corridorName,
            Double latitude,
            Double longitude,
            Instant measurementTimestamp,
            String measurementType,
            Double measuredDepthM,
            Double measurementUncertaintyM,
            String units,
            String instrumentType,
            String instrumentId,
            String calibrationCertificateRef,
            String sourceDocumentTitle,
            String sourceDocumentUrl
    ) {}

    public record CalibratedDepthDecisionRequest(
            String decision, // VERIFY, REJECT
            String reason,
            String auditNotes
    ) {}

    public record CalibratedDepthMeasurementView(
            Long id,
            String measurementCode,
            String observationId,
            String corridorName,
            Double latitude,
            Double longitude,
            Instant measurementTimestamp,
            String measurementType,
            Double measuredDepthM,
            Double measurementUncertaintyM,
            String units,
            String instrumentType,
            String instrumentId,
            String calibrationCertificateRef,
            String sourceDocumentTitle,
            String sourceDocumentUrl,
            String evidenceStatus,
            String reviewerUsername,
            Instant reviewedAt,
            String auditNotes,
            Instant createdAt
    ) {
        public static CalibratedDepthMeasurementView of(org.growforge.jalmarg.twin.model.CalibratedDepthMeasurement d) {
            return new CalibratedDepthMeasurementView(
                    d.getId(), d.getMeasurementCode(), d.getObservationId(), d.getCorridorName(),
                    d.getLatitude(), d.getLongitude(), d.getMeasurementTimestamp(),
                    d.getMeasurementType(), d.getMeasuredDepthM(), d.getMeasurementUncertaintyM(),
                    d.getUnits(), d.getInstrumentType(), d.getInstrumentId(),
                    d.getCalibrationCertificateRef(), d.getSourceDocumentTitle(),
                    d.getSourceDocumentUrl(), d.getEvidenceStatus(), d.getReviewerUsername(),
                    d.getReviewedAt(), d.getAuditNotes(), d.getCreatedAt()
            );
        }
    }
}

