package com.finbase.entity.enums;

/** Maps to Postgres {@code seizure_reason_enum}. */
public enum SeizureReason {
    non_payment,
    willful_default,
    collateral_misuse,
    asset_being_sold,
    other
}
