package com.finbase.auth;

/** Matches {@code otp_requests.purpose} in the schema: login | registration | mobile_change. */
public enum OtpPurpose {
    login,
    registration,
    mobile_change
}
