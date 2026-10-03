package com.finbase.auth;

import com.finbase.entity.OtpRequest;
import com.finbase.repository.FinancierRepository;
import com.finbase.repository.OtpRequestRepository;
import com.finbase.sms.SmsSender;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * The OTP flow, exactly per the functional spec Part 1.3. Live OTP state
 * (hash, session token, attempt/resend counts) lives in Redis with a
 * 300-second TTL; {@code otp_requests} in Postgres is audit-only.
 *
 * <p>Rules enforced here: 6-digit SecureRandom code, Argon2 hash (never
 * plaintext, never logged), 3 verify attempts per OTP, 30s resend
 * cooldown, 3 resends per session, 10 requests/hour and 20/day per
 * mobile (soft block 1h on breach), 20/hour per IP, 5 consecutive failed
 * verifies locks the mobile for 30 minutes, one active OTP per mobile,
 * OTP bound to the session token issued at request time.
 */
@Service
@Slf4j
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_VERIFY_ATTEMPTS = 3;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final int MAX_RESENDS_PER_SESSION = 3;
    private static final int MAX_REQUESTS_PER_MOBILE_PER_HOUR = 10;
    private static final int MAX_REQUESTS_PER_MOBILE_PER_DAY = 20;
    private static final Duration SOFT_BLOCK_DURATION = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_IP_PER_HOUR = 20;
    private static final int CONSECUTIVE_FAILURES_BEFORE_LOCKOUT = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);

    private final OtpStore otpStore;
    private final RateLimiter rateLimiter;
    private final FinancierRepository financierRepository;
    private final OtpRequestRepository otpRequestRepository;
    private final SmsSender smsSender;
    private final Argon2PasswordEncoder argon2;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
            OtpStore otpStore,
            RateLimiter rateLimiter,
            FinancierRepository financierRepository,
            OtpRequestRepository otpRequestRepository,
            SmsSender smsSender) {
        this.otpStore = otpStore;
        this.rateLimiter = rateLimiter;
        this.financierRepository = financierRepository;
        this.otpRequestRepository = otpRequestRepository;
        this.smsSender = smsSender;
        this.argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * @return the session token the caller must present back to {@link #verifyOtp}
     */
    public UUID requestOtp(String mobile, OtpPurpose purpose, String ipAddress) {
        otpStore.lockedFor(mobile).ifPresent(remaining -> {
            throw new OtpRequestException(OtpRequestException.Reason.ACCOUNT_LOCKED,
                    "Too many failed attempts. Try again in " + remaining.toMinutes() + " minutes.");
        });

        if (purpose == OtpPurpose.login && !financierRepository.existsByPrimaryMobile(mobile)) {
            throw new OtpRequestException(OtpRequestException.Reason.MOBILE_NOT_REGISTERED,
                    "This mobile number is not registered. Please register first.");
        }
        if (purpose == OtpPurpose.registration && financierRepository.existsByPrimaryMobile(mobile)) {
            throw new OtpRequestException(OtpRequestException.Reason.MOBILE_ALREADY_REGISTERED,
                    "This mobile number is already registered. Log in instead.");
        }

        otpStore.get(mobile).ifPresent(existing -> {
            // Only the resend-specific limits apply to a mobile with an
            // already-active OTP; a brand-new request (no prior state) skips
            // straight to the hour/day counters below.
            long remainingTtl = otpStore.remainingTtl(mobile).orElse(0L);
            long secondsSinceIssued = OTP_TTL.getSeconds() - remainingTtl;
            if (secondsSinceIssued < RESEND_COOLDOWN.getSeconds()) {
                throw new OtpRequestException(OtpRequestException.Reason.RESEND_COOLDOWN_ACTIVE,
                        "Please wait before requesting another OTP.");
            }
            if (existing.resendCount() >= MAX_RESENDS_PER_SESSION) {
                otpStore.lock(mobile, SOFT_BLOCK_DURATION);
                throw new OtpRequestException(OtpRequestException.Reason.MAX_RESENDS_EXCEEDED,
                        "Too many attempts. Try again in 30 minutes.");
            }
        });

        checkAndIncrementRequestLimits(mobile, ipAddress);

        String otp = generateSixDigitOtp();
        String otpHash = argon2.encode(otp);
        UUID sessionToken = UUID.randomUUID();
        int resendCount = otpStore.get(mobile).map(s -> s.resendCount() + 1).orElse(0);

        otpStore.put(mobile, new OtpState(otpHash, sessionToken, purpose, 0, resendCount), OTP_TTL);

        OtpRequest audit = new OtpRequest();
        audit.setMobile(mobile);
        audit.setPurpose(purpose.name());
        audit.setOtpHash(otpHash);
        audit.setSessionToken(sessionToken);
        audit.setExpiresAt(Instant.now().plus(OTP_TTL));
        audit.setIpAddress(ipAddress);
        audit.setDeliveryStatus("queued");
        otpRequestRepository.save(audit);

        smsSender.send(mobile, "Your FinBase OTP is " + otp + ". Valid for 5 minutes. Do not share this with anyone.");

        return sessionToken;
    }

    /** @return the purpose the verified OTP was requested for */
    public OtpPurpose verifyOtp(String mobile, String otp, UUID sessionToken) {
        otpStore.lockedFor(mobile).ifPresent(remaining -> {
            throw new OtpVerifyException(OtpVerifyException.Reason.ACCOUNT_LOCKED,
                    "Too many failed attempts. Try again in " + remaining.toMinutes() + " minutes.");
        });

        OtpState state = otpStore.get(mobile)
                .orElseThrow(() -> new OtpVerifyException(OtpVerifyException.Reason.EXPIRED_OR_NOT_FOUND,
                        "OTP expired, request a new one."));

        if (!state.sessionToken().equals(sessionToken)) {
            throw new OtpVerifyException(OtpVerifyException.Reason.SESSION_TOKEN_MISMATCH,
                    "This OTP was not issued for this session.");
        }

        if (argon2.matches(otp, state.otpHash())) {
            otpStore.invalidate(mobile);
            otpStore.resetConsecutiveFailures(mobile);
            if (state.purpose() == OtpPurpose.registration) {
                otpStore.markVerifiedForRegistration(mobile, sessionToken);
            }
            return state.purpose();
        }

        recordFailedVerify(mobile, state);
        throw new IllegalStateException("unreachable: recordFailedVerify always throws");
    }

    private void recordFailedVerify(String mobile, OtpState state) {
        OtpState updated = state.withAttemptIncremented();

        long consecutiveFailures = otpStore.incrementConsecutiveFailures(mobile, LOCKOUT_DURATION);
        if (consecutiveFailures >= CONSECUTIVE_FAILURES_BEFORE_LOCKOUT) {
            otpStore.invalidate(mobile);
            otpStore.lock(mobile, LOCKOUT_DURATION);
            throw new OtpVerifyException(OtpVerifyException.Reason.ACCOUNT_LOCKED,
                    "Too many failed attempts. Locked for 30 minutes.");
        }

        if (updated.attemptCount() >= MAX_VERIFY_ATTEMPTS) {
            otpStore.invalidate(mobile);
            throw new OtpVerifyException(OtpVerifyException.Reason.INCORRECT_CODE,
                    "Incorrect OTP. Please request a new one.", 0);
        }

        otpStore.replaceKeepingTtl(mobile, updated);
        int remaining = MAX_VERIFY_ATTEMPTS - updated.attemptCount();
        throw new OtpVerifyException(OtpVerifyException.Reason.INCORRECT_CODE,
                "Incorrect. " + remaining + " attempts left.", remaining);
    }

    private void checkAndIncrementRequestLimits(String mobile, String ipAddress) {
        String hourKey = "finbase:otp:reqcount:hour:" + mobile;
        String dayKey = "finbase:otp:reqcount:day:" + mobile;
        String ipKey = "finbase:otp:reqcount:ip:hour:" + ipAddress;

        long hourly = rateLimiter.incrementWithinWindow(hourKey, Duration.ofHours(1));
        long daily = rateLimiter.incrementWithinWindow(dayKey, Duration.ofDays(1));
        long perIp = ipAddress == null ? 0 : rateLimiter.incrementWithinWindow(ipKey, Duration.ofHours(1));

        if (hourly > MAX_REQUESTS_PER_MOBILE_PER_HOUR) {
            otpStore.lock(mobile, SOFT_BLOCK_DURATION);
            throw new OtpRequestException(OtpRequestException.Reason.HOURLY_REQUEST_LIMIT_EXCEEDED,
                    "Too many OTP requests. Try again in 1 hour.");
        }
        if (daily > MAX_REQUESTS_PER_MOBILE_PER_DAY) {
            otpStore.lock(mobile, SOFT_BLOCK_DURATION);
            throw new OtpRequestException(OtpRequestException.Reason.DAILY_REQUEST_LIMIT_EXCEEDED,
                    "Too many OTP requests today. Try again later.");
        }
        if (perIp > MAX_REQUESTS_PER_IP_PER_HOUR) {
            throw new OtpRequestException(OtpRequestException.Reason.IP_RATE_LIMIT_EXCEEDED,
                    "Too many requests from this network. Try again later.");
        }
    }

    private String generateSixDigitOtp() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format("%0" + OTP_LENGTH + "d", value);
    }
}
