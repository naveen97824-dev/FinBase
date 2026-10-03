package com.finbase.entity;

import com.finbase.entity.enums.DocCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Unified document vault — the Aadhaar photocopy lives here, never an Aadhaar number. */
@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "seizure_id")
    private UUID seizureId;

    @Column(name = "expense_id")
    private UUID expenseId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private DocCategory category;

    @Column(name = "file_key", nullable = false)
    private String fileKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256")
    private String checksumSha256;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "issuing_authority", length = 150)
    private String issuingAuthority;

    @Column(name = "original_held", nullable = false)
    private boolean originalHeld = false;

    @Column(name = "custody_location", length = 150)
    private String custodyLocation;

    @Column(name = "version", nullable = false)
    private short version = 1;

    @Column(name = "supersedes_id")
    private UUID supersedesId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
