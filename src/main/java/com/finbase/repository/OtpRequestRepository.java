package com.finbase.repository;

import com.finbase.entity.OtpRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Audit-only writes — the live OTP state lives in Redis ({@link com.finbase.auth.OtpStore}). */
public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID> {
}
