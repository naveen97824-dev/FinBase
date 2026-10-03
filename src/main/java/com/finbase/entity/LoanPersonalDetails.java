package com.finbase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Personal-loan additional fields. {@code loan_id} is both the primary key
 * and the FK to {@link Loan} — a strict 1:1 extension table, not its own
 * aggregate. Only Reference 1 is mandatory; Reference 2 is optional.
 */
@Entity
@Table(name = "loan_personal_details")
@Getter
@Setter
public class LoanPersonalDetails {

    @Id
    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "employer_name", nullable = false, length = 150)
    private String employerName;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "net_monthly_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal netMonthlySalary;

    @Column(name = "salary_credit_day")
    private Short salaryCreditDay;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "bank_branch", length = 100)
    private String bankBranch;

    @Column(name = "ifsc_code", length = 11)
    private String ifscCode;

    @Column(name = "account_number_encrypted")
    private byte[] accountNumberEncrypted;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "account_number_last4", length = 4)
    private String accountNumberLast4;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_physically_held", nullable = false)
    private boolean cardPhysicallyHeld = false;

    @Column(name = "card_custody_location", length = 150)
    private String cardCustodyLocation;

    @Column(name = "ref1_name", nullable = false, length = 100)
    private String ref1Name;

    @Column(name = "ref1_phone", nullable = false, length = 10)
    private String ref1Phone;

    @Column(name = "ref1_relationship", length = 50)
    private String ref1Relationship;

    @Column(name = "ref1_verified")
    private Boolean ref1Verified;

    @Column(name = "ref2_name", length = 100)
    private String ref2Name;

    @Column(name = "ref2_phone", length = 10)
    private String ref2Phone;

    @Column(name = "ref2_relationship", length = 50)
    private String ref2Relationship;

    @Column(name = "ref2_verified")
    private Boolean ref2Verified;
}
