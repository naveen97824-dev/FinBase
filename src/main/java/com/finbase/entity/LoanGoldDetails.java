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

/**
 * Gold-loan additional fields (simplified: no net weight, deduction,
 * purity test method, max LTV, appraiser, seal number, or storage
 * location). Purity is mandatory — weight alone cannot value the pledge.
 */
@Entity
@Table(name = "loan_gold_details")
@Getter
@Setter
public class LoanGoldDetails {

    @Id
    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "total_weight_g", nullable = false, precision = 10, scale = 3)
    private BigDecimal totalWeightG;

    @Column(name = "weighted_avg_purity", precision = 5, scale = 2)
    private BigDecimal weightedAvgPurity;

    @Column(name = "item_count", nullable = false)
    private short itemCount = 0;

    @Column(name = "gold_rate_per_gram_24k", nullable = false, precision = 10, scale = 2)
    private BigDecimal goldRatePerGram24k;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "rate_manually_overridden", nullable = false)
    private boolean rateManuallyOverridden = false;

    @Column(name = "computed_valuation", nullable = false, precision = 15, scale = 2)
    private BigDecimal computedValuation;

    @Column(name = "entered_valuation", nullable = false, precision = 15, scale = 2)
    private BigDecimal enteredValuation;

    @Column(name = "valuation_variance_pct", precision = 6, scale = 3)
    private BigDecimal valuationVariancePct;
}
