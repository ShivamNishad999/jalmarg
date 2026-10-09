package org.growforge.jalmarg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rain forecast for the next 6 hours from Open-Meteo (free, no API key).
 * Cached for 20 minutes per ~1 km grid cell.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    public static final String PRODUCT_ID = "ECMWF_IFS_OPENMETEO";

    public record RainForecast(
            double next6hMm,
            double maxHourlyMm,
            String level,
            java.time.Instant fetchedAt,
            java.time.Instant forecastValidAt,
            org.growforge.jalmarg.model.SourceProvenance provenance
    ) {
        // Backward-compatible constructor for existing callers and tests
        public RainForecast(double next6hMm, double maxHourlyMm, String level) {
            this(next6hMm, maxHourlyMm, level, java.time.Instant.now(), java.time.Instant.now().plus(java.time.Duration.ofHours(6)), null);
        }
    }

    private record Cached(RainForecast value, long at) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public RainForecast forecast(double lat, double lng) {
        String key = String.format(Locale.ROOT, "%.2f,%.2f", lat, lng);
        Cached c = cache.get(key);
        if (c != null && System.currentTimeMillis() - c.at() < 20 * 60 * 1000) return c.value();

        RainForecast f = fetch(lat, lng);
        cache.put(key, new Cached(f, System.currentTimeMillis()));
        return f;
    }

    private RainForecast fetch(double lat, double lng) {
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&hourly=precipitation&forecast_hours=6&timezone=Asia%%2FKolkata",
                lat, lng);
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant validUntil = now.plus(Duration.ofHours(6));

        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode arr = mapper.readTree(res.body()).path("hourly").path("precipitation");
            double sum = 0, max = 0;
            for (JsonNode v : arr) {
                double mm = v.asDouble(0);
                sum += mm;
                max = Math.max(max, mm);
            }
            sum = Math.round(sum * 10) / 10.0;
            String lvl = level(sum, max);

            org.growforge.jalmarg.model.SourceProvenance prov = new org.growforge.jalmarg.model.SourceProvenance(
                    org.growforge.jalmarg.model.ProvenanceEnums.ProviderName.OPEN_METEO,
                    PRODUCT_ID,
                    org.growforge.jalmarg.model.ProvenanceEnums.SourceCategory.WEATHER_FORECAST,
                    now,
                    validUntil,
                    now,
                    lat,
                    lng,
                    10000.0, // ~0.1 deg model resolution
                    "mm",
                    sum,
                    String.format(Locale.ROOT, "Next 6h: %.1fmm, Max Hourly: %.1fmm, Level: %s", sum, max, lvl),
                    org.growforge.jalmarg.model.Enums.DataQuality.FRESH,
                    false,
                    "TTL_20_MIN"
            );

            return new RainForecast(sum, max, lvl, now, validUntil, prov);
        } catch (Exception e) {
            log.warn("Open-Meteo forecast failed for ({}, {}): {}", lat, lng, e.getMessage());
            org.growforge.jalmarg.model.SourceProvenance prov = new org.growforge.jalmarg.model.SourceProvenance(
                    org.growforge.jalmarg.model.ProvenanceEnums.ProviderName.OPEN_METEO,
                    PRODUCT_ID,
                    org.growforge.jalmarg.model.ProvenanceEnums.SourceCategory.WEATHER_FORECAST,
                    null,
                    null,
                    now,
                    lat,
                    lng,
                    10000.0,
                    "mm",
                    0.0,
                    "UNAVAILABLE",
                    org.growforge.jalmarg.model.Enums.DataQuality.MISSING_TELEMETRY,
                    false,
                    "RETRY_IMMEDIATE"
            );
            return new RainForecast(0, 0, "UNKNOWN", now, null, prov);
        }
    }

    /** IMD-style buckets, adapted to a 6-hour window. */
    static String level(double sixHourMm, double maxHourly) {
        if (maxHourly >= 15 || sixHourMm >= 35) return "VERY_HEAVY";
        if (maxHourly >= 7.5 || sixHourMm >= 15) return "HEAVY";
        if (sixHourMm >= 2.5) return "MODERATE";
        if (sixHourMm > 0) return "LIGHT";
        return "NONE";
    }
}
