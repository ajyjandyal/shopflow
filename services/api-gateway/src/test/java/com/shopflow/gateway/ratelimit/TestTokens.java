package com.shopflow.gateway.ratelimit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/** Builds tokens shaped exactly like the ones user-service issues (HS256, issuer "shopflow"). */
final class TestTokens {

    static final String ISSUER = "shopflow";
    static final String SECRET = base64("0123456789abcdef0123456789abcdef");      // 32 bytes
    static final String OTHER_SECRET = base64("fedcba9876543210fedcba9876543210"); // 32 bytes

    private TestTokens() {
    }

    static String valid(String subject) {
        return token(SECRET, ISSUER, subject, Instant.now().plus(Duration.ofHours(1)));
    }

    static String token(String secret, String issuer, String subject, Instant expiresAt) {
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(expiresAt.minus(Duration.ofHours(2))))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .compact();
    }

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
