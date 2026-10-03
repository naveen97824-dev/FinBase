package com.finbase.auth;

import java.io.Serializable;
import java.util.UUID;

/** The single active OTP for a mobile number, as stored in Redis. */
public record OtpState(
        String otpHash,
        UUID sessionToken,
        OtpPurpose purpose,
        int attemptCount,
        int resendCount) implements Serializable {

    public OtpState withAttemptIncremented() {
        return new OtpState(otpHash, sessionToken, purpose, attemptCount + 1, resendCount);
    }
}
