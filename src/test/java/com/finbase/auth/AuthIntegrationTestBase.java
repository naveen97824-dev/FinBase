package com.finbase.auth;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers setup for the OTP/token test suite: real Postgres
 * (Flyway-migrated) and real Redis, since the rate limits and lockouts
 * under test depend on actual TTL expiry behaviour that an in-memory fake
 * would not faithfully reproduce.
 *
 * <p>Unlike {@link com.finbase.db.TenantIsolationTest}, these tests don't
 * need the non-superuser RLS dance — {@code financiers}, {@code
 * otp_requests} and {@code sessions} are explicitly not under Row Level
 * Security (they're the global auth layer), so the default Testcontainers
 * superuser connection is fine here.
 *
 * <p>Deliberately NOT annotated with {@code @Testcontainers}/{@code
 * @Container}: that JUnit extension manages container lifecycle per test
 * class, including stopping containers after each class's tests finish —
 * but {@code postgres}/{@code redis} here are {@code static}, meaning one
 * shared instance across every subclass (OtpServiceTest, TokenServiceTest,
 * RegistrationServiceTest). Letting the extension manage them caused the
 * first test class to tear the containers down before the next class ran,
 * failing every test in it with "connection refused". Lifecycle is instead
 * managed entirely by hand in the static initializer below: start once,
 * reuse for the whole JVM, let Testcontainers' Ryuk reaper clean up at the
 * end of the run as usual.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AuthIntegrationTestBase {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getRedisHost);
        registry.add("spring.data.redis.port", redis::getRedisPort);
    }
}
