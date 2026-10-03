package com.finbase.entity.listener;

import java.time.Instant;

/** Implemented by every entity with a {@code created_at TIMESTAMPTZ NOT NULL} column. */
public interface CreatedAtAware {

    void setCreatedAt(Instant createdAt);
}
