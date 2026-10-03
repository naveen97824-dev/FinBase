package com.finbase.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record OtpVerifyRequest(
        @NotNull @Pattern(regexp = "^[6-9][0-9]{9}$") String mobile,
        @NotNull @Pattern(regexp = "^[0-9]{6}$") String otp,
        @NotNull UUID sessionToken) {
}
