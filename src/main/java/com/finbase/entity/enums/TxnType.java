package com.finbase.entity.enums;

/** Maps to Postgres {@code txn_type_enum}. */
public enum TxnType {
    disbursal,
    repayment,
    interest_accrual,
    penal_charge,
    charge,
    waiver,
    write_off,
    reversal,
    refund
}
