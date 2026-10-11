package org.growforge.jalmarg.twin.model;

import jakarta.persistence.*;
import org.growforge.jalmarg.twin.model.TwinEnums.AssetStatus;
import org.growforge.jalmarg.twin.model.TwinEnums.DrainageAssetType;

import java.time.Instant;

/**
 * Represents a stormwater drainage infrastructure asset (pumping station, primary nala outfall, sluice gate).
 * NOTE: Assets without live IoT sensors are stored with status UNVERIFIED and isTelemetryConnected=false.
 */
@Entity
@Table(name = "twin_drainage_assets", indexes = {
        @Index(name = "idx_drainage_asset_code", columnList = "assetCode", unique = true),
        @Index(name = "idx_drainage_zone_id", columnList = "zone_id"),
        @Index(name = "idx_drainage_status", columnList = "status")
})
public class DrainageAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrainageAssetType assetType;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 256)
    private String locationName;

    private double latitude;
    private double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private MunicipalZone zone;

    /** Design discharge/pumping capacity in cusecs (null if unmeasured/unverified) */
    private Double designCapacityCusecs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status;

    private Instant lastInspectedAt;

    /** True only if hardware IoT sensor telemetry is live and authenticated */
    private boolean isTelemetryConnected;

    @Column(length = 500)
    private String operationalNotes;

    private Instant createdAt;
    private Instant updatedAt;

    public DrainageAsset() {}

    public DrainageAsset(String assetCode, DrainageAssetType assetType, String name, String locationName,
                         double latitude, double longitude, MunicipalZone zone, Double designCapacityCusecs,
                         AssetStatus status, boolean isTelemetryConnected, String operationalNotes) {
        this.assetCode = assetCode;
        this.assetType = assetType;
        this.name = name;
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zone = zone;
        this.designCapacityCusecs = designCapacityCusecs;
        this.status = status;
        this.isTelemetryConnected = isTelemetryConnected;
        this.operationalNotes = operationalNotes;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }

    public DrainageAssetType getAssetType() { return assetType; }
    public void setAssetType(DrainageAssetType assetType) { this.assetType = assetType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public MunicipalZone getZone() { return zone; }
    public void setZone(MunicipalZone zone) { this.zone = zone; }

    public Double getDesignCapacityCusecs() { return designCapacityCusecs; }
    public void setDesignCapacityCusecs(Double designCapacityCusecs) { this.designCapacityCusecs = designCapacityCusecs; }

    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }

    public Instant getLastInspectedAt() { return lastInspectedAt; }
    public void setLastInspectedAt(Instant lastInspectedAt) { this.lastInspectedAt = lastInspectedAt; }

    public boolean isTelemetryConnected() { return isTelemetryConnected; }
    public void setTelemetryConnected(boolean telemetryConnected) { isTelemetryConnected = telemetryConnected; }

    public String getOperationalNotes() { return operationalNotes; }
    public void setOperationalNotes(String operationalNotes) { this.operationalNotes = operationalNotes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
