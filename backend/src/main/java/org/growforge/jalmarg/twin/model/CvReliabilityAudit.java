package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_cv_reliability_audit", indexes = {
        @Index(name = "idx_ml_cv_rel_model_time", columnList = "model_version, audit_timestamp")
})
public class CvReliabilityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "audit_timestamp", nullable = false)
    private Instant auditTimestamp = Instant.now();

    @Column(name = "total_images_audited", nullable = false)
    private Integer totalImagesAudited;

    @Column(name = "unique_images_count", nullable = false)
    private Integer uniqueImagesCount;

    @Column(name = "exact_duplicates_count", nullable = false)
    private Integer exactDuplicatesCount;

    @Column(name = "cross_class_duplicates_count", nullable = false)
    private Integer crossClassDuplicatesCount;

    @Column(name = "folder_inferred_negatives_count", nullable = false)
    private Integer folderInferredNegativesCount;

    @Column(name = "human_verified_negatives_count", nullable = false)
    private Integer humanVerifiedNegativesCount;

    @Column(name = "license_status", nullable = false, length = 64)
    private String licenseStatus;

    @Column(name = "deployment_blocker_reason", columnDefinition = "TEXT")
    private String deploymentBlockerReason;

    @Column(name = "abstention_lower_threshold", nullable = false)
    private Double abstentionLowerThreshold = 0.35;

    @Column(name = "abstention_upper_threshold", nullable = false)
    private Double abstentionUpperThreshold = 0.65;

    @Column(name = "abstention_policy_notes", nullable = false, columnDefinition = "TEXT")
    private String abstentionPolicyNotes;

    @Column(name = "failure_cases_json", nullable = false, columnDefinition = "TEXT")
    private String failureCasesJson;

    @Column(name = "reliability_verdict", nullable = false, length = 64)
    private String reliabilityVerdict;

    @Column(name = "audited_by", nullable = false, length = 64)
    private String auditedBy = "SYSTEM_CV_AUDITOR";

    public CvReliabilityAudit() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public Instant getAuditTimestamp() { return auditTimestamp; }
    public void setAuditTimestamp(Instant auditTimestamp) { this.auditTimestamp = auditTimestamp; }

    public Integer getTotalImagesAudited() { return totalImagesAudited; }
    public void setTotalImagesAudited(Integer totalImagesAudited) { this.totalImagesAudited = totalImagesAudited; }

    public Integer getUniqueImagesCount() { return uniqueImagesCount; }
    public void setUniqueImagesCount(Integer uniqueImagesCount) { this.uniqueImagesCount = uniqueImagesCount; }

    public Integer getExactDuplicatesCount() { return exactDuplicatesCount; }
    public void setExactDuplicatesCount(Integer exactDuplicatesCount) { this.exactDuplicatesCount = exactDuplicatesCount; }

    public Integer getCrossClassDuplicatesCount() { return crossClassDuplicatesCount; }
    public void setCrossClassDuplicatesCount(Integer crossClassDuplicatesCount) { this.crossClassDuplicatesCount = crossClassDuplicatesCount; }

    public Integer getFolderInferredNegativesCount() { return folderInferredNegativesCount; }
    public void setFolderInferredNegativesCount(Integer folderInferredNegativesCount) { this.folderInferredNegativesCount = folderInferredNegativesCount; }

    public Integer getHumanVerifiedNegativesCount() { return humanVerifiedNegativesCount; }
    public void setHumanVerifiedNegativesCount(Integer humanVerifiedNegativesCount) { this.humanVerifiedNegativesCount = humanVerifiedNegativesCount; }

    public String getLicenseStatus() { return licenseStatus; }
    public void setLicenseStatus(String licenseStatus) { this.licenseStatus = licenseStatus; }

    public String getDeploymentBlockerReason() { return deploymentBlockerReason; }
    public void setDeploymentBlockerReason(String deploymentBlockerReason) { this.deploymentBlockerReason = deploymentBlockerReason; }

    public Double getAbstentionLowerThreshold() { return abstentionLowerThreshold; }
    public void setAbstentionLowerThreshold(Double abstentionLowerThreshold) { this.abstentionLowerThreshold = abstentionLowerThreshold; }

    public Double getAbstentionUpperThreshold() { return abstentionUpperThreshold; }
    public void setAbstentionUpperThreshold(Double abstentionUpperThreshold) { this.abstentionUpperThreshold = abstentionUpperThreshold; }

    public String getAbstentionPolicyNotes() { return abstentionPolicyNotes; }
    public void setAbstentionPolicyNotes(String abstentionPolicyNotes) { this.abstentionPolicyNotes = abstentionPolicyNotes; }

    public String getFailureCasesJson() { return failureCasesJson; }
    public void setFailureCasesJson(String failureCasesJson) { this.failureCasesJson = failureCasesJson; }

    public String getReliabilityVerdict() { return reliabilityVerdict; }
    public void setReliabilityVerdict(String reliabilityVerdict) { this.reliabilityVerdict = reliabilityVerdict; }

    public String getAuditedBy() { return auditedBy; }
    public void setAuditedBy(String auditedBy) { this.auditedBy = auditedBy; }
}
