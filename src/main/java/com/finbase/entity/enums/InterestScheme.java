package com.finbase.entity.enums;

/**
 * Maps to Postgres {@code interest_scheme_enum}. Scheme C (reducing
 * balance) was removed — only A, B, D exist.
 */
public enum InterestScheme {
    A_monthly_interest_bullet,
    B_flat_emi,
    D_daily_collection
}
