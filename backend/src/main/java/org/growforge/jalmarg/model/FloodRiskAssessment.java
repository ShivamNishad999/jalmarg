package org.growforge.jalmarg.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.model.Enums.DataQuality;
import org.growforge.jalmarg.model.Enums.RiskLevel;
import org.growforge.jalmarg.model.Enums.RiskStatus;

import java.time.Instant;

/**
 * Persisted snapshot of autonomous flood risk evaluation for a hotspot or geo zone.
 * Distinguishes OBSERVED_FLOODING (confirmed by citizen/authority reports) from
 * PREDICTED_RISK (inferred from precipitation forecast + historical proneness).
 */
@Entity
@Table(name = "flood_risk_assessments", indexes = {
        @Index(name = "idx_risk_hotspot", columnList = "hotspot_id"),
        @Index(name = "idx_risk_level", columnList = "risk_level"),
        @Index(name = "idx_risk_assessed_at", columnList = "assessed_at")
})
public class FloodRiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotspot_id")
    private Long hotspotId;

    @Column(name = "hotspot_name", length = 200)
    private String hotspotName;

    @Column(length = 100)
    private String area;

    private double latitude;
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RiskStatus status;

    @Column(name = "rainfall_next_6h_mm")
    private double rainfallNext6hMm;

    @Column(name = "max_hourly_mm")
    private double maxHourlyMm;

    @Column(name = "rain_level", length = 30)
    private String rainLevel;

    @Column(name = "active_report_count")
    private int activeReportCount;

    @Column(name = "max_observed_depth_ft")
    private double maxObservedDepthFt;

    @Column(name = "historical_proneness")
    private int historicalProneness;

    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_quality", length = 30)
    private DataQuality dataQuality;

    @Column(name = "summary_en", length = 1000)
    private String summaryEn;

    @Column(name = "summary_hi", length = 1000)
    private String summaryHi;

    @Column(name = "factor_breakdown", length = 2000)
    private String factorBreakdown;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public FloodRiskAssessment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getHotspotId() { return hotspotId; }
    public void setHotspotId(Long hotspotId) { this.hotspotId = hotspotId; }

    public String getHotspotName() { return hotspotName; }
    public void setHotspotName(String hotspotName) { this.hotspotName = hotspotName; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public RiskStatus getStatus() { return status; }
    public void setStatus(RiskStatus status) { this.status = status; }

    public double getRainfallNext6hMm() { return rainfallNext6hMm; }
    public void setRainfallNext6hMm(double rainfallNext6hMm) { this.rainfallNext6hMm = rainfallNext6hMm; }

    public double getMaxHourlyMm() { return maxHourlyMm; }
    public void setMaxHourlyMm(double maxHourlyMm) { this.maxHourlyMm = maxHourlyMm; }

    public String getRainLevel() { return rainLevel; }
    public void setRainLevel(String rainLevel) { this.rainLevel = rainLevel; }

    public int getActiveReportCount() { return activeReportCount; }
    public void setActiveReportCount(int activeReportCount) { this.activeReportCount = activeReportCount; }

    public double getMaxObservedDepthFt() { return maxObservedDepthFt; }
    public void setMaxObservedDepthFt(double maxObservedDepthFt) { this.maxObservedDepthFt = maxObservedDepthFt; }

    public int getHistoricalProneness() { return historicalProneness; }
    public void setHistoricalProneness(int historicalProneness) { this.historicalProneness = historicalProneness; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public DataQuality getDataQuality() { return dataQuality; }
    public void setDataQuality(DataQuality dataQuality) { this.dataQuality = dataQuality; }

    public String getSummaryEn() { return summaryEn; }
    public void setSummaryEn(String summaryEn) { this.summaryEn = summaryEn; }

    public String getSummaryHi() { return summaryHi; }
    public void setSummaryHi(String summaryHi) { this.summaryHi = summaryHi; }

    public String getFactorBreakdown() { return factorBreakdown; }
    public void setFactorBreakdown(String factorBreakdown) { this.factorBreakdown = factorBreakdown; }

    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
