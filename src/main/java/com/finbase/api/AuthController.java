package com.finbase.api;

import com.finbase.auth.AccountNotActiveException;
import com.finbase.auth.OtpPurpose;
import com.finbase.auth.OtpService;
import com.finbase.auth.RegistrationService;
import com.finbase.auth.TokenPair;
import com.finbase.auth.TokenService;
import com.finbase.dto.OtpRequestRequest;
import com.finbase.dto.OtpRequestResponse;
import com.finbase.dto.OtpVerifyRequest;
import com.finbase.dto.OtpVerifyResponse;
import com.finbase.dto.RefreshRequest;
import com.finbase.dto.RegisterRequest;
import com.finbase.dto.RegisterResponse;
import com.finbase.dto.TokenResponse;
import com.finbase.entity.Financier;
import com.finbase.entity.enums.FinancierStatus;
import com.finbase.repository.FinancierRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Part 1 of the functional spec: registration and mobile-OTP authentication. */
@RestController
public class AuthController {

    private final OtpService otpService;
    private final RegistrationService registrationService;
    private final TokenService tokenService;
    private final FinancierRepository financierRepository;

    public AuthController(OtpService otpService, RegistrationService registrationService,
            TokenService tokenService, FinancierRepository financierRepository) {
        this.otpService = otpService;
        this.registrationService = registrationService;
        this.tokenService = tokenService;
        this.financierRepository = financierRepository;
    }

    @PostMapping("/auth/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequestRequest request, HttpServletRequest http) {
        var sessionToken = otpService.requestOtp(request.mobile(), request.purpose(), http.getRemoteAddr());
        return new OtpRequestResponse(sessionToken);
    }

    @PostMapping("/auth/otp/verify")
    public OtpVerifyResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest http) {
        OtpPurpose purpose = otpService.verifyOtp(request.mobile(), request.otp(), request.sessionToken());

        if (purpose != OtpPurpose.login) {
            return new OtpVerifyResponse(purpose, null, null);
        }

        // Login OTP verified against an existing financier — issue tokens now,
        // unless the account can't log in yet (pending_verification, suspended, closed).
        Financier financier = financierRepository.findByPrimaryMobile(request.mobile())
                .orElseThrow(() -> new NoSuchElementException("Financier not found for " + request.mobile()));

        FinancierStatus status = financier.getStatus();
        if (status != FinancierStatus.active && status != FinancierStatus.read_only) {
            throw new AccountNotActiveException(status);
        }

        TokenPair tokens = tokenService.issueNewSession(
                financier.getId(), null, null, "web", http.getRemoteAddr());
        return new OtpVerifyResponse(purpose, tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/auth/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        Financier financier = registrationService.register(request);
        return new RegisterResponse(financier.getId(), financier.getFinancierCode(), financier.getStatus());
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        TokenPair tokens = tokenService.refresh(request.refreshToken(), http.getRemoteAddr());
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/auth/logout")
    public void logout(@RequestHeader("X-Refresh-Token") String refreshToken) {
        tokenService.logout(refreshToken);
    }
}
