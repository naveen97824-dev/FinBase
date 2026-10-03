package com.finbase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Brokers live in their own table, not free text — volume per broker matters. */
@Entity
@Table(name = "brokers")
@Getter
@Setter
public class Broker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "broker_code", nullable = false, length = 20)
    private String brokerCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", nullable = false, length = 10)
    private String phone;

    @Column(name = "alternate_phone", length = 10)
    private String alternatePhone;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "area", length = 100)
    private String area;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
