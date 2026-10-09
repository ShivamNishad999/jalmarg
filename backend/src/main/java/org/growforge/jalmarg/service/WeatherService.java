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

    public record RainForecast(double next6hMm, double maxHourlyMm, String level) {}

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
            return new RainForecast(sum, max, level(sum, max));
        } catch (Exception e) {
            log.warn("Open-Meteo forecast failed: {}", e.getMessage());
            return new RainForecast(0, 0, "UNKNOWN");
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
