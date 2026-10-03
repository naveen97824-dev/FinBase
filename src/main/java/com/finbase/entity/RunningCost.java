package com.finbase.entity;

import com.finbase.entity.enums.Frequency;
import com.finbase.entity.enums.PaymentMode;
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

/** A recurring cost template — generates {@link RunningCostPayment} rows each period. */
@Entity
@Table(name = "running_costs")
@Getter
@Setter
public class RunningCost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "cost_code", nullable = false, length = 25)
    private String costCode;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "due_day_of_period", nullable = false)
    private short dueDayOfPeriod;

    @Column(name = "payee_name", nullable = false, length = 150)
    private String payeeName;

    @Column(name = "payee_contact", length = 15)
    private String payeeContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private PaymentMode paymentMode;

    @Column(name = "employee_name", length = 100)
    private String employeeName;

    @Column(name = "auto_generate", nullable = false)
    private boolean autoGenerate = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
