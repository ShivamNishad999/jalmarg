package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.twin.model.TwinEnums.SourceClassification;
import org.growforge.jalmarg.twin.model.TwinEnums.VerificationStatus;

import java.time.Instant;

/**
 * Registry of external geospatial, meteorological, topographical, and crowdsourced data feeds.
 */
@Entity
@Table(name = "twin_data_source_registry", indexes = {
        @Index(name = "idx_source_code", columnList = "sourceCode", unique = true)
})
public class DataSourceRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sourceCode;

    @Column(nullable = false, length = 128)
    private String sourceName;

    @Column(nullable = false, length = 128)
    private String provider;

    @Column(length = 256)
    private String originalUrl;

    @Column(length = 128)
    private String geographicCoverage; // e.g., "Delhi NCR / Global"

    @Column(length = 64)
    private String crs; // e.g., "EPSG:4326"

    @Column(length = 64)
    private String license;

    @Column(length = 128)
    private String permittedUsage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceClassification sourceClassification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;

    @Column(length = 64)
    private String updateFrequency;

    @Column(length = 500)
    private String notes;

    private Instant registeredAt;

    public DataSourceRegistry() {}

    public DataSourceRegistry(String sourceCode, String sourceName, String provider, String originalUrl,
                              String geographicCoverage, String crs, String license, String permittedUsage,
                              SourceClassification sourceClassification, VerificationStatus verificationStatus,
                              String updateFrequency, String notes) {
        this.sourceCode = sourceCode;
        this.sourceName = sourceName;
        this.provider = provider;
        this.originalUrl = originalUrl;
        this.geographicCoverage = geographicCoverage;
        this.crs = crs;
        this.license = license;
        this.permittedUsage = permittedUsage;
        this.sourceClassification = sourceClassification;
        this.verificationStatus = verificationStatus;
        this.updateFrequency = updateFrequency;
        this.notes = notes;
        this.registeredAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    public String getGeographicCoverage() { return geographicCoverage; }
    public void setGeographicCoverage(String geographicCoverage) { this.geographicCoverage = geographicCoverage; }

    public String getCrs() { return crs; }
    public void setCrs(String crs) { this.crs = crs; }

    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }

    public String getPermittedUsage() { return permittedUsage; }
    public void setPermittedUsage(String permittedUsage) { this.permittedUsage = permittedUsage; }

    public SourceClassification getSourceClassification() { return sourceClassification; }
    public void setSourceClassification(SourceClassification sourceClassification) { this.sourceClassification = sourceClassification; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getUpdateFrequency() { return updateFrequency; }
    public void setUpdateFrequency(String updateFrequency) { this.updateFrequency = updateFrequency; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
}
