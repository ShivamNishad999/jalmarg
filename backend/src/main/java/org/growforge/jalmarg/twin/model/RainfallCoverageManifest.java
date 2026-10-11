package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ml_rainfall_coverage_manifest", indexes = {
        @Index(name = "idx_ml_rain_cov_code", columnList = "period_code"),
        @Index(name = "idx_ml_rain_cov_dates", columnList = "start_date, end_date"),
        @Index(name = "idx_ml_rain_cov_status", columnList = "acquisition_status")
})
public class RainfallCoverageManifest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_code", nullable = false, unique = true, length = 64)
    private String periodCode;

    @Column(name = "period_name", nullable = false, length = 128)
    private String periodName;

    @Column(name = "source_dataset", nullable = false, length = 64)
    private String sourceDataset = "OPEN_METEO_ERA5_LAND";

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "corridors_sampled", nullable = false)
    private Integer corridorsSampled = 11;

    @Column(name = "records_count", nullable = false)
    private Integer recordsCount = 0;

    @Column(name = "missing_timestamps_count", nullable = false)
    private Integer missingTimestampsCount = 0;

    @Column(name = "spatial_resolution", nullable = false, length = 64)
    private String spatialResolution = "0.1° (~9 km ERA5-Land grid)";

    @Column(name = "temporal_resolution", nullable = false, length = 32)
    private String temporalResolution = "HOURLY";

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    @Column(name = "acquisition_status", nullable = false, length = 32)
    private String acquisitionStatus = "ACQUIRED_AND_VALIDATED";

    @Column(name = "quality_status", nullable = false, length = 32)
    private String qualityStatus = "PASSED_QUALITY_CHECKS";

    @Column(name = "quality_notes", columnDefinition = "TEXT")
    private String qualityNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public RainfallCoverageManifest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPeriodCode() { return periodCode; }
    public void setPeriodCode(String periodCode) { this.periodCode = periodCode; }

    public String getPeriodName() { return periodName; }
    public void setPeriodName(String periodName) { this.periodName = periodName; }

    public String getSourceDataset() { return sourceDataset; }
    public void setSourceDataset(String sourceDataset) { this.sourceDataset = sourceDataset; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getCorridorsSampled() { return corridorsSampled; }
    public void setCorridorsSampled(Integer corridorsSampled) { this.corridorsSampled = corridorsSampled; }

    public Integer getRecordsCount() { return recordsCount; }
    public void setRecordsCount(Integer recordsCount) { this.recordsCount = recordsCount; }

    public Integer getMissingTimestampsCount() { return missingTimestampsCount; }
    public void setMissingTimestampsCount(Integer missingTimestampsCount) { this.missingTimestampsCount = missingTimestampsCount; }

    public String getSpatialResolution() { return spatialResolution; }
    public void setSpatialResolution(String spatialResolution) { this.spatialResolution = spatialResolution; }

    public String getTemporalResolution() { return temporalResolution; }
    public void setTemporalResolution(String temporalResolution) { this.temporalResolution = temporalResolution; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getSha256Checksum() { return sha256Checksum; }
    public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }

    public String getAcquisitionStatus() { return acquisitionStatus; }
    public void setAcquisitionStatus(String acquisitionStatus) { this.acquisitionStatus = acquisitionStatus; }

    public String getQualityStatus() { return qualityStatus; }
    public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }

    public String getQualityNotes() { return qualityNotes; }
    public void setQualityNotes(String qualityNotes) { this.qualityNotes = qualityNotes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
