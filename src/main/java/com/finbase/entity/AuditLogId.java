package com.finbase.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite key for {@link AuditLog}: the table is range-partitioned on
 * {@code created_at}, so Postgres requires the partition key in the
 * primary key alongside the generated {@code id}.
 */
public class AuditLogId implements Serializable {

    private Long id;
    private Instant createdAt;

    public AuditLogId() {
    }

    public AuditLogId(Long id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditLogId that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
