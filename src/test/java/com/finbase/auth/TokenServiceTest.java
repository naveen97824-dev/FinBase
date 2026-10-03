package com.finbase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finbase.entity.Financier;
import com.finbase.entity.enums.EntityType;
import com.finbase.entity.enums.FinancierStatus;
import com.finbase.repository.FinancierRepository;
import com.finbase.repository.SessionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Refresh-token rotation and reuse detection (functional spec Part 1.4).
 * Reuse of an already-rotated-away token must revoke the entire session
 * family — this is the standard defence against a stolen refresh token
 * being used alongside the legitimate client, and must never be weakened.
 */
class TokenServiceTest extends AuthIntegrationTestBase {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private FinancierRepository financierRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private UUID registerFinancier() {
        Financier f = new Financier();
        f.setFinancierCode("FIN-" + UUID.randomUUID().toString().substring(0, 6));
        f.setCompanyName("Test Co");
        f.setEntityType(EntityType.individual);
        f.setAddressLine1("Addr");
        f.setArea("Area");
        f.setCity("City");
        f.setDistrict("District");
        f.setState("State");
        f.setPincode("600001");
        f.setOwnerName("Owner");
        f.setPrimaryMobile("9" + String.format("%09d", Math.abs(new java.util.Random().nextInt(100_000_000))));
        f.setStatus(FinancierStatus.active);
        return financierRepository.save(f).getId();
    }

    @Test
    void issueNewSessionProducesWorkingAccessAndRefreshTokens() {
        UUID financierId = registerFinancier();
        TokenPair pair = tokenService.issueNewSession(financierId, "device-1", "Pixel 8", "android", "127.0.0.1");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
    }

    @Test
    void refreshRotatesToANewTokenInTheSameFamily() {
        UUID financierId = registerFinancier();
        TokenPair first = tokenService.issueNewSession(financierId, null, null, "web", "127.0.0.1");

        TokenPair second = tokenService.refresh(first.refreshToken(), "127.0.0.1");

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotBlank();
    }

    @Test
    void reusingAnAlreadyRotatedTokenIsRejected() {
        UUID financierId = registerFinancier();
        TokenPair first = tokenService.issueNewSession(financierId, null, null, "web", "127.0.0.1");
        tokenService.refresh(first.refreshToken(), "127.0.0.1"); // rotates; first.refreshToken() now stale

        assertThatThrownBy(() -> tokenService.refresh(first.refreshToken(), "127.0.0.1"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessageContaining("reuse");
    }

    @Test
    void reuseOfARotatedTokenRevokesTheEntireFamilyIncludingTheCurrentToken() {
        UUID financierId = registerFinancier();
        TokenPair first = tokenService.issueNewSession(financierId, null, null, "web", "127.0.0.1");
        TokenPair second = tokenService.refresh(first.refreshToken(), "127.0.0.1");

        // Present the stale (already-rotated) token: reuse detected.
        assertThatThrownBy(() -> tokenService.refresh(first.refreshToken(), "127.0.0.1"))
                .isInstanceOf(RefreshTokenException.class);

        // The CURRENT, legitimately-issued token must now also be rejected —
        // reuse revokes the whole family, not just the stale token.
        assertThatThrownBy(() -> tokenService.refresh(second.refreshToken(), "127.0.0.1"))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        assertThatThrownBy(() -> tokenService.refresh("not-a-real-token", "127.0.0.1"))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void logoutRevokesTheSessionSoItCanNoLongerBeRefreshed() {
        UUID financierId = registerFinancier();
        TokenPair pair = tokenService.issueNewSession(financierId, null, null, "web", "127.0.0.1");

        tokenService.logout(pair.refreshToken());

        assertThatThrownBy(() -> tokenService.refresh(pair.refreshToken(), "127.0.0.1"))
                .isInstanceOf(RefreshTokenException.class);
    }

    @Test
    void logoutDoesNotAffectOtherDevicesInADifferentFamily() {
        UUID financierId = registerFinancier();
        TokenPair device1 = tokenService.issueNewSession(financierId, "d1", "Phone", "android", "127.0.0.1");
        TokenPair device2 = tokenService.issueNewSession(financierId, "d2", "Laptop", "web", "127.0.0.1");

        tokenService.logout(device1.refreshToken());

        // device2's session is a different family — untouched by device1's logout.
        TokenPair rotated = tokenService.refresh(device2.refreshToken(), "127.0.0.1");
        assertThat(rotated.refreshToken()).isNotBlank();
    }

    @Test
    void rotationLeavesExactlyOneLiveSessionPerFamily() {
        UUID financierId = registerFinancier();
        TokenPair first = tokenService.issueNewSession(financierId, null, null, "web", "127.0.0.1");
        TokenPair second = tokenService.refresh(first.refreshToken(), "127.0.0.1");
        tokenService.refresh(second.refreshToken(), "127.0.0.1");

        List<com.finbase.entity.Session> family = sessionRepository.findAll().stream()
                .filter(s -> s.getFinancierId().equals(financierId))
                .toList();

        assertThat(family).hasSize(3); // original + 2 rotations
        long live = family.stream().filter(s -> s.getRevokedAt() == null).count();
        assertThat(live).isEqualTo(1);
    }
}
