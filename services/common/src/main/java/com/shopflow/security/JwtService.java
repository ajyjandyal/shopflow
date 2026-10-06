package com.shopflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues (user-service only) and verifies (every service) HS256-signed JWTs.
 *
 * Because verification only needs the shared secret, product-service and order-service
 * can authenticate requests WITHOUT calling user-service. That keeps them independent:
 * if user-service is down, existing tokens still work everywhere.
 *
 * Trade-off: every service holding the secret could also MINT tokens. Production systems
 * often switch to RS256 (private key only in the auth service, public key everywhere).
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String generateToken(Long userId, String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.issuer())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @throws JwtException             if the token is invalid, tampered with, or expired
     * @throws IllegalArgumentException if the token or its claims are malformed
     */
    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }

    public Duration getExpiration() {
        return properties.expiration();
    }
}
