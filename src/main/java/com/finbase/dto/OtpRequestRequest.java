package com.finbase.dto;

import com.finbase.auth.OtpPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OtpRequestRequest(
        @NotNull @Pattern(regexp = "^[6-9][0-9]{9}$") String mobile,
        @NotNull OtpPurpose purpose) {
}
