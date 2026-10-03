package com.finbase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Shared daily reference rate for gold valuation. */
@Entity
@Table(name = "gold_rates")
@Getter
@Setter
public class GoldRate {

    @Id
    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "rate_24k_per_gram", nullable = false, precision = 10, scale = 2)
    private BigDecimal rate24kPerGram;

    @Column(name = "rate_22k_per_gram", precision = 10, scale = 2)
    private BigDecimal rate22kPerGram;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
