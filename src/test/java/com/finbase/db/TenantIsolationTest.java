package com.finbase.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Company B must never see Company A's rows. Run against real Postgres — H2
 * has no Row Level Security and would pass meaninglessly. See CLAUDE.md /
 * tech-stack doc §2.2 (week-one checklist, item 1).
 *
 * <p>Must connect as a non-superuser: Postgres superusers bypass Row Level
 * Security entirely, even with FORCE ROW LEVEL SECURITY, which would make
 * this test pass meaninglessly too. Testcontainers' PostgreSQLContainer
 * connects as its default superuser, so a dedicated app role is created and
 * Flyway is run manually as the superuser (ALTER TABLE ... FORCE ROW LEVEL
 * SECURITY requires owner/superuser) before Spring Boot's own datasource —
 * pointed at the unprivileged app role — ever opens a connection. Spring
 * Boot's Flyway auto-configuration is disabled so it doesn't try to
 * re-migrate as that unprivileged role.
 *
 * <p>All of this must happen inside {@code @DynamicPropertySource} — it's the
 * only hook guaranteed to run before Spring builds the ApplicationContext.
 * {@code @BeforeAll} is a plain JUnit lifecycle hook with no such guarantee
 * relative to Spring's (lazy) context preparation, so role/schema/grant setup
 * does not belong there — a first attempt that used {@code @BeforeAll} for
 * the grants ran before the context (and thus the role) existed.
 */
@Testcontainers
@SpringBootTest
class TenantIsolationTest {

    private static final String APP_ROLE = "finbase_app";
    private static final String APP_PASSWORD = "finbase_app_pw";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        try (var conn = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
        }

        // Run Flyway here, synchronously, as the superuser — ALTER TABLE ...
        // FORCE ROW LEVEL SECURITY requires owner/superuser — then grant the
        // app role access to the new schema before Spring's own datasource
        // (pointed at the app role, configured below) does anything.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var conn = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO " + APP_ROLE);
            stmt.execute("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO " + APP_ROLE);
            stmt.execute("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO " + APP_ROLE);
        }

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        // Flyway already ran above (as superuser); don't let Spring Boot's
        // Flyway auto-configuration try to run it again as the app role,
        // which lacks the privileges FORCE ROW LEVEL SECURITY needs.
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private TenantSession tenantSession;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID insertFinancier(String code, String mobile) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO financiers (id, financier_code, company_name, entity_type,
                    address_line1, area, city, district, state, pincode,
                    owner_name, primary_mobile, status)
                VALUES (?, ?, ?, 'individual', 'Addr 1', 'Area', 'City', 'District',
                    'TamilNadu', '600001', 'Owner', ?, 'active')
                """, id, code, code, mobile);
        return id;
    }

    private UUID insertCustomer(UUID financierId, String code, String mobile) {
        UUID id = UUID.randomUUID();
        tenantSession.execute(financierId, tx -> jdbcTemplate.update("""
                INSERT INTO customers (id, financier_id, customer_code, full_name, mobile,
                    address_line1, area, city_village, district, state, pincode,
                    pan_encrypted, pan_last4, pan_hash)
                VALUES (?, ?, ?, 'Test Customer', ?,
                    'Addr 1', 'Area', 'City', 'District', 'TamilNadu', '600001',
                    '\\x00', '0000', ?)
                """, id, financierId, code, mobile, UUID.randomUUID().toString()));
        return id;
    }

    private void insertLoan(UUID financierId, UUID customerId, String loanNumber) {
        tenantSession.execute(financierId, tx -> jdbcTemplate.update("""
                INSERT INTO loans (financier_id, loan_number, customer_id, loan_type,
                    principal_amount, interest_rate, tenure_value, penal_percent)
                VALUES (?, ?, ?, 'personal', 10000, 2.0, 12, 2.0)
                """, financierId, loanNumber, customerId));
    }

    @Test
    void companyBSeesNothingFromCompanyA() {
        UUID companyA = insertFinancier("FIN-TEST-A", "9000000001");
        UUID companyB = insertFinancier("FIN-TEST-B", "9000000002");

        UUID customerA = insertCustomer(companyA, "CUS-A-001", "9000000003");
        insertLoan(companyA, customerA, "LN-TEST-A-001");

        List<Map<String, Object>> rowsSeenByB = tenantSession.execute(companyB,
                tx -> jdbcTemplate.queryForList("SELECT * FROM loans"));

        assertThat(rowsSeenByB).isEmpty();

        List<Map<String, Object>> rowsSeenByA = tenantSession.execute(companyA,
                tx -> jdbcTemplate.queryForList("SELECT * FROM loans"));

        assertThat(rowsSeenByA).hasSize(1);
    }
}
