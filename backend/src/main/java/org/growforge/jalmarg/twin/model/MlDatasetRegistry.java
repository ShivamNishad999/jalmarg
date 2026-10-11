package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_dataset_registry", indexes = {
        @Index(name = "idx_ml_dataset_code", columnList = "datasetCode"),
        @Index(name = "idx_ml_dataset_status", columnList = "validationStatus")
})
public class MlDatasetRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String datasetCode;

    @Column(nullable = false, length = 128)
    private String datasetName;

    @Column(nullable = false, length = 128)
    private String sourceOrganization;

    @Column(length = 512)
    private String officialSourceUrl;

    @Column(nullable = false, length = 32)
    private String provenanceType;

    @Column(length = 256)
    private String spatialCoverage;

    @Column(length = 256)
    private String temporalCoverage;

    @Column(length = 64)
    private String spatialResolution;

    @Column(length = 64)
    private String temporalResolution;

    @Column(length = 64)
    private String crs;

    @Column(length = 64)
    private String units;

    @Column(length = 128)
    private String license;

    @Column(length = 512)
    private String localStoragePath;

    private Long fileSizeBytes;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(nullable = false, length = 32)
    private String validationStatus;

    @Column(columnDefinition = "TEXT")
    private String knownLimitations;

    private Instant lastAcquiredAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public MlDatasetRegistry() {}

    public Long getId() { return id; }
    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public String getSourceOrganization() { return sourceOrganization; }
    public void setSourceOrganization(String sourceOrganization) { this.sourceOrganization = sourceOrganization; }
    public String getOfficialSourceUrl() { return officialSourceUrl; }
    public void setOfficialSourceUrl(String officialSourceUrl) { this.officialSourceUrl = officialSourceUrl; }
    public String getProvenanceType() { return provenanceType; }
    public void setProvenanceType(String provenanceType) { this.provenanceType = provenanceType; }
    public String getSpatialCoverage() { return spatialCoverage; }
    public void setSpatialCoverage(String spatialCoverage) { this.spatialCoverage = spatialCoverage; }
    public String getTemporalCoverage() { return temporalCoverage; }
    public void setTemporalCoverage(String temporalCoverage) { this.temporalCoverage = temporalCoverage; }
    public String getSpatialResolution() { return spatialResolution; }
    public void setSpatialResolution(String spatialResolution) { this.spatialResolution = spatialResolution; }
    public String getTemporalResolution() { return temporalResolution; }
    public void setTemporalResolution(String temporalResolution) { this.temporalResolution = temporalResolution; }
    public String getCrs() { return crs; }
    public void setCrs(String crs) { this.crs = crs; }
    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public String getLocalStoragePath() { return localStoragePath; }
    public void setLocalStoragePath(String localStoragePath) { this.localStoragePath = localStoragePath; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public String getKnownLimitations() { return knownLimitations; }
    public void setKnownLimitations(String knownLimitations) { this.knownLimitations = knownLimitations; }
    public Instant getLastAcquiredAt() { return lastAcquiredAt; }
    public void setLastAcquiredAt(Instant lastAcquiredAt) { this.lastAcquiredAt = lastAcquiredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
