package com.finbase.entity.enums;

/**
 * Maps to Postgres {@code gold_purity_enum}. Database labels ('18K', '20K',
 * ...) aren't valid Java identifiers, so this uses an explicit {@link #dbValue}
 * with {@link GoldPurityConverter} rather than {@code @Enumerated(STRING)}.
 */
public enum GoldPurity {
    K18("18K"),
    K20("20K"),
    K22("22K"),
    K24("24K");

    private final String dbValue;

    GoldPurity(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static GoldPurity fromDbValue(String dbValue) {
        for (GoldPurity p : values()) {
            if (p.dbValue.equals(dbValue)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown gold purity: " + dbValue);
    }
}
