package com.finbase.entity.enums;

/** Maps to Postgres {@code payment_mode_enum}. */
public enum PaymentMode {
    cash,
    bank_transfer,
    upi,
    cheque,
    card
}
