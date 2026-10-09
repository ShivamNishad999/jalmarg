package org.growforge.jalmarg.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A place that keeps flooding. Every report is attached to the nearest hotspot
 * (within ~250 m) or creates a new one. This is what authorities get reports about.
 */
@Entity
@Table(name = "hotspots")
public class Hotspot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** City / authority area, e.g. Delhi, Noida, Greater Noida, Gurugram, Ghaziabad. */
    private String area;

    private double latitude;
    private double longitude;

    private int totalReports;
    private Instant firstSeen;
    private Instant lastSeen;

    public Hotspot() {}

    public Hotspot(String name, String area, double latitude, double longitude) {
        this.name = name;
        this.area = area;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public int getTotalReports() { return totalReports; }
    public void setTotalReports(int totalReports) { this.totalReports = totalReports; }
    public Instant getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
}
