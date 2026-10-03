package com.finbase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finbase.dto.RegisterRequest;
import com.finbase.entity.Financier;
import com.finbase.entity.enums.EntityType;
import com.finbase.entity.enums.FinancierStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Registration completes in PENDING_VERIFICATION and only after the
 * mobile's OTP has been verified — never before. See the registration
 * flow in docs/v2-01-functional-spec.md Part 1.2.
 */
class RegistrationServiceTest extends AuthIntegrationTestBase {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private OtpStore otpStore;

    private RegisterRequest requestFor(String mobile, UUID sessionToken) {
        return new RegisterRequest(
                sessionToken,
                "Sri Lakshmi Finance",
                EntityType.proprietorship,
                null, null, null, null, null,
                "123 Main St", null, "Area", "City", null, "District", "State", "600001",
                "Owner Name", mobile, null, null, null);
    }

    @Test
    void registrationWithoutAPriorVerifiedOtpIsRejected() {
        String mobile = randomMobile();
        RegisterRequest request = requestFor(mobile, UUID.randomUUID());

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("OTP must be verified");
    }

    @Test
    void registrationSucceedsAfterOtpVerifiedForThisExactSessionToken() {
        String mobile = randomMobile();
        UUID sessionToken = UUID.randomUUID();
        otpStore.markVerifiedForRegistration(mobile, sessionToken);

        Financier financier = registrationService.register(requestFor(mobile, sessionToken));

        assertThat(financier.getStatus()).isEqualTo(FinancierStatus.pending_verification);
        assertThat(financier.getFinancierCode()).startsWith("FIN-");
        assertThat(financier.getPrimaryMobile()).isEqualTo(mobile);
    }

    @Test
    void registrationWithADifferentSessionTokenThanWasVerifiedIsRejected() {
        String mobile = randomMobile();
        otpStore.markVerifiedForRegistration(mobile, UUID.randomUUID());

        RegisterRequest request = requestFor(mobile, UUID.randomUUID()); // different token

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(RegistrationException.class);
    }

    @Test
    void verifiedMarkerIsConsumedOnSuccessfulRegistrationPreventingReuse() {
        String mobile = randomMobile();
        UUID sessionToken = UUID.randomUUID();
        otpStore.markVerifiedForRegistration(mobile, sessionToken);

        registrationService.register(requestFor(mobile, sessionToken));

        // The marker is gone — a second registration attempt with the same
        // (now-already-used) verified session must fail, even though the
        // mobile-already-registered check would also catch it.
        assertThat(otpStore.isVerifiedForRegistration(mobile, sessionToken)).isFalse();
    }

    private String randomMobile() {
        return "9" + String.format("%09d", Math.abs(new java.util.Random().nextInt(100_000_000)));
    }
}
