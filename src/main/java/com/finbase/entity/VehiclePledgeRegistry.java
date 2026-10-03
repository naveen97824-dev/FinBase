package com.finbase.entity;

import com.finbase.entity.enums.PledgeStatus;
import com.finbase.entity.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * The shared cross-company table. No borrower PII, no loan financials —
 * only enough to answer "is this vehicle already pledged, and to whom."
 */
@Entity
@Table(name = "vehicle_pledge_registry")
@Getter
@Setter
public class VehiclePledgeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_number_norm", nullable = false, length = 20)
    private String vehicleNumberNorm;

    @Column(name = "vehicle_number_raw", nullable = false, length = 30)
    private String vehicleNumberRaw;

    @Column(name = "chassis_number_norm", length = 25)
    private String chassisNumberNorm;

    @Column(name = "engine_number_norm", length = 25)
    private String engineNumberNorm;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type")
    private VehicleType vehicleType;

    @Column(name = "make", length = 50)
    private String make;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "manufacturing_year")
    private Short manufacturingYear;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PledgeStatus status = PledgeStatus.active;

    @Column(name = "pledged_on", nullable = false)
    private LocalDate pledgedOn;

    @Column(name = "released_on")
    private LocalDate releasedOn;

    @Column(name = "source", nullable = false, length = 20)
    private String source = "auto";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
