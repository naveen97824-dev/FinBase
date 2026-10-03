package com.finbase.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link DailySnapshot}: one row per financier per day. */
public class DailySnapshotId implements Serializable {

    private UUID financierId;
    private LocalDate snapshotDate;

    public DailySnapshotId() {
    }

    public DailySnapshotId(UUID financierId, LocalDate snapshotDate) {
        this.financierId = financierId;
        this.snapshotDate = snapshotDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DailySnapshotId that)) {
            return false;
        }
        return Objects.equals(financierId, that.financierId) && Objects.equals(snapshotDate, that.snapshotDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(financierId, snapshotDate);
    }
}
