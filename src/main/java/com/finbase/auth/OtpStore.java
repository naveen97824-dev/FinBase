package com.finbase.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed OTP state. One active OTP per mobile: writing a new state
 * for a mobile overwrites (and so invalidates) whatever was there before —
 * the "one active OTP per mobile" rule is enforced simply by this being a
 * single key per mobile, not a list.
 */
@Component
public class OtpStore {

    private static final String OTP_PREFIX = "finbase:otp:state:";
    private static final String LOCKOUT_PREFIX = "finbase:otp:locked:";
    private static final String FAIL_COUNT_PREFIX = "finbase:otp:failcount:";
    private static final String VERIFIED_PREFIX = "finbase:otp:verified:";
    private static final Duration VERIFIED_MARKER_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public OtpStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    public void put(String mobile, OtpState state, Duration ttl) {
        redis.opsForValue().set(OTP_PREFIX + mobile, objectMapper.writeValueAsString(state), ttl);
    }

    @SneakyThrows
    public Optional<OtpState> get(String mobile) {
        String raw = redis.opsForValue().get(OTP_PREFIX + mobile);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(raw, OtpState.class));
    }

    /** Seconds remaining before the active OTP for this mobile expires, or empty if there is none. */
    public Optional<Long> remainingTtl(String mobile) {
        Long ttl = redis.getExpire(OTP_PREFIX + mobile);
        if (ttl == null || ttl < 0) {
            return Optional.empty();
        }
        return Optional.of(ttl);
    }

    /** Preserves the key's remaining TTL — used when recording a failed attempt. */
    @SneakyThrows
    public void replaceKeepingTtl(String mobile, OtpState state) {
        Long ttlSeconds = redis.getExpire(OTP_PREFIX + mobile);
        Duration ttl = (ttlSeconds == null || ttlSeconds <= 0) ? Duration.ofSeconds(1) : Duration.ofSeconds(ttlSeconds);
        put(mobile, state, ttl);
    }

    public void invalidate(String mobile) {
        redis.delete(OTP_PREFIX + mobile);
    }

    public void lock(String mobile, Duration duration) {
        redis.opsForValue().set(LOCKOUT_PREFIX + mobile, "locked", duration);
    }

    public Optional<Duration> lockedFor(String mobile) {
        Long ttl = redis.getExpire(LOCKOUT_PREFIX + mobile);
        if (ttl == null || ttl <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(ttl));
    }

    public long incrementConsecutiveFailures(String mobile, Duration window) {
        Long count = redis.opsForValue().increment(FAIL_COUNT_PREFIX + mobile);
        long value = count == null ? 1L : count;
        if (value == 1L) {
            redis.expire(FAIL_COUNT_PREFIX + mobile, window);
        }
        return value;
    }

    public void resetConsecutiveFailures(String mobile) {
        redis.delete(FAIL_COUNT_PREFIX + mobile);
    }

    /**
     * Marks a mobile+sessionToken pair as OTP-verified for registration
     * purposes. Registration must complete within this window of a
     * successful verify — it does not reuse the (now-invalidated) OTP
     * state itself.
     */
    public void markVerifiedForRegistration(String mobile, UUID sessionToken) {
        redis.opsForValue().set(VERIFIED_PREFIX + mobile, sessionToken.toString(), VERIFIED_MARKER_TTL);
    }

    public boolean isVerifiedForRegistration(String mobile, UUID sessionToken) {
        String stored = redis.opsForValue().get(VERIFIED_PREFIX + mobile);
        return stored != null && stored.equals(sessionToken.toString());
    }

    public void clearVerifiedForRegistration(String mobile) {
        redis.delete(VERIFIED_PREFIX + mobile);
    }
}
