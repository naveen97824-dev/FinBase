package com.finbase.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies access tokens carrying the {@code financier_id}
 * claim. Refresh-token issuing/rotation is a separate concern (opaque
 * random tokens, not JWTs) — see {@link com.finbase.auth.TokenService}.
 */
@Component
public final class JwtService {

    private static final String FINANCIER_ID_CLAIM = "financier_id";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final SecretKey signingKey;

    public JwtService(@Value("${finbase.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Issues a 15-minute access token carrying {@code financier_id}. */
    public String issueAccessToken(UUID financierId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim(FINANCIER_ID_CLAIM, financierId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies the token's signature and expiry, then returns its
     * {@code financier_id} claim.
     *
     * @throws JwtException if the token is missing, malformed, expired, has
     *     a bad signature, or has no {@code financier_id} claim
     */
    public UUID verifyAndExtractFinancierId(String token) {
        var claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String financierId = claims.get(FINANCIER_ID_CLAIM, String.class);
        if (financierId == null) {
            throw new JwtException("Token has no financier_id claim");
        }

        try {
            return UUID.fromString(financierId);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Token financier_id claim is not a valid UUID: " + financierId, e);
        }
    }
}
