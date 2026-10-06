package com.shopflow.gateway.ratelimit;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Returns the user id ("sub" claim) of a token ONLY if its signature, issuer and expiry are
 * valid.
 *
 * Why verify instead of just decoding the payload? A JWT payload is only Base64, so anyone
 * can write a token claiming "sub": "12345". If the rate limiter trusted unverified tokens,
 * an attacker could send each request with a different fake subject and get a fresh bucket
 * every time, i.e. no rate limit at all.
 *
 * The gateway does NOT authorize anything with this: services still verify the token and
 * check roles themselves.
 */
@Component
public class JwtSubjectExtractor {

    private final JwtParser parser;

    public JwtSubjectExtractor(@Value("${app.jwt.secret:}") String secret,
                               @Value("${app.jwt.issuer:shopflow}") String issuer) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable must be set (the gateway verifies tokens to rate-limit per user)");
        }
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build();
    }

    public Optional<String> verifiedSubject(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            String subject = parser.parseSignedClaims(token).getPayload().getSubject();
            return StringUtils.hasText(subject) ? Optional.of(subject) : Optional.empty();
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid, expired or forged: treat the caller as anonymous (rate-limited by IP).
            // Never log the token itself; it is a credential.
            return Optional.empty();
        }
    }
}
