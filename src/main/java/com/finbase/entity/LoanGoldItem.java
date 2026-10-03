package com.finbase.entity;

import com.finbase.entity.enums.GoldPurity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One row per ornament pledged on a gold loan. */
@Entity
@Table(name = "loan_gold_items")
@Getter
@Setter
public class LoanGoldItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "item_seq", nullable = false)
    private short itemSeq;

    @Column(name = "item_type", nullable = false, length = 40)
    private String itemType;

    @Column(name = "quantity", nullable = false)
    private short quantity = 1;

    @Column(name = "weight_g", nullable = false, precision = 10, scale = 3)
    private BigDecimal weightG;

    @Column(name = "purity", nullable = false)
    private GoldPurity purity;

    @Column(name = "hallmark_huid", length = 30)
    private String hallmarkHuid;

    @Column(name = "item_valuation", precision = 15, scale = 2)
    private BigDecimal itemValuation;

    @Column(name = "description")
    private String description;

    @Column(name = "is_released", nullable = false)
    private boolean released = false;

    @Column(name = "released_at")
    private Instant releasedAt;
}
