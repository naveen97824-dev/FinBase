package com.finbase.entity.enums;

/** Maps to Postgres {@code financier_status_enum}. */
public enum FinancierStatus {
    pending_verification,
    active,
    suspended,
    read_only,
    closed
}
