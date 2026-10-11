package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.twin.model.TwinEnums.BoundaryType;

import java.time.Instant;

/**
 * Represents a recognized municipal administrative or watershed management zone in Delhi NCR.
 */
@Entity
@Table(name = "twin_municipal_zones", indexes = {
        @Index(name = "idx_zone_code", columnList = "zoneCode", unique = true),
        @Index(name = "idx_zone_municipality", columnList = "municipalityName")
})
public class MunicipalZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String zoneCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 128)
    private String municipalityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoundaryType boundaryType;

    /** GeoJSON polygon coordinates string, or null if UNAVAILABLE */
    @Column(name = "boundary_geojson", columnDefinition = "TEXT")
    private String boundaryGeoJson;

    private double centroidLat;
    private double centroidLng;

    private double areaSqKm;

    private Double populationEstimate;

    @Column(length = 64)
    private String drainageNetworkStatus; // UNAVAILABLE, PARTIAL_PLANNED, METERED

    private Instant createdAt;
    private Instant updatedAt;

    public MunicipalZone() {}

    public MunicipalZone(String zoneCode, String name, String municipalityName, BoundaryType boundaryType,
                         String boundaryGeoJson, double centroidLat, double centroidLng, double areaSqKm,
                         Double populationEstimate, String drainageNetworkStatus) {
        this.zoneCode = zoneCode;
        this.name = name;
        this.municipalityName = municipalityName;
        this.boundaryType = boundaryType;
        this.boundaryGeoJson = boundaryGeoJson;
        this.centroidLat = centroidLat;
        this.centroidLng = centroidLng;
        this.areaSqKm = areaSqKm;
        this.populationEstimate = populationEstimate;
        this.drainageNetworkStatus = drainageNetworkStatus;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMunicipalityName() { return municipalityName; }
    public void setMunicipalityName(String municipalityName) { this.municipalityName = municipalityName; }

    public BoundaryType getBoundaryType() { return boundaryType; }
    public void setBoundaryType(BoundaryType boundaryType) { this.boundaryType = boundaryType; }

    public String getBoundaryGeoJson() { return boundaryGeoJson; }
    public void setBoundaryGeoJson(String boundaryGeoJson) { this.boundaryGeoJson = boundaryGeoJson; }

    public double getCentroidLat() { return centroidLat; }
    public void setCentroidLat(double centroidLat) { this.centroidLat = centroidLat; }

    public double getCentroidLng() { return centroidLng; }
    public void setCentroidLng(double centroidLng) { this.centroidLng = centroidLng; }

    public double getAreaSqKm() { return areaSqKm; }
    public void setAreaSqKm(double areaSqKm) { this.areaSqKm = areaSqKm; }

    public Double getPopulationEstimate() { return populationEstimate; }
    public void setPopulationEstimate(Double populationEstimate) { this.populationEstimate = populationEstimate; }

    public String getDrainageNetworkStatus() { return drainageNetworkStatus; }
    public void setDrainageNetworkStatus(String drainageNetworkStatus) { this.drainageNetworkStatus = drainageNetworkStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
