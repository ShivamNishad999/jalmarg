package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ml_data_quality_reports", indexes = {
        @Index(name = "idx_ml_quality_dataset_time", columnList = "datasetCode, reportTimestamp")
})
public class MlDataQualityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String datasetCode;

    @Column(nullable = false)
    private Instant reportTimestamp = Instant.now();

    @Column(nullable = false)
    private Integer totalRecordsChecked;

    @Column(nullable = false)
    private Integer missingTimestampsCount = 0;

    @Column(nullable = false)
    private Integer coordinateAnomaliesCount = 0;

    @Column(nullable = false)
    private Integer implausibleValuesCount = 0;

    @Column(nullable = false)
    private Integer duplicatesCount = 0;

    @Column(nullable = false)
    private Integer nodataContaminationCount = 0;

    @Column(nullable = false)
    private Integer qualityScore;

    @Column(nullable = false, length = 32)
    private String qualityVerdict;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    public MlDataQualityReport() {}

    public Long getId() { return id; }
    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }
    public Instant getReportTimestamp() { return reportTimestamp; }
    public void setReportTimestamp(Instant reportTimestamp) { this.reportTimestamp = reportTimestamp; }
    public Integer getTotalRecordsChecked() { return totalRecordsChecked; }
    public void setTotalRecordsChecked(Integer totalRecordsChecked) { this.totalRecordsChecked = totalRecordsChecked; }
    public Integer getMissingTimestampsCount() { return missingTimestampsCount; }
    public void setMissingTimestampsCount(Integer missingTimestampsCount) { this.missingTimestampsCount = missingTimestampsCount; }
    public Integer getCoordinateAnomaliesCount() { return coordinateAnomaliesCount; }
    public void setCoordinateAnomaliesCount(Integer coordinateAnomaliesCount) { this.coordinateAnomaliesCount = coordinateAnomaliesCount; }
    public Integer getImplausibleValuesCount() { return implausibleValuesCount; }
    public void setImplausibleValuesCount(Integer implausibleValuesCount) { this.implausibleValuesCount = implausibleValuesCount; }
    public Integer getDuplicatesCount() { return duplicatesCount; }
    public void setDuplicatesCount(Integer duplicatesCount) { this.duplicatesCount = duplicatesCount; }
    public Integer getNodataContaminationCount() { return nodataContaminationCount; }
    public void setNodataContaminationCount(Integer nodataContaminationCount) { this.nodataContaminationCount = nodataContaminationCount; }
    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public String getQualityVerdict() { return qualityVerdict; }
    public void setQualityVerdict(String qualityVerdict) { this.qualityVerdict = qualityVerdict; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
}
