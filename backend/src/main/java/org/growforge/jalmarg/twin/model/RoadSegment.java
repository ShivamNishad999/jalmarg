package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.twin.model.TwinEnums.RoadType;

import java.time.Instant;

/**
 * Represents a key transportation corridor or underpass segment in the Delhi NCR twin.
 */
@Entity
@Table(name = "twin_road_segments", indexes = {
        @Index(name = "idx_road_segment_code", columnList = "segmentCode", unique = true),
        @Index(name = "idx_road_underpass", columnList = "isUnderpass"),
        @Index(name = "idx_road_zone_id", columnList = "zone_id")
})
public class RoadSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String segmentCode;

    @Column(nullable = false, length = 128)
    private String roadName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoadType roadType;

    private double startLat;
    private double startLng;
    private double endLat;
    private double endLng;

    /** GeoJSON LineString coordinates */
    @Column(name = "geometry_geojson", columnDefinition = "TEXT")
    private String geometryGeoJson;

    private boolean isUnderpass;
    private double lengthMeters;
    private Double elevationMinMeters;
    private Double elevationMaxMeters;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private MunicipalZone zone;

    private Instant createdAt;
    private Instant updatedAt;

    public RoadSegment() {}

    public RoadSegment(String segmentCode, String roadName, RoadType roadType,
                       double startLat, double startLng, double endLat, double endLng,
                       String geometryGeoJson, boolean isUnderpass, double lengthMeters,
                       Double elevationMinMeters, Double elevationMaxMeters, MunicipalZone zone) {
        this.segmentCode = segmentCode;
        this.roadName = roadName;
        this.roadType = roadType;
        this.startLat = startLat;
        this.startLng = startLng;
        this.endLat = endLat;
        this.endLng = endLng;
        this.geometryGeoJson = geometryGeoJson;
        this.isUnderpass = isUnderpass;
        this.lengthMeters = lengthMeters;
        this.elevationMinMeters = elevationMinMeters;
        this.elevationMaxMeters = elevationMaxMeters;
        this.zone = zone;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSegmentCode() { return segmentCode; }
    public void setSegmentCode(String segmentCode) { this.segmentCode = segmentCode; }

    public String getRoadName() { return roadName; }
    public void setRoadName(String roadName) { this.roadName = roadName; }

    public RoadType getRoadType() { return roadType; }
    public void setRoadType(RoadType roadType) { this.roadType = roadType; }

    public double getStartLat() { return startLat; }
    public void setStartLat(double startLat) { this.startLat = startLat; }

    public double getStartLng() { return startLng; }
    public void setStartLng(double startLng) { this.startLng = startLng; }

    public double getEndLat() { return endLat; }
    public void setEndLat(double endLat) { this.endLat = endLat; }

    public double getEndLng() { return endLng; }
    public void setEndLng(double endLng) { this.endLng = endLng; }

    public String getGeometryGeoJson() { return geometryGeoJson; }
    public void setGeometryGeoJson(String geometryGeoJson) { this.geometryGeoJson = geometryGeoJson; }

    public boolean isUnderpass() { return isUnderpass; }
    public void setUnderpass(boolean underpass) { isUnderpass = underpass; }

    public double getLengthMeters() { return lengthMeters; }
    public void setLengthMeters(double lengthMeters) { this.lengthMeters = lengthMeters; }

    public Double getElevationMinMeters() { return elevationMinMeters; }
    public void setElevationMinMeters(Double elevationMinMeters) { this.elevationMinMeters = elevationMinMeters; }

    public Double getElevationMaxMeters() { return elevationMaxMeters; }
    public void setElevationMaxMeters(Double elevationMaxMeters) { this.elevationMaxMeters = elevationMaxMeters; }

    public MunicipalZone getZone() { return zone; }
    public void setZone(MunicipalZone zone) { this.zone = zone; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
