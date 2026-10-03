package com.finbase.entity;

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

/** Ad-hoc, usually-cash expense entry. Reversals are contra entries, never deletes. */
@Entity
@Table(name = "petty_expenses")
@Getter
@Setter
public class PettyExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "expense_code", nullable = false, length = 25)
    private String expenseCode;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate = LocalDate.now();

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private PaymentMode paymentMode;

    @Column(name = "paid_to", length = 150)
    private String paidTo;

    @Column(name = "receipt_document_id")
    private UUID receiptDocumentId;

    @Column(name = "linked_loan_id")
    private UUID linkedLoanId;

    @Column(name = "linked_seizure_id")
    private UUID linkedSeizureId;

    @Column(name = "is_recoverable", nullable = false)
    private boolean recoverable = false;

    @Column(name = "entered_by_name", length = 100)
    private String enteredByName;

    @Column(name = "is_reversed", nullable = false)
    private boolean reversed = false;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
