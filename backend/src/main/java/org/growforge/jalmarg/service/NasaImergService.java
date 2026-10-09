package org.growforge.jalmarg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.growforge.jalmarg.model.Enums.DataQuality;
import org.growforge.jalmarg.model.ProvenanceEnums.ProviderName;
import org.growforge.jalmarg.model.ProvenanceEnums.SourceCategory;
import org.growforge.jalmarg.model.SourceProvenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter for NASA GPM IMERG V07B Early Run (Global Precipitation Measurement).
 * Product: GPM_3IMERGHHE.07 (Half-hourly 0.1° x 0.1° L3 calibrated satellite precipitation rate).
 *
 * Latency: ~4 hours from satellite overpass to GES DISC data distribution.
 * Authentication: Requires NASA Earthdata Login Bearer Token.
 *
 * When disabled or credentials absent, reports MISSING_TELEMETRY without fabricating observations.
 */
@Service
public class NasaImergService {

    private static final Logger log = LoggerFactory.getLogger(NasaImergService.class);
    public static final String PRODUCT_ID = "GPM_3IMERGHHE.07";

    public record SatellitePrecipitationObservation(
            boolean available,
            double precipitationRateMmPerHr,
            Instant observedAt,
            Instant fetchedAt,
            long latencyMinutes,
            DataQuality dataQuality,
            SourceProvenance provenance
    ) {
        public static SatellitePrecipitationObservation unavailable(double lat, double lng, String reason) {
            Instant now = Instant.now();
            SourceProvenance prov = new SourceProvenance(
                    ProviderName.NASA_GES_DISC,
                    PRODUCT_ID,
                    SourceCategory.SATELLITE_OBSERVATION,
                    null,
                    null,
                    now,
                    lat,
                    lng,
                    11000.0, // ~0.1 deg (~11 km grid resolution)
                    "mm/hr",
                    0.0,
                    reason,
                    DataQuality.MISSING_TELEMETRY,
                    false,
                    "TTL_60_MIN"
            );
            return new SatellitePrecipitationObservation(false, 0.0, null, now, 0, DataQuality.MISSING_TELEMETRY, prov);
        }
    }

    private final boolean enabled;
    private final String endpoint;
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Map<String, SatellitePrecipitationObservation> cache = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public NasaImergService(
            @Value("${jalmarg.satellite.nasa-imerg.enabled:false}") boolean enabled,
            @Value("${jalmarg.satellite.nasa-imerg.endpoint:https://gpm1.gesdisc.eosdis.nasa.gov/opendap/GPM_L3/GPM_3IMERGHHE.07}") String endpoint,
            @Value("${jalmarg.satellite.nasa-imerg.token:}") String token) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.token = token;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
        this.mapper = new ObjectMapper();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Fetches recent satellite precipitation rate for coordinates.
     */
    public SatellitePrecipitationObservation fetchRecentObservation(double lat, double lng) {
        if (!enabled || token == null || token.isBlank()) {
            return SatellitePrecipitationObservation.unavailable(lat, lng,
                    enabled ? "EARTHDATA_TOKEN_MISSING" : "NASA_IMERG_DISABLED");
        }

        String cacheKey = String.format(Locale.ROOT, "%.2f,%.2f", lat, lng);
        SatellitePrecipitationObservation cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).toMinutes() < 30) {
            return cached;
        }

        try {
            // Subsetting query for 0.1 degree pixel
            String url = String.format(Locale.ROOT,
                    "%s/subset?lat=%.4f&lon=%.4f&var=precipitationCal",
                    endpoint, lat, lng);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                double rateMmHr = root.path("precipitationCal").asDouble(0.0);
                String timeStr = root.path("time").asText(null);
                Instant observedAt = timeStr != null ? Instant.parse(timeStr) : Instant.now().minus(Duration.ofHours(4));
                Instant fetchedAt = Instant.now();
                long latencyMin = Duration.between(observedAt, fetchedAt).toMinutes();

                SourceProvenance prov = new SourceProvenance(
                        ProviderName.NASA_GES_DISC,
                        PRODUCT_ID,
                        SourceCategory.SATELLITE_OBSERVATION,
                        observedAt,
                        null,
                        fetchedAt,
                        lat,
                        lng,
                        11000.0, // 0.1 deg
                        "mm/hr",
                        rateMmHr,
                        String.format(Locale.ROOT, "%.2f mm/hr", rateMmHr),
                        DataQuality.FRESH,
                        false,
                        "TTL_30_MIN"
                );

                SatellitePrecipitationObservation obs = new SatellitePrecipitationObservation(
                        true, rateMmHr, observedAt, fetchedAt, latencyMin, DataQuality.FRESH, prov);
                cache.put(cacheKey, obs);
                return obs;
            } else {
                log.warn("NASA GES DISC returned HTTP {}: {}", resp.statusCode(), resp.body());
                return SatellitePrecipitationObservation.unavailable(lat, lng, "HTTP_" + resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("NASA IMERG query failed for ({}, {}): {}", lat, lng, e.getMessage());
            return SatellitePrecipitationObservation.unavailable(lat, lng, "NETWORK_ERROR");
        }
    }
}
