package com.finbase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.finbase.entity.listener.CreatedAtAware;
import com.finbase.entity.listener.TimestampListener;

/**
 * Audit of every vehicle registry lookup. Powers rate limiting, enumeration
 * detection, and reciprocity scoring.
 */
@Entity
@Table(name = "vehicle_inspection_queries")
@EntityListeners(TimestampListener.class)
@Getter
@Setter
public class VehicleInspectionQuery implements CreatedAtAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "financier_id", nullable = false)
    private UUID financierId;

    @Column(name = "queried_vehicle_raw", nullable = false, length = 30)
    private String queriedVehicleRaw;

    @Column(name = "queried_vehicle_norm", nullable = false, length = 20)
    private String queriedVehicleNorm;

    @Column(name = "queried_chassis_norm", length = 25)
    private String queriedChassisNorm;

    @Column(name = "result_found", nullable = false)
    private boolean resultFound;

    @Column(name = "matched_registry_id")
    private UUID matchedRegistryId;

    @Column(name = "matched_on", length = 20)
    private String matchedOn;

    @Column(name = "query_context", nullable = false, length = 30)
    private String queryContext;

    @Column(name = "linked_loan_id")
    private UUID linkedLoanId;

    @Column(name = "stated_reason")
    private String statedReason;

    @Column(name = "overridden", nullable = false)
    private boolean overridden = false;

    @Column(name = "override_reason")
    private String overrideReason;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
