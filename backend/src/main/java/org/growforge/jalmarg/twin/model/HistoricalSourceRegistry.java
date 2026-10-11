package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_historical_source_registry", indexes = {
        @Index(name = "idx_ml_hist_source_code", columnList = "source_code"),
        @Index(name = "idx_ml_hist_source_status", columnList = "acquisition_status")
})
public class HistoricalSourceRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_code", nullable = false, unique = true, length = 64)
    private String sourceCode;

    @Column(name = "source_title", nullable = false, length = 256)
    private String sourceTitle;

    @Column(nullable = false, length = 128)
    private String publisher;

    @Column(name = "official_url", length = 512)
    private String officialUrl;

    @Column(name = "date_coverage", length = 128)
    private String dateCoverage;

    @Column(name = "geographic_coverage", length = 256)
    private String geographicCoverage;

    @Column(name = "licensing_status", nullable = false, length = 64)
    private String licensingStatus = "GOI_OPEN_DATA";

    @Column(name = "permitted_usage", nullable = false, length = 128)
    private String permittedUsage = "RESEARCH_AND_MUNICIPAL_OPERATIONS";

    @Column(name = "data_format", nullable = false, length = 64)
    private String dataFormat = "STRUCTURED_INCIDENT_RECORDS";

    @Column(name = "access_method", nullable = false, length = 64)
    private String accessMethod = "DIRECT_AUTHENTICATED";

    @Column(name = "verification_limitations", columnDefinition = "TEXT")
    private String verificationLimitations;

    @Column(name = "acquisition_status", nullable = false, length = 32)
    private String acquisitionStatus = "ACQUIRED";

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(name = "records_count")
    private Integer recordsCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public HistoricalSourceRegistry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String officialUrl) { this.officialUrl = officialUrl; }

    public String getDateCoverage() { return dateCoverage; }
    public void setDateCoverage(String dateCoverage) { this.dateCoverage = dateCoverage; }

    public String getGeographicCoverage() { return geographicCoverage; }
    public void setGeographicCoverage(String geographicCoverage) { this.geographicCoverage = geographicCoverage; }

    public String getLicensingStatus() { return licensingStatus; }
    public void setLicensingStatus(String licensingStatus) { this.licensingStatus = licensingStatus; }

    public String getPermittedUsage() { return permittedUsage; }
    public void setPermittedUsage(String permittedUsage) { this.permittedUsage = permittedUsage; }

    public String getDataFormat() { return dataFormat; }
    public void setDataFormat(String dataFormat) { this.dataFormat = dataFormat; }

    public String getAccessMethod() { return accessMethod; }
    public void setAccessMethod(String accessMethod) { this.accessMethod = accessMethod; }

    public String getVerificationLimitations() { return verificationLimitations; }
    public void setVerificationLimitations(String verificationLimitations) { this.verificationLimitations = verificationLimitations; }

    public String getAcquisitionStatus() { return acquisitionStatus; }
    public void setAcquisitionStatus(String acquisitionStatus) { this.acquisitionStatus = acquisitionStatus; }

    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }

    public Integer getRecordsCount() { return recordsCount; }
    public void setRecordsCount(Integer recordsCount) { this.recordsCount = recordsCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
