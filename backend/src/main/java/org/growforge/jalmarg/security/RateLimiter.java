package org.growforge.jalmarg.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory fixed-window limiter for login and OTP endpoints.
 * Enough for one backend instance; behind several instances use a shared store.
 */
@Component
public class RateLimiter {

    private record Window(long start, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Returns true if the call is allowed: at most {@code max} calls per {@code windowSeconds} per key. */
    public boolean allow(String key, int max, long windowSeconds) {
        long now = System.currentTimeMillis();
        long size = windowSeconds * 1000;
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.start() >= size ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (windows.size() > 50_000) windows.clear(); // crude memory guard
        return w.count() <= max;
    }
}
