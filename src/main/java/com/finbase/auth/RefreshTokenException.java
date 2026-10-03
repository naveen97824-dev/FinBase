package com.finbase.auth;

/**
 * Thrown when a presented refresh token cannot be honoured — expired,
 * revoked, unknown, or (most importantly) already used, which triggers
 * whole-family revocation before this is thrown.
 */
public class RefreshTokenException extends RuntimeException {

    public RefreshTokenException(String message) {
        super(message);
    }
}
