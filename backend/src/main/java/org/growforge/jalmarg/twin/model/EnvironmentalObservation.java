package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.model.Enums.DataQuality;
import org.growforge.jalmarg.twin.model.TwinEnums.ObservationType;
import org.growforge.jalmarg.twin.model.TwinEnums.SourceClassification;

import java.time.Instant;

/**
 * Stores raw or processed environmental observations (meteorological, crowdsourced, or satellite).
 */
@Entity
@Table(name = "twin_environmental_observations", indexes = {
        @Index(name = "idx_obs_type", columnList = "observationType"),
        @Index(name = "idx_obs_timestamp", columnList = "observedAt"),
        @Index(name = "idx_obs_source_id", columnList = "data_source_id")
})
public class EnvironmentalObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ObservationType observationType;

    private double latitude;
    private double longitude;

    private double measuredValue;

    @Column(nullable = false, length = 32)
    private String unit;

    @Column(nullable = false)
    private Instant observedAt;

    @Column(nullable = false)
    private Instant acquiredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id")
    private DataSourceRegistry dataSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceClassification sourceClassification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataQuality dataQuality;

    @Column(columnDefinition = "TEXT")
    private String rawPayloadJson;

    public EnvironmentalObservation() {}

    public EnvironmentalObservation(ObservationType observationType, double latitude, double longitude,
                                    double measuredValue, String unit, Instant observedAt, Instant acquiredAt,
                                    DataSourceRegistry dataSource, SourceClassification sourceClassification,
                                    DataQuality dataQuality, String rawPayloadJson) {
        this.observationType = observationType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.measuredValue = measuredValue;
        this.unit = unit;
        this.observedAt = observedAt;
        this.acquiredAt = acquiredAt;
        this.dataSource = dataSource;
        this.sourceClassification = sourceClassification;
        this.dataQuality = dataQuality;
        this.rawPayloadJson = rawPayloadJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ObservationType getObservationType() { return observationType; }
    public void setObservationType(ObservationType observationType) { this.observationType = observationType; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(double measuredValue) { this.measuredValue = measuredValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }

    public Instant getAcquiredAt() { return acquiredAt; }
    public void setAcquiredAt(Instant acquiredAt) { this.acquiredAt = acquiredAt; }

    public DataSourceRegistry getDataSource() { return dataSource; }
    public void setDataSource(DataSourceRegistry dataSource) { this.dataSource = dataSource; }

    public SourceClassification getSourceClassification() { return sourceClassification; }
    public void setSourceClassification(SourceClassification sourceClassification) { this.sourceClassification = sourceClassification; }

    public DataQuality getDataQuality() { return dataQuality; }
    public void setDataQuality(DataQuality dataQuality) { this.dataQuality = dataQuality; }

    public String getRawPayloadJson() { return rawPayloadJson; }
    public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
}
