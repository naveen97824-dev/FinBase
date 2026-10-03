package com.finbase.entity;

import com.finbase.entity.enums.ScheduleStatus;
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
import com.finbase.entity.listener.UpdatedAtAware;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row per installment. Drives Current Dues and Pending Dues. */
@Entity
@Table(name = "loan_schedule")
@EntityListeners(TimestampListener.class)
@Getter
@Setter
public class LoanSchedule implements CreatedAtAware, UpdatedAtAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "installment_no", nullable = false)
    private short installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal duePrincipal = BigDecimal.ZERO;

    @Column(name = "due_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal dueInterest = BigDecimal.ZERO;

    @Column(name = "due_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal dueTotal;

    @Column(name = "opening_balance", precision = 15, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 15, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "paid_principal", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidPrincipal = BigDecimal.ZERO;

    @Column(name = "paid_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidInterest = BigDecimal.ZERO;

    @Column(name = "paid_penal", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidPenal = BigDecimal.ZERO;

    @Column(name = "paid_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidTotal = BigDecimal.ZERO;

    @Column(name = "last_paid_date")
    private LocalDate lastPaidDate;

    @Column(name = "penal_accrued", nullable = false, precision = 15, scale = 2)
    private BigDecimal penalAccrued = BigDecimal.ZERO;

    @Column(name = "waived_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal waivedAmount = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleStatus status = ScheduleStatus.pending;

    @Column(name = "days_past_due", nullable = false)
    private int daysPastDue = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
