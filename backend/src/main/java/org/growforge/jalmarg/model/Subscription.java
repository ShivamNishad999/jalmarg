package org.growforge.jalmarg.model;

import jakarta.persistence.*;
import java.time.Instant;

/** "Tell me when the area around this place floods." */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false)
    private String email;

    /** e.g. "Home", "College", "Office" */
    private String label;

    private double latitude;
    private double longitude;
    private double radiusKm = 3.0;

    /** Language for alert text: en or hi */
    private String lang = "en";

    private boolean snsSubscribed;
    private Instant createdAt;
    private Instant lastAlertAt;

    /** Fingerprint of the last alert sent, so we don't repeat the same one. */
    @Column(length = 500)
    private String lastAlertSignature;

    public Subscription() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(double radiusKm) { this.radiusKm = radiusKm; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public boolean isSnsSubscribed() { return snsSubscribed; }
    public void setSnsSubscribed(boolean snsSubscribed) { this.snsSubscribed = snsSubscribed; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastAlertAt() { return lastAlertAt; }
    public void setLastAlertAt(Instant lastAlertAt) { this.lastAlertAt = lastAlertAt; }
    public String getLastAlertSignature() { return lastAlertSignature; }
    public void setLastAlertSignature(String lastAlertSignature) { this.lastAlertSignature = lastAlertSignature; }
}
