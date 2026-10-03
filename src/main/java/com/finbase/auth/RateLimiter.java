package com.finbase.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters backed by Redis — {@code INCR} then
 * {@code EXPIRE NX} on the first hit of a window, so every key in a
 * window shares one TTL regardless of how many increments land inside it.
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Increments the counter at {@code key} and returns the new count.
     * On the first increment of a fresh key, sets the key to expire after
     * {@code window} — later increments within the window don't reset it.
     */
    public long incrementWithinWindow(String key, Duration window) {
        Long count = redis.opsForValue().increment(key);
        long value = count == null ? 1L : count;
        if (value == 1L) {
            redis.expire(key, window);
        }
        return value;
    }

    /** True if the counter at {@code key} has already reached or passed {@code limit}. */
    public boolean exceeds(String key, long limit) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return false;
        }
        return Long.parseLong(raw) > limit;
    }
}
