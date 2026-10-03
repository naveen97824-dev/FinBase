package com.finbase.entity.enums;

/** Maps to Postgres {@code payment_status_enum}. */
public enum PaymentStatus {
    pending,
    paid,
    partially_paid,
    skipped,
    overdue
}
