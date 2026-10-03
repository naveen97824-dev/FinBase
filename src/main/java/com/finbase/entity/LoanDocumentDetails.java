package com.finbase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Document-loan (land/property) additional fields. Uniqueness is SRO +
 * year + document number, not the document number alone — the same land
 * can be pledged under different deeds.
 */
@Entity
@Table(name = "loan_document_details")
@Getter
@Setter
public class LoanDocumentDetails {

    @Id
    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "document_number", nullable = false, length = 60)
    private String documentNumber;

    @Column(name = "sro_office", nullable = false, length = 100)
    private String sroOffice;

    @Column(name = "registration_year", nullable = false)
    private short registrationYear;

    @Column(name = "document_type", length = 40)
    private String documentType;

    @Column(name = "survey_number", nullable = false, length = 40)
    private String surveyNumber;

    @Column(name = "subdivision_number", length = 20)
    private String subdivisionNumber;

    @Column(name = "patta_number", length = 40)
    private String pattaNumber;

    @Column(name = "village", nullable = false, length = 100)
    private String village;

    @Column(name = "taluk", nullable = false, length = 100)
    private String taluk;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "land_parcel_key", length = 200)
    private String landParcelKey;

    @Column(name = "extent_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal extentValue;

    @Column(name = "extent_unit", nullable = false, length = 15)
    private String extentUnit;

    @Column(name = "land_classification", length = 30)
    private String landClassification;

    @Column(name = "current_price_per_unit", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentPricePerUnit;

    @Column(name = "guideline_value", precision = 15, scale = 2)
    private BigDecimal guidelineValue;

    @Column(name = "total_valuation", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalValuation;

    @Column(name = "title_holder_count")
    private Short titleHolderCount = 1;

    @Column(name = "ec_number", length = 60)
    private String ecNumber;

    @Column(name = "ec_obtained_on")
    private LocalDate ecObtainedOn;

    @Column(name = "ec_valid_until")
    private LocalDate ecValidUntil;

    @Column(name = "ec_findings", length = 30)
    private String ecFindings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "originals_held_list")
    private String originalsHeldList;
}
