package com.finbase.entity;

import com.finbase.entity.enums.Frequency;
import com.finbase.entity.enums.InterestScheme;
import com.finbase.entity.enums.LoanStatus;
import com.finbase.entity.enums.LoanType;
import com.finbase.entity.enums.PaymentMode;
import com.finbase.entity.enums.PenalType;
import com.finbase.entity.enums.RateType;
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
 * No approval fields — a created loan starts ACTIVE immediately.
 * {@code OVERDUE} is derived nightly from the schedule, never stored here.
 */
@Entity
@Table(name = "loans")
@Getter
@Setter
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "loan_number", nullable = false, length = 25)
    private String loanNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "guarantor_customer_id")
    private UUID guarantorCustomerId;

    @Column(name = "broker_id")
    private UUID brokerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false)
    private LoanType loanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LoanStatus status = LoanStatus.active;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false)
    private RateType rateType = RateType.per_month;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_scheme", nullable = false)
    private InterestScheme interestScheme = InterestScheme.A_monthly_interest_bullet;

    @Column(name = "tenure_value", nullable = false)
    private int tenureValue;

    @Column(name = "tenure_unit", nullable = false, length = 10)
    private String tenureUnit = "months";

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false)
    private Frequency repaymentFrequency = Frequency.monthly;

    @Column(name = "processing_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal processingFee = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "net_disbursal_amount", precision = 15, scale = 2)
    private BigDecimal netDisbursalAmount;

    @Column(name = "foreclosure_charge_pct", precision = 6, scale = 3)
    private BigDecimal foreclosureChargePct = BigDecimal.ZERO;

    @Column(name = "grace_days", nullable = false)
    private short graceDays = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "penal_type", nullable = false)
    private PenalType penalType = PenalType.percentage;

    @Column(name = "penal_percent", precision = 6, scale = 3)
    private BigDecimal penalPercent;

    @Column(name = "penal_fixed_amount", precision = 15, scale = 2)
    private BigDecimal penalFixedAmount;

    @Column(name = "penal_repeats_monthly", nullable = false)
    private boolean penalRepeatsMonthly = false;

    @Column(name = "collateral_valuation", precision = 15, scale = 2)
    private BigDecimal collateralValuation;

    @Column(name = "loan_date", nullable = false)
    private LocalDate loanDate = LocalDate.now();

    @Column(name = "disbursal_date", nullable = false)
    private LocalDate disbursalDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "disbursal_mode")
    private PaymentMode disbursalMode;

    @Column(name = "disbursal_reference", length = 100)
    private String disbursalReference;

    @Column(name = "first_due_date")
    private LocalDate firstDueDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "total_interest_payable", precision = 15, scale = 2)
    private BigDecimal totalInterestPayable = BigDecimal.ZERO;

    @Column(name = "total_repayable", precision = 15, scale = 2)
    private BigDecimal totalRepayable = BigDecimal.ZERO;

    @Column(name = "principal_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "penal_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal penalPaid = BigDecimal.ZERO;

    @Column(name = "outstanding_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingPrincipal = BigDecimal.ZERO;

    @Column(name = "outstanding_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingInterest = BigDecimal.ZERO;

    @Column(name = "outstanding_penal", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstandingPenal = BigDecimal.ZERO;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "inspection_query_id")
    private UUID inspectionQueryId;

    @Column(name = "inspection_result", length = 20)
    private String inspectionResult;

    @Column(name = "inspection_overridden", nullable = false)
    private boolean inspectionOverridden = false;

    @Column(name = "inspection_override_reason")
    private String inspectionOverrideReason;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
