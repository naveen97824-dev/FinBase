package com.finbase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.finbase.entity.listener.CreatedAtAware;
import com.finbase.entity.listener.TimestampListener;

/**
 * Precomputed dashboard aggregates. The dashboard reads this table, never
 * the live ledger — scanning loan_transactions directly would crawl past a
 * few thousand loans.
 */
@Entity
@Table(name = "daily_snapshots")
@EntityListeners(TimestampListener.class)
@IdClass(DailySnapshotId.class)
@Getter
@Setter
public class DailySnapshot implements CreatedAtAware {

    @Id
    @Column(name = "financier_id")
    private UUID financierId;

    @Id
    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

    @Column(name = "active_loan_count", nullable = false)
    private int activeLoanCount = 0;

    @Column(name = "total_disbursed", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalDisbursed = BigDecimal.ZERO;

    @Column(name = "outstanding_principal", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingPrincipal = BigDecimal.ZERO;

    @Column(name = "outstanding_interest", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingInterest = BigDecimal.ZERO;

    @Column(name = "collected_today", nullable = false, precision = 18, scale = 2)
    private BigDecimal collectedToday = BigDecimal.ZERO;

    @Column(name = "overdue_count", nullable = false)
    private int overdueCount = 0;

    @Column(name = "overdue_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal overdueAmount = BigDecimal.ZERO;

    @Column(name = "par_1_30", nullable = false, precision = 18, scale = 2)
    private BigDecimal par1To30 = BigDecimal.ZERO;

    @Column(name = "par_31_60", nullable = false, precision = 18, scale = 2)
    private BigDecimal par31To60 = BigDecimal.ZERO;

    @Column(name = "par_61_90", nullable = false, precision = 18, scale = 2)
    private BigDecimal par61To90 = BigDecimal.ZERO;

    @Column(name = "par_90_plus", nullable = false, precision = 18, scale = 2)
    private BigDecimal par90Plus = BigDecimal.ZERO;

    @Column(name = "collateral_value_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal collateralValueTotal = BigDecimal.ZERO;

    @Column(name = "collateral_value_at_risk", nullable = false, precision = 18, scale = 2)
    private BigDecimal collateralValueAtRisk = BigDecimal.ZERO;

    @Column(name = "expense_today", nullable = false, precision = 18, scale = 2)
    private BigDecimal expenseToday = BigDecimal.ZERO;

    @Column(name = "interest_income_mtd", nullable = false, precision = 18, scale = 2)
    private BigDecimal interestIncomeMtd = BigDecimal.ZERO;

    @Column(name = "expense_mtd", nullable = false, precision = 18, scale = 2)
    private BigDecimal expenseMtd = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "by_loan_type")
    private String byLoanType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
