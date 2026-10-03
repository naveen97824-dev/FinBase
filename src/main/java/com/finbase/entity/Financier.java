package com.finbase.entity;

import com.finbase.entity.enums.EntityType;
import com.finbase.entity.enums.FinancierStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.finbase.entity.listener.CreatedAtAware;
import com.finbase.entity.listener.TimestampListener;
import com.finbase.entity.listener.UpdatedAtAware;

/**
 * One row per company — also the login account, since there is exactly one
 * shared login per company. Not under Row Level Security: this and the
 * other global tables are the shared/auth layer, reached only through
 * dedicated service roles with narrow read contracts.
 */
@Entity
@Table(name = "financiers")
@EntityListeners(TimestampListener.class)
@Getter
@Setter
public class Financier implements CreatedAtAware, UpdatedAtAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_code", nullable = false, unique = true, length = 20)
    private String financierCode;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(name = "business_pan", length = 10)
    private String businessPan;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "established_year")
    private Short establishedYear;

    @Column(name = "licence_number", length = 60)
    private String licenceNumber;

    @Column(name = "licence_authority", length = 150)
    private String licenceAuthority;

    @Column(name = "licence_valid_until")
    private LocalDate licenceValidUntil;

    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "area", nullable = false, length = 100)
    private String area;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "taluk", length = 100)
    private String taluk;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pincode", nullable = false, length = 6)
    private String pincode;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(name = "owner_pan", length = 10)
    private String ownerPan;

    @Column(name = "primary_mobile", nullable = false, unique = true, length = 10)
    private String primaryMobile;

    @Column(name = "alternate_mobile", length = 10)
    private String alternateMobile;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "mpin_hash")
    private String mpinHash;

    @Column(name = "biometric_enabled", nullable = false)
    private boolean biometricEnabled = false;

    @Column(name = "last_otp_verified_at")
    private Instant lastOtpVerifiedAt;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FinancierStatus status = FinancierStatus.pending_verification;

    @Column(name = "subscription_tier", length = 30)
    private String subscriptionTier = "basic";

    @Column(name = "subscription_valid_until")
    private LocalDate subscriptionValidUntil;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "registry_enrolled", nullable = false)
    private boolean registryEnrolled = true;

    @Column(name = "registry_query_limit", nullable = false)
    private int registryQueryLimit = 100;

    @Column(name = "registry_suspended", nullable = false)
    private boolean registrySuspended = false;

    @Column(name = "registry_suspend_reason")
    private String registrySuspendReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
