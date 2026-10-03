package com.finbase.entity;

import com.finbase.entity.enums.PaymentMode;
import com.finbase.entity.enums.TxnType;
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
 * The immutable ledger. Corrections are reversal entries, never edits or
 * deletes — enforced at the database layer by a rule that turns DELETE
 * into a no-op (see the migration).
 */
@Entity
@Table(name = "loan_transactions")
@Getter
@Setter
public class LoanTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false)
    private TxnType txnType;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalComponent = BigDecimal.ZERO;

    @Column(name = "interest_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestComponent = BigDecimal.ZERO;

    @Column(name = "penal_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal penalComponent = BigDecimal.ZERO;

    @Column(name = "charge_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal chargeComponent = BigDecimal.ZERO;

    @Column(name = "excess_component", nullable = false, precision = 15, scale = 2)
    private BigDecimal excessComponent = BigDecimal.ZERO;

    @Column(name = "balance_after", precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Column(name = "receipt_number", length = 30)
    private String receiptNumber;

    @Column(name = "entered_by_name", length = 100)
    private String enteredByName;

    @Column(name = "is_reversed", nullable = false)
    private boolean reversed = false;

    @Column(name = "reversed_by_txn_id")
    private UUID reversedByTxnId;

    @Column(name = "reverses_txn_id")
    private UUID reversesTxnId;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
