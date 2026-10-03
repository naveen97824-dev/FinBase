package com.finbase.entity.listener;

import jakarta.persistence.PrePersist;
import java.time.Instant;

/**
 * Sets {@code created_at}/{@code updated_at} on insert. Every mapped
 * entity field is sent explicitly by Hibernate, including {@code null},
 * so a column's SQL {@code DEFAULT now()} never actually fires — without
 * this, every {@code NOT NULL} timestamp column throws on first insert.
 */
public class TimestampListener {

    @PrePersist
    public void prePersist(Object entity) {
        Instant now = Instant.now();
        if (entity instanceof CreatedAtAware created) {
            created.setCreatedAt(now);
        }
        if (entity instanceof UpdatedAtAware updated) {
            updated.setUpdatedAt(now);
        }
    }
}
