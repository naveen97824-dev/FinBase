package com.finbase.auth;

import com.finbase.entity.Session;
import com.finbase.repository.SessionRepository;
import com.finbase.security.JwtService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues access + refresh token pairs and rotates refresh tokens on use,
 * per the functional spec Part 1.4: 15-minute JWT access token, 30-day
 * refresh token that rotates on every use. Presenting an already-used
 * refresh token (reuse of a rotated-away token) revokes the whole session
 * family and forces re-login — the standard defence against a stolen
 * refresh token being used alongside the legitimate client.
 */
@Service
public class TokenService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final SessionRepository sessionRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(SessionRepository sessionRepository, JwtService jwtService) {
        this.sessionRepository = sessionRepository;
        this.jwtService = jwtService;
    }

    /** Starts a new session family — used at login/registration, never at refresh. */
    public TokenPair issueNewSession(UUID financierId, String deviceId, String deviceName,
            String devicePlatform, String ipAddress) {
        UUID familyId = UUID.randomUUID();
        return issueSessionInFamily(financierId, familyId, deviceId, deviceName, devicePlatform, ipAddress);
    }

    /**
     * Rotates a refresh token: the presented token is looked up, checked for
     * reuse, marked revoked, and a new token is issued in the same family.
     *
     * @throws RefreshTokenException if the token is unknown, expired, or reused
     */
    public TokenPair refresh(String presentedRefreshToken, String ipAddress) {
        String presentedHash = sha256(presentedRefreshToken);
        Session session = sessionRepository.findByRefreshTokenHash(presentedHash)
                .orElseThrow(() -> new RefreshTokenException("Refresh token not recognised."));

        if (session.getRevokedAt() != null) {
            // This exact token was already rotated away (or explicitly revoked)
            // and is being presented again — reuse. Nuke the whole family.
            revokeFamily(session.getFamilyId(), "reuse_detected");
            throw new RefreshTokenException("Refresh token reuse detected. Please log in again.");
        }

        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new RefreshTokenException("Refresh token expired. Please log in again.");
        }

        session.setRevokedAt(Instant.now());
        session.setRevokedReason("rotation");
        session.setLastUsedAt(Instant.now());
        sessionRepository.save(session);

        return issueSessionInFamily(session.getFinancierId(), session.getFamilyId(),
                session.getDeviceId(), session.getDeviceName(), session.getDevicePlatform(), ipAddress);
    }

    /** Revokes a single session (explicit logout) — not the whole family. */
    public void logout(String refreshToken) {
        String hash = sha256(refreshToken);
        sessionRepository.findByRefreshTokenHash(hash).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                session.setRevokedReason("logout");
                sessionRepository.save(session);
            }
        });
    }

    private void revokeFamily(UUID familyId, String reason) {
        List<Session> family = sessionRepository.findByFamilyId(familyId);
        Instant now = Instant.now();
        for (Session s : family) {
            if (s.getRevokedAt() == null) {
                s.setRevokedAt(now);
                s.setRevokedReason(reason);
            }
        }
        sessionRepository.saveAll(family);
    }

    private TokenPair issueSessionInFamily(UUID financierId, UUID familyId, String deviceId,
            String deviceName, String devicePlatform, String ipAddress) {
        String refreshToken = generateOpaqueToken();
        Instant now = Instant.now();

        Session session = new Session();
        session.setFinancierId(financierId);
        session.setFamilyId(familyId);
        session.setRefreshTokenHash(sha256(refreshToken));
        session.setDeviceId(deviceId);
        session.setDeviceName(deviceName);
        session.setDevicePlatform(devicePlatform);
        session.setIpAddress(ipAddress);
        session.setIssuedAt(now);
        session.setExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        sessionRepository.save(session);

        return new TokenPair(jwtService.issueAccessToken(financierId), refreshToken);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
