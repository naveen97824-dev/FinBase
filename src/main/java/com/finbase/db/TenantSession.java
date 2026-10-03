package com.finbase.db;

import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one and only way a request's financier_id reaches Postgres' Row Level
 * Security policies. {@code SET LOCAL} only applies inside a transaction, and
 * HikariCP pools connections — so the set_config call and the business logic
 * must run as a single unambiguous block on the same bound connection.
 *
 * <p>Do not reimplement this with {@code @Transactional} + ThreadLocal + an
 * {@code @Around} aspect. Getting the ordering right there (set_config must
 * run after the transaction begins but before any other query) depends on
 * {@code @Order} values that are easy to get subtly wrong, and a wrong answer
 * here is a cross-company data leak. See CLAUDE.md / tech-stack doc §2.2.
 */
@Component
@RequiredArgsConstructor
public class TenantSession {

    private final PlatformTransactionManager txManager;
    private final JdbcTemplate jdbcTemplate;

    public <T> T execute(UUID financierId, Function<Void, T> work) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        return tt.execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT set_config('app.current_financier_id', ?, true)",
                    String.class, financierId.toString());
            return work.apply(null);
        });
    }
}
