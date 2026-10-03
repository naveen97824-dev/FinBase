package com.finbase.auth;

import com.finbase.entity.enums.FinancierStatus;

/** Thrown when a login OTP verifies correctly but the financier isn't active yet. */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(FinancierStatus status) {
        super(statusMessage(status));
    }

    private static String statusMessage(FinancierStatus status) {
        return switch (status) {
            case pending_verification -> "Your registration is awaiting verification. "
                    + "You'll be able to log in once it's approved.";
            case suspended -> "Your account has been suspended. Contact support.";
            case closed -> "Your account is closed.";
            case active, read_only -> "Account can log in."; // unreachable; kept for exhaustiveness
        };
    }
}
