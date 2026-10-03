package com.finbase.entity;

import com.finbase.entity.enums.InterestScheme;
import com.finbase.entity.enums.PenalType;
import com.finbase.entity.enums.RateType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Per-company configuration. Notification and approval settings were removed in v2. */
@Entity
@Table(name = "financier_settings")
@Getter
@Setter
public class FinancierSettings {

    @Id
    @Column(name = "financier_id")
    private UUID financierId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "default_interest_scheme", nullable = false)
    private InterestScheme defaultInterestScheme = InterestScheme.A_monthly_interest_bullet;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "default_rate_type", nullable = false)
    private RateType defaultRateType = RateType.per_month;

    @Column(name = "default_grace_days", nullable = false)
    private short defaultGraceDays = 5;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "default_penal_type", nullable = false)
    private PenalType defaultPenalType = PenalType.percentage;

    @Column(name = "default_penal_percent", precision = 6, scale = 3)
    private BigDecimal defaultPenalPercent = new BigDecimal("2.000");

    @Column(name = "default_penal_fixed", precision = 15, scale = 2)
    private BigDecimal defaultPenalFixed = BigDecimal.ZERO;

    @Column(name = "interest_rate_ceiling", precision = 6, scale = 3)
    private BigDecimal interestRateCeiling;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "appropriation_order", nullable = false)
    private String appropriationOrder = "[\"penal\",\"charges\",\"interest\",\"principal\"]";

    @Column(name = "petty_expense_receipt_prompt", nullable = false, precision = 15, scale = 2)
    private BigDecimal pettyExpenseReceiptPrompt = new BigDecimal("500");

    @Column(name = "require_otp_every_login", nullable = false)
    private boolean requireOtpEveryLogin = true;

    @Column(name = "trusted_device_days", nullable = false)
    private short trustedDeviceDays = 30;

    @Column(name = "print_header_text", length = 200)
    private String printHeaderText;

    @Column(name = "print_footer_text", length = 200)
    private String printFooterText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
