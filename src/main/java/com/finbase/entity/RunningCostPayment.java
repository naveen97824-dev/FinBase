package com.finbase.entity;

import com.finbase.entity.enums.PaymentMode;
import com.finbase.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import com.finbase.entity.listener.CreatedAtAware;
import com.finbase.entity.listener.TimestampListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One generated payable per period for a {@link RunningCost} template. */
@Entity
@Table(name = "running_cost_payments")
@EntityListeners(TimestampListener.class)
@Getter
@Setter
public class RunningCostPayment implements CreatedAtAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "running_cost_id", nullable = false)
    private UUID runningCostId;

    @Column(name = "period_label", nullable = false, length = 20)
    private String periodLabel;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.pending;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Column(name = "receipt_document_id")
    private UUID receiptDocumentId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
