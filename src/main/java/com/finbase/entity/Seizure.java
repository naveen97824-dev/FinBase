package com.finbase.entity;

import com.finbase.entity.enums.LoanType;
import com.finbase.entity.enums.SeizureReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * Simplified in v2: no status enum, no owner-approval chain. Current state
 * is derived from which dates are filled in (see {@code v_seizure_status}
 * in the migration) — released if {@link #releaseDate} is set, sold if
 * {@link #saleDate} is set, otherwise in custody.
 */
@Entity
@Table(name = "seizures")
@Getter
@Setter
public class Seizure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "seizure_number", nullable = false, length = 25)
    private String seizureNumber;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "collateral_type", nullable = false)
    private LoanType collateralType;

    @Column(name = "collateral_reference", nullable = false, length = 100)
    private String collateralReference;

    @Column(name = "seizure_date", nullable = false)
    private LocalDate seizureDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private SeizureReason reason;

    @Column(name = "reason_detail")
    private String reasonDetail;

    @Column(name = "outstanding_at_seizure", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingAtSeizure;

    @Column(name = "days_overdue_at_seizure")
    private Integer daysOverdueAtSeizure;

    @Column(name = "notice_served", nullable = false)
    private boolean noticeServed = false;

    @Column(name = "notice_date")
    private LocalDate noticeDate;

    @Column(name = "notice_mode", length = 40)
    private String noticeMode;

    @Column(name = "agency_name", length = 150)
    private String agencyName;

    @Column(name = "current_valuation", precision = 15, scale = 2)
    private BigDecimal currentValuation;

    @Column(name = "valuation_date")
    private LocalDate valuationDate;

    @Column(name = "sale_date")
    private LocalDate saleDate;

    @Column(name = "sale_mode", length = 30)
    private String saleMode;

    @Column(name = "sale_price", precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "buyer_name", length = 150)
    private String buyerName;

    @Column(name = "recovery_charges", nullable = false, precision = 15, scale = 2)
    private BigDecimal recoveryCharges = BigDecimal.ZERO;

    @Column(name = "surplus_amount", precision = 15, scale = 2)
    private BigDecimal surplusAmount;

    @Column(name = "shortfall_amount", precision = 15, scale = 2)
    private BigDecimal shortfallAmount;

    @Column(name = "surplus_refunded", nullable = false)
    private boolean surplusRefunded = false;

    @Column(name = "surplus_refund_date")
    private LocalDate surplusRefundDate;

    @Column(name = "surplus_refund_ref", length = 100)
    private String surplusRefundRef;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "released_to", length = 150)
    private String releasedTo;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
