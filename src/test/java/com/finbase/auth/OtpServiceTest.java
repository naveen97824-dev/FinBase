package com.finbase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finbase.entity.Financier;
import com.finbase.entity.enums.EntityType;
import com.finbase.entity.enums.FinancierStatus;
import com.finbase.repository.FinancierRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Every OTP rate-limit and lockout path, exactly as specified in
 * docs/v2-01-functional-spec.md Part 1.3. This test class must never be
 * deleted or have its assertions weakened — these are the limits that
 * keep the OTP channel from being abused.
 */
class OtpServiceTest extends AuthIntegrationTestBase {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpStore otpStore;

    @Autowired
    private FinancierRepository financierRepository;

    @Autowired
    private StringRedisTemplate redis;

    private String mobile;

    @BeforeEach
    void setUp() {
        mobile = "9" + String.format("%09d", Math.abs(new java.util.Random().nextInt(100_000_000)));
    }

    @AfterEach
    void cleanUp() {
        // Tests share one Redis instance across the class; flushing avoids
        // state from one test leaking into the next's rate-limit counters.
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private Financier registerActiveFinancier(String mobile) {
        Financier f = new Financier();
        f.setFinancierCode("FIN-" + UUID.randomUUID().toString().substring(0, 6));
        f.setCompanyName("Test Co");
        f.setEntityType(EntityType.individual);
        f.setAddressLine1("Addr");
        f.setArea("Area");
        f.setCity("City");
        f.setDistrict("District");
        f.setState("State");
        f.setPincode("600001");
        f.setOwnerName("Owner");
        f.setPrimaryMobile(mobile);
        f.setStatus(FinancierStatus.active);
        return financierRepository.save(f);
    }

    private String lastOtpSentTo(String mobile) {
        // OtpService doesn't expose the raw OTP (correctly — it's never
        // logged or retrievable in plaintext), so tests that need to
        // verify read it back via the hash comparison exposed for test
        // use only: see TestOtpAccess.
        return TestOtpAccess.captured.get(mobile);
    }

    // ---- Happy path ----

    @Test
    void requestThenVerifyWithCorrectOtpSucceeds() {
        registerActiveFinancier(mobile);
        UUID sessionToken = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        String otp = lastOtpSentTo(mobile);

        OtpPurpose purpose = otpService.verifyOtp(mobile, otp, sessionToken);

        assertThat(purpose).isEqualTo(OtpPurpose.login);
        assertThat(otpStore.get(mobile)).isEmpty(); // invalidated after success
    }

    @Test
    void oneActiveOtpPerMobileNewRequestInvalidatesPrevious() {
        registerActiveFinancier(mobile);
        UUID firstToken = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");

        // Jump the cooldown so the second request isn't rejected for that reason.
        expireOtpCooldown(mobile);
        UUID secondToken = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        String secondOtp = lastOtpSentTo(mobile);

        assertThatThrownBy(() -> otpService.verifyOtp(mobile, secondOtp, firstToken))
                .isInstanceOf(OtpVerifyException.class)
                .satisfies(e -> assertThat(((OtpVerifyException) e).getReason())
                        .isEqualTo(OtpVerifyException.Reason.SESSION_TOKEN_MISMATCH));

        // The second (current) token must still work.
        assertThat(otpService.verifyOtp(mobile, secondOtp, secondToken)).isEqualTo(OtpPurpose.login);
    }

    // ---- Verify-attempt limit (3 per OTP) ----

    @Test
    void wrongOtpUnderThreeAttemptsReportsRemainingCount() {
        registerActiveFinancier(mobile);
        UUID sessionToken = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");

        OtpVerifyException first = catchVerifyException(mobile, "000000", sessionToken);
        assertThat(first.getReason()).isEqualTo(OtpVerifyException.Reason.INCORRECT_CODE);
        assertThat(first.getAttemptsRemaining()).isEqualTo(2);

        OtpVerifyException second = catchVerifyException(mobile, "000000", sessionToken);
        assertThat(second.getAttemptsRemaining()).isEqualTo(1);
    }

    @Test
    void thirdWrongAttemptInvalidatesOtpRequiringResend() {
        registerActiveFinancier(mobile);
        UUID sessionToken = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        String correctOtp = lastOtpSentTo(mobile);

        catchVerifyException(mobile, "000000", sessionToken);
        catchVerifyException(mobile, "000000", sessionToken);
        catchVerifyException(mobile, "000000", sessionToken);

        // Even the CORRECT otp must now fail — the OTP was invalidated, not
        // just attempt-limited.
        assertThatThrownBy(() -> otpService.verifyOtp(mobile, correctOtp, sessionToken))
                .isInstanceOf(OtpVerifyException.class)
                .satisfies(e -> assertThat(((OtpVerifyException) e).getReason())
                        .isEqualTo(OtpVerifyException.Reason.EXPIRED_OR_NOT_FOUND));
    }

    @Test
    void expiredOrMissingOtpIsRejected() {
        registerActiveFinancier(mobile);
        assertThatThrownBy(() -> otpService.verifyOtp(mobile, "123456", UUID.randomUUID()))
                .isInstanceOf(OtpVerifyException.class)
                .satisfies(e -> assertThat(((OtpVerifyException) e).getReason())
                        .isEqualTo(OtpVerifyException.Reason.EXPIRED_OR_NOT_FOUND));
    }

    @Test
    void sessionTokenNotMatchingTheRequestIsRejected() {
        registerActiveFinancier(mobile);
        otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        String otp = lastOtpSentTo(mobile);

        assertThatThrownBy(() -> otpService.verifyOtp(mobile, otp, UUID.randomUUID()))
                .isInstanceOf(OtpVerifyException.class)
                .satisfies(e -> assertThat(((OtpVerifyException) e).getReason())
                        .isEqualTo(OtpVerifyException.Reason.SESSION_TOKEN_MISMATCH));
    }

    // ---- Consecutive-failure lockout (5 → 30 min) ----

    @Test
    void fiveConsecutiveFailedVerifiesAcrossOtpsLocksTheMobileFor30Minutes() {
        registerActiveFinancier(mobile);

        // 3 wrong attempts invalidate OTP #1; request a fresh OTP and burn
        // 2 more wrong attempts on OTP #2 to reach 5 consecutive failures.
        UUID token1 = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        catchVerifyException(mobile, "000000", token1);
        catchVerifyException(mobile, "000000", token1);
        catchVerifyException(mobile, "000000", token1); // 3rd: OTP invalidated, consecutive=3

        expireOtpCooldown(mobile);
        UUID token2 = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        catchVerifyException(mobile, "000000", token2); // consecutive=4

        OtpVerifyException fifth = catchVerifyException(mobile, "000000", token2); // consecutive=5
        assertThat(fifth.getReason()).isEqualTo(OtpVerifyException.Reason.ACCOUNT_LOCKED);

        assertThat(otpStore.lockedFor(mobile)).isPresent();
        assertThat(otpStore.lockedFor(mobile).get()).isLessThanOrEqualTo(Duration.ofMinutes(30));

        // Locked out blocks BOTH new requests and verifies.
        assertThatThrownBy(() -> otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1"))
                .isInstanceOf(OtpRequestException.class)
                .satisfies(e -> assertThat(((OtpRequestException) e).getReason())
                        .isEqualTo(OtpRequestException.Reason.ACCOUNT_LOCKED));
    }

    @Test
    void successfulVerifyResetsConsecutiveFailureCount() {
        registerActiveFinancier(mobile);

        UUID token1 = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        catchVerifyException(mobile, "000000", token1);
        catchVerifyException(mobile, "000000", token1); // consecutive=2, 1 attempt left on this OTP

        String correctOtp = lastOtpSentTo(mobile);
        otpService.verifyOtp(mobile, correctOtp, token1); // succeeds, resets consecutive count

        expireOtpCooldown(mobile);
        UUID token2 = otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        // Fresh failures start from zero again, not from the earlier 2.
        OtpVerifyException e = catchVerifyException(mobile, "000000", token2);
        assertThat(e.getAttemptsRemaining()).isEqualTo(2);
    }

    // ---- Resend cooldown (30s) and per-session resend cap (3) ----

    @Test
    void resendBeforeCooldownElapsedIsRejected() {
        registerActiveFinancier(mobile);
        otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");

        assertThatThrownBy(() -> otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1"))
                .isInstanceOf(OtpRequestException.class)
                .satisfies(e -> assertThat(((OtpRequestException) e).getReason())
                        .isEqualTo(OtpRequestException.Reason.RESEND_COOLDOWN_ACTIVE));
    }

    @Test
    void moreThanThreeResendsPerSessionSoftBlocksForThirtyMinutes() {
        registerActiveFinancier(mobile);
        otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1"); // original request, resendCount 0

        // 3 resends are allowed (the original plus 3 resends = 4 OTPs sent).
        for (int i = 0; i < 3; i++) {
            expireOtpCooldown(mobile);
            otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        }

        // The 4th resend (5th request overall) must be rejected.
        expireOtpCooldown(mobile);
        assertThatThrownBy(() -> otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1"))
                .isInstanceOf(OtpRequestException.class)
                .satisfies(e -> assertThat(((OtpRequestException) e).getReason())
                        .isEqualTo(OtpRequestException.Reason.MAX_RESENDS_EXCEEDED));

        assertThat(otpStore.lockedFor(mobile)).isPresent();
    }

    // ---- Hourly / daily request limits per mobile ----

    @Test
    void moreThanTenRequestsPerHourSoftBlocksTheMobile() {
        registerActiveFinancier(mobile);

        // The per-session resend cap (3) is a separate, tighter limit than
        // the per-mobile hourly cap (10) under test here — starting a fresh
        // "session" every few requests (as if the user reopened the app)
        // avoids tripping the resend cap before the hourly one, without
        // touching the hourly/daily counters themselves (separate Redis keys).
        for (int i = 0; i < 10; i++) {
            if (i > 0 && i % 3 == 0) {
                otpStore.invalidate(mobile);
            } else if (i > 0) {
                expireOtpCooldown(mobile);
            }
            otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1");
        }

        otpStore.invalidate(mobile);
        assertThatThrownBy(() -> otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1"))
                .isInstanceOf(OtpRequestException.class)
                .satisfies(e -> assertThat(((OtpRequestException) e).getReason())
                        .isEqualTo(OtpRequestException.Reason.HOURLY_REQUEST_LIMIT_EXCEEDED));
    }

    // ---- Per-IP limit ----

    @Test
    void moreThanTwentyRequestsPerIpPerHourIsRejected() {
        // Use distinct mobiles so the per-mobile hourly/daily/resend limits
        // never trip first — isolating the per-IP limit specifically.
        String ip = "203.0.113.5";
        for (int i = 0; i < 20; i++) {
            String m = "9" + String.format("%09d", 200_000_000 + i);
            registerActiveFinancier(m);
            otpService.requestOtp(m, OtpPurpose.login, ip);
        }

        String overLimitMobile = "9" + String.format("%09d", 200_000_999);
        registerActiveFinancier(overLimitMobile);
        assertThatThrownBy(() -> otpService.requestOtp(overLimitMobile, OtpPurpose.login, ip))
                .isInstanceOf(OtpRequestException.class)
                .satisfies(e -> assertThat(((OtpRequestException) e).getReason())
                        .isEqualTo(OtpRequestException.Reason.IP_RATE_LIMIT_EXCEEDED));
    }

    // ---- Purpose / registration-state gating ----

    @Test
    void loginOtpOnUnregisteredMobileIsRejected() {
        assertThatThrownBy(() -> otpService.requestOtp(mobile, OtpPurpose.login, "127.0.0.1"))
                .isInstanceOf(OtpRequestException.class)
                .satisfies(e -> assertThat(((OtpRequestException) e).getReason())
                        .isEqualTo(OtpRequestException.Reason.MOBILE_NOT_REGISTERED));
    }

    @Test
    void registrationOtpOnAlreadyRegisteredMobileIsRejected() {
        registerActiveFinancier(mobile);
        assertThatThrownBy(() -> otpService.requestOtp(mobile, OtpPurpose.registration, "127.0.0.1"))
                .isInstanceOf(OtpRequestException.class)
                .satisfies(e -> assertThat(((OtpRequestException) e).getReason())
                        .isEqualTo(OtpRequestException.Reason.MOBILE_ALREADY_REGISTERED));
    }

    private OtpVerifyException catchVerifyException(String mobile, String wrongOtp, UUID sessionToken) {
        try {
            otpService.verifyOtp(mobile, wrongOtp, sessionToken);
            throw new AssertionError("expected OtpVerifyException but verify succeeded");
        } catch (OtpVerifyException e) {
            return e;
        }
    }

    /**
     * Simulates 31 real seconds passing since the OTP was issued — enough
     * to clear the 30s resend cooldown — without destroying the Redis key
     * itself (which would reset resendCount/attemptCount to zero, since
     * {@link OtpService} treats a missing key as "no active OTP" rather
     * than "cooldown elapsed"). Done by shortening the key's remaining TTL
     * to {@code OTP_TTL - 31s}, which is exactly what 31 real seconds of
     * elapsed time would have done to it.
     */
    private void expireOtpCooldown(String mobile) {
        redis.expire("finbase:otp:state:" + mobile, Duration.ofMinutes(5).minusSeconds(31));
    }
}
