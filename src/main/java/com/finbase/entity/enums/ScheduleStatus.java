package com.finbase.entity.enums;

/** Maps to Postgres {@code schedule_status_enum}. */
public enum ScheduleStatus {
    pending,
    due,
    partially_paid,
    paid,
    overdue,
    waived
}
