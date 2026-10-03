package com.finbase.entity;

import com.finbase.entity.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Vehicle-loan additional fields. The chassis number is the highest-value
 * field here — it survives re-registration and is what the shared
 * {@link VehiclePledgeRegistry} keys on as a secondary lookup.
 */
@Entity
@Table(name = "loan_vehicle_details")
@Getter
@Setter
public class LoanVehicleDetails {

    @Id
    @Column(name = "loan_id")
    private UUID loanId;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "vehicle_number_raw", nullable = false, length = 30)
    private String vehicleNumberRaw;

    @Column(name = "vehicle_number_norm", nullable = false, length = 20)
    private String vehicleNumberNorm;

    @Column(name = "chassis_number", length = 25)
    private String chassisNumber;

    @Column(name = "chassis_number_norm", length = 25)
    private String chassisNumberNorm;

    @Column(name = "engine_number", length = 25)
    private String engineNumber;

    @Column(name = "engine_number_norm", length = 25)
    private String engineNumberNorm;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "is_commercial", nullable = false)
    private boolean commercial = false;

    @Column(name = "make", nullable = false, length = 50)
    private String make;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "variant", length = 50)
    private String variant;

    @Column(name = "manufacturing_year", nullable = false)
    private short manufacturingYear;

    @Column(name = "fuel_type", length = 20)
    private String fuelType;

    @Column(name = "colour", length = 30)
    private String colour;

    @Column(name = "odometer_reading")
    private Integer odometerReading;

    @Column(name = "rc_number", length = 30)
    private String rcNumber;

    @Column(name = "rc_owner_name", nullable = false, length = 100)
    private String rcOwnerName;

    @Column(name = "rc_registration_date")
    private LocalDate rcRegistrationDate;

    @Column(name = "rc_original_held", nullable = false)
    private boolean rcOriginalHeld = false;

    @Column(name = "insurance_company", nullable = false, length = 100)
    private String insuranceCompany;

    @Column(name = "insurance_policy_number", nullable = false, length = 60)
    private String insurancePolicyNumber;

    @Column(name = "insurance_expiry", nullable = false)
    private LocalDate insuranceExpiry;

    @Column(name = "licence_number", nullable = false, length = 30)
    private String licenceNumber;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Column(name = "permit_number", length = 50)
    private String permitNumber;

    @Column(name = "permit_expiry")
    private LocalDate permitExpiry;

    @Column(name = "fitness_expiry")
    private LocalDate fitnessExpiry;

    @Column(name = "vehicle_valuation", nullable = false, precision = 15, scale = 2)
    private BigDecimal vehicleValuation;
}
