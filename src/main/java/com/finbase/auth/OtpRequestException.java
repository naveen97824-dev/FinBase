package com.finbase.auth;

import lombok.Getter;

/** Thrown by {@link OtpService#requestOtp} when the request is rejected before an OTP is sent. */
@Getter
public class OtpRequestException extends RuntimeException {

    private final Reason reason;

    public OtpRequestException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public enum Reason {
        MOBILE_NOT_REGISTERED,
        MOBILE_ALREADY_REGISTERED,
        ACCOUNT_LOCKED,
        RESEND_COOLDOWN_ACTIVE,
        MAX_RESENDS_EXCEEDED,
        HOURLY_REQUEST_LIMIT_EXCEEDED,
        DAILY_REQUEST_LIMIT_EXCEEDED,
        IP_RATE_LIMIT_EXCEEDED,
        SOFT_BLOCKED
    }
}
