package com.finbase.dto;

import com.finbase.auth.OtpPurpose;

/**
 * For {@code purpose: login}, tokens are issued immediately and the client
 * goes Home. For {@code purpose: registration}, no financier exists yet —
 * tokens are null and the client proceeds to {@code POST /auth/register}.
 */
public record OtpVerifyResponse(OtpPurpose purpose, String accessToken, String refreshToken) {
}
