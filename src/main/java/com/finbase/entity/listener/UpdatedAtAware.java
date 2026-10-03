package com.finbase.entity.listener;

import java.time.Instant;

/**
 * Implemented by every entity with an {@code updated_at TIMESTAMPTZ NOT
 * NULL} column. The database trigger {@code set_updated_at()} (see the
 * Flyway baseline) re-bumps this on every {@code UPDATE}; the app only
 * needs to supply an initial value on {@code INSERT}, since Hibernate
 * always sends an explicit column value and so never falls through to
 * the column's {@code DEFAULT now()}.
 */
public interface UpdatedAtAware {

    void setUpdatedAt(Instant updatedAt);
}
