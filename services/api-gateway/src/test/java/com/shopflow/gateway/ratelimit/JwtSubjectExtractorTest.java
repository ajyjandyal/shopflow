package com.shopflow.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSubjectExtractorTest {

    private final JwtSubjectExtractor extractor = new JwtSubjectExtractor(TestTokens.SECRET, TestTokens.ISSUER);

    @Test
    void validTokenYieldsItsSubject() {
        assertThat(extractor.verifiedSubject(TestTokens.valid("42"))).contains("42");
    }

    @Test
    void forgedTokenSignedWithAnotherKeyIsIgnored() {
        String forged = TestTokens.token(TestTokens.OTHER_SECRET, TestTokens.ISSUER, "42",
                Instant.now().plus(Duration.ofHours(1)));

        assertThat(extractor.verifiedSubject(forged)).isEmpty();
    }

    @Test
    void expiredTokenIsIgnored() {
        String expired = TestTokens.token(TestTokens.SECRET, TestTokens.ISSUER, "42",
                Instant.now().minus(Duration.ofMinutes(5)));

        assertThat(extractor.verifiedSubject(expired)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsIgnored() {
        String foreign = TestTokens.token(TestTokens.SECRET, "someone-else", "42",
                Instant.now().plus(Duration.ofHours(1)));

        assertThat(extractor.verifiedSubject(foreign)).isEmpty();
    }

    @Test
    void garbageIsIgnored() {
        assertThat(extractor.verifiedSubject("not-a-jwt")).isEmpty();
        assertThat(extractor.verifiedSubject("")).isEmpty();
    }

    @Test
    void missingSecretFailsAtStartup() {
        assertThatThrownBy(() -> new JwtSubjectExtractor("", TestTokens.ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
