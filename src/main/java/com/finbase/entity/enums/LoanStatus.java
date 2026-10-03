package com.finbase.entity.enums;

/**
 * Maps to Postgres {@code loan_status_enum}. No draft / pending_approval /
 * approved / rejected — loans start ACTIVE.
 */
public enum LoanStatus {
    active,
    closed,
    foreclosed,
    seized,
    written_off
}
