package com.finbase.repository;

import com.finbase.entity.Financier;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code financiers} is not under Row Level Security — it is the global
 * auth/account layer, reached directly, not through {@link
 * com.finbase.db.TenantSession}.
 */
public interface FinancierRepository extends JpaRepository<Financier, UUID> {

    Optional<Financier> findByPrimaryMobile(String primaryMobile);

    boolean existsByPrimaryMobile(String primaryMobile);

    boolean existsByBusinessPan(String businessPan);
}
