package com.finbase.auth;

/** An access token (JWT, 15 min) paired with an opaque refresh token (30 days). */
public record TokenPair(String accessToken, String refreshToken) {
}
