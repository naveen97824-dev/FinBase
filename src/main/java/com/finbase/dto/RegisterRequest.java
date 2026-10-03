package com.finbase.dto;

import com.finbase.entity.enums.EntityType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registration submits in one call once the mobile OTP has been verified;
 * {@code sessionToken} is the token from that successful
 * {@code /auth/otp/verify} call, re-presented here to prove it. Business
 * PAN and owner PAN are both optional per the v2 functional spec.
 */
public record RegisterRequest(
        @NotNull UUID sessionToken,

        // Business details
        @NotBlank @Size(min = 3, max = 150) String companyName,
        @NotNull EntityType entityType,
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$") String businessPan,
        @Pattern(regexp = "^[0-9A-Z]{15}$") String gstin,
        String licenceNumber,
        @Future LocalDate licenceValidUntil,
        Integer establishedYear,

        // Business address
        @NotBlank String addressLine1,
        String addressLine2,
        @NotBlank String area,
        @NotBlank String city,
        String taluk,
        @NotBlank String district,
        @NotBlank String state,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$") String pincode,

        // Owner / primary contact
        @NotBlank @Size(max = 100) String ownerName,
        @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$") String primaryMobile,
        @Pattern(regexp = "^[6-9][0-9]{9}$") String alternateMobile,
        String email,
        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$") String ownerPan) {
}
