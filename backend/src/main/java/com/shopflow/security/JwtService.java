package com.shopflow.security;

import com.shopflow.user.Role;
import com.shopflow.user.User;
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
 * Issues and verifies HS256-signed JWTs.
 * Token layout: sub = user id, plus "email" and "role" claims, iss, iat, exp.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // Throws WeakKeyException at startup if the decoded secret is shorter than 256 bits.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(properties.issuer())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature, expiry and issuer, then maps claims to an AuthenticatedUser.
     *
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

        Long userId = Long.valueOf(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
        return new AuthenticatedUser(userId, email, role);
    }

    public Duration getExpiration() {
        return properties.expiration();
    }
}
