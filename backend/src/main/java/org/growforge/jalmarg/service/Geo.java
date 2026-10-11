package org.growforge.jalmarg.service;

/** Small geometry helpers. Good enough at city scale. */
public final class Geo {
    private Geo() {}

    private static final double EARTH_RADIUS_M = 6_371_000;

    /** Great-circle distance in metres. */
    public static double distanceM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * Distance in metres from point P to segment AB, using a local flat projection
     * around P (accurate to a few metres over a few km).
     */
    public static double pointToSegmentM(double pLat, double pLng,
                                         double aLat, double aLng,
                                         double bLat, double bLng) {
        double mPerDegLat = 111_320;
        double mPerDegLng = 111_320 * Math.cos(Math.toRadians(pLat));
        double ax = (aLng - pLng) * mPerDegLng, ay = (aLat - pLat) * mPerDegLat;
        double bx = (bLng - pLng) * mPerDegLng, by = (bLat - pLat) * mPerDegLat;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.sqrt(cx * cx + cy * cy);
    }

    /** Degrees of latitude covering the given metres (for bounding boxes). */
    public static double metersToLatDeg(double m) {
        return m / 111_320.0;
    }

    public static double metersToLngDeg(double m, double atLat) {
        return m / (111_320.0 * Math.cos(Math.toRadians(atLat)));
    }
}
