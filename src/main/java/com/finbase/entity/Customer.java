package com.finbase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One record per borrower, reusable across loans. No Aadhaar number column
 * — the photocopy lives in {@link Document} with category 'aadhaar'.
 * Dedup keys are PAN and mobile only, scoped per financier. Never deleted:
 * {@link #active} only hides a customer from default lists.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "customer_code", nullable = false, length = 20)
    private String customerCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "father_spouse_name", length = 100)
    private String fatherSpouseName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "mobile", nullable = false, length = 10)
    private String mobile;

    @Column(name = "alternate_mobile", length = 10)
    private String alternateMobile;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage = "ta";

    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "area", nullable = false, length = 100)
    private String area;

    @Column(name = "city_village", nullable = false, length = 100)
    private String cityVillage;

    @Column(name = "taluk", length = 100)
    private String taluk;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pincode", nullable = false, length = 6)
    private String pincode;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "pan_encrypted", nullable = false)
    private byte[] panEncrypted;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pan_last4", nullable = false, length = 4)
    private String panLast4;

    @Column(name = "pan_hash", nullable = false)
    private String panHash;

    @Column(name = "address_proof_type", length = 30)
    private String addressProofType;

    @Column(name = "address_proof_number", length = 50)
    private String addressProofNumber;

    @Column(name = "address_proof_date")
    private LocalDate addressProofDate;

    @Column(name = "occupation", length = 30)
    private String occupation;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "broker_id")
    private UUID brokerId;

    @Column(name = "is_blacklisted", nullable = false)
    private boolean blacklisted = false;

    @Column(name = "blacklist_reason")
    private String blacklistReason;

    @Column(name = "blacklisted_at")
    private Instant blacklistedAt;

    @Column(name = "consent_given_at")
    private Instant consentGivenAt;

    @Column(name = "registry_consent", nullable = false)
    private boolean registryConsent = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
