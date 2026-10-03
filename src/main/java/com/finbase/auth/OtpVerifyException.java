package com.finbase.auth;

import lombok.Getter;

/** Thrown by {@link OtpService#verifyOtp} for every non-success outcome. */
@Getter
public class OtpVerifyException extends RuntimeException {

    private final Reason reason;
    private final int attemptsRemaining;

    public OtpVerifyException(Reason reason, String message, int attemptsRemaining) {
        super(message);
        this.reason = reason;
        this.attemptsRemaining = attemptsRemaining;
    }

    public OtpVerifyException(Reason reason, String message) {
        this(reason, message, 0);
    }

    public enum Reason {
        EXPIRED_OR_NOT_FOUND,
        SESSION_TOKEN_MISMATCH,
        INCORRECT_CODE,
        ACCOUNT_LOCKED
    }
}
