package com.optifit.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.optifit.config.AppProperties;
import com.optifit.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UsageLimiter {

    private record Counter(long window, int count) {
    }

    private final Map<String, Counter> counters = new HashMap<>();
    private final AppProperties properties;

    public synchronized void reserve(String session, String ip) {
        long hour = Instant.now().getEpochSecond() / 3600, day = hour / 24;
        counters.entrySet().removeIf(e -> !e.getKey().equals("daily") && e.getValue().window() != hour);
        if (counters.size() > 10000) {
            throw limited();
        }
        check("s:" + session, hour, properties.sessionLimit());
        check("ip:" + ip, hour, properties.ipLimit());
        check("daily", day, properties.dailyLimit());
        add("s:" + session, hour);
        add("ip:" + ip, hour);
        add("daily", day);
    }

    private void check(String key, long window, int limit) {
        var c = counters.get(key);
        if (c != null && c.window() == window && c.count() >= limit) {
            throw limited();
        }
    }

    private void add(String key, long window) {
        var c = counters.get(key);
        counters.put(key, new Counter(window, c != null && c.window() == window ? c.count() + 1 : 1));
    }

    private ApiException limited() {
        return new ApiException(429, "RATE_LIMITED", "The analysis limit has been reached. Please try again later.");
    }
}
