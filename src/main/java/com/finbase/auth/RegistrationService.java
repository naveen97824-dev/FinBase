package com.finbase.auth;

import com.finbase.dto.RegisterRequest;
import com.finbase.entity.Financier;
import com.finbase.entity.enums.FinancierStatus;
import com.finbase.repository.FinancierRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Completes registration after the mobile has been OTP-verified (Part 1.2,
 * steps 2–5). The financier is created in {@code pending_verification} —
 * login stays blocked until a platform admin moves it to {@code active}
 * (step 6), which is a separate, not-yet-built admin flow.
 */
@Service
public class RegistrationService {

    private final FinancierRepository financierRepository;
    private final OtpStore otpStore;
    private final JdbcTemplate jdbcTemplate;

    public RegistrationService(FinancierRepository financierRepository, OtpStore otpStore,
            JdbcTemplate jdbcTemplate) {
        this.financierRepository = financierRepository;
        this.otpStore = otpStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Financier register(RegisterRequest request) {
        if (!otpStore.isVerifiedForRegistration(request.primaryMobile(), request.sessionToken())) {
            throw new RegistrationException(
                    "Mobile OTP must be verified before registration can be submitted.");
        }

        if (financierRepository.existsByPrimaryMobile(request.primaryMobile())) {
            throw new RegistrationException("This mobile number is already registered. Log in instead.");
        }
        if (request.businessPan() != null && financierRepository.existsByBusinessPan(request.businessPan())) {
            throw new RegistrationException("This business PAN is already registered.");
        }

        Financier financier = new Financier();
        financier.setFinancierCode(generateFinancierCode());
        financier.setCompanyName(request.companyName());
        financier.setEntityType(request.entityType());
        financier.setBusinessPan(request.businessPan());
        financier.setGstin(request.gstin());
        financier.setLicenceNumber(request.licenceNumber());
        financier.setLicenceValidUntil(request.licenceValidUntil());
        if (request.establishedYear() != null) {
            financier.setEstablishedYear(request.establishedYear().shortValue());
        }

        financier.setAddressLine1(request.addressLine1());
        financier.setAddressLine2(request.addressLine2());
        financier.setArea(request.area());
        financier.setCity(request.city());
        financier.setTaluk(request.taluk());
        financier.setDistrict(request.district());
        financier.setState(request.state());
        financier.setPincode(request.pincode());

        financier.setOwnerName(request.ownerName());
        financier.setOwnerPan(request.ownerPan());
        financier.setPrimaryMobile(request.primaryMobile());
        financier.setAlternateMobile(request.alternateMobile());
        financier.setEmail(request.email());

        financier.setStatus(FinancierStatus.pending_verification);

        Financier saved = financierRepository.save(financier);
        otpStore.clearVerifiedForRegistration(request.primaryMobile());
        return saved;
    }

    private String generateFinancierCode() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('financier_code_seq')", Long.class);
        return "FIN-" + String.format("%06d", next);
    }
}
