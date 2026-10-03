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

/** Audit only — live OTPs live in Redis with a TTL. */
@Entity
@Table(name = "otp_requests")
@EntityListeners(TimestampListener.class)
@Getter
@Setter
public class OtpRequest implements CreatedAtAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mobile", nullable = false, length = 10)
    private String mobile;

    @Column(name = "financier_id")
    private UUID financierId;

    @Column(name = "purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "session_token", nullable = false)
    private UUID sessionToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount = 0;

    @Column(name = "resend_count", nullable = false)
    private short resendCount = 0;

    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus = "queued";

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
