package com.shopflow.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // Test-only keys (32 bytes = 256 bits). Real secrets come from environment variables.
    private static final String SECRET_A = base64("test-secret-a-0123456789abcdefgh");
    private static final String SECRET_B = base64("test-secret-b-0123456789abcdefgh");

    @Test
    void generatedTokenCanBeParsedBack() {
        JwtService jwtService = service(SECRET_A, Duration.ofMinutes(5));

        AuthenticatedUser parsed = jwtService.parseToken(jwtService.generateToken(42L, "seller@example.com", Role.SELLER));

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.email()).isEqualTo("seller@example.com");
        assertThat(parsed.role()).isEqualTo(Role.SELLER);
        assertThat(parsed.authorities()).extracting(Object::toString).containsExactly("ROLE_SELLER");
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        String foreignToken = service(SECRET_B, Duration.ofMinutes(5)).generateToken(1L, "seller@example.com", Role.SELLER);

        assertThatThrownBy(() -> service(SECRET_A, Duration.ofMinutes(5)).parseToken(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService alreadyExpired = service(SECRET_A, Duration.ofSeconds(-60));
        String token = alreadyExpired.generateToken(1L, "seller@example.com", Role.SELLER);

        assertThatThrownBy(() -> alreadyExpired.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void secretShorterThan256BitsFailsFast() {
        String weak = base64("only-16-bytes!!!");

        assertThatThrownBy(() -> service(weak, Duration.ofMinutes(5))).isInstanceOf(WeakKeyException.class);
    }

    private static JwtService service(String secret, Duration expiration) {
        return new JwtService(new JwtProperties(secret, expiration, "shopflow-test"));
    }

    private static String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
