package com.shopflow.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClientKeyResolverTest {

    private static final InetSocketAddress CLIENT = new InetSocketAddress("203.0.113.7", 50_000);

    private final JwtSubjectExtractor jwt = new JwtSubjectExtractor(TestTokens.SECRET, TestTokens.ISSUER);
    private final ClientKeyResolver apiResolver = ClientKeyResolver.byUserOrIp("api", jwt);
    private final ClientKeyResolver authResolver = ClientKeyResolver.byIp("auth");

    @Test
    void loggedInUserGetsTheirOwnBucket() {
        assertThat(resolve(apiResolver, "Bearer " + TestTokens.valid("42"))).isEqualTo("api:user:42");
    }

    @Test
    void anonymousCallerIsCountedByIp() {
        assertThat(resolve(apiResolver, null)).isEqualTo("api:ip:203.0.113.7");
    }

    @Test
    void forgedTokenDoesNotBuyAFreshBucket() {
        String forged = TestTokens.token(TestTokens.OTHER_SECRET, TestTokens.ISSUER, "999",
                Instant.now().plus(Duration.ofHours(1)));

        assertThat(resolve(apiResolver, "Bearer " + forged)).isEqualTo("api:ip:203.0.113.7");
    }

    @Test
    void xForwardedForHeaderIsNotTrusted() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .header("X-Forwarded-For", "198.51.100.1")
                .remoteAddress(CLIENT)
                .build();

        assertThat(apiResolver.resolve(MockServerWebExchange.from(request)).block()).isEqualTo("api:ip:203.0.113.7");
    }

    @Test
    void authScopeAlwaysUsesIpEvenWithAValidToken() {
        assertThat(resolve(authResolver, "Bearer " + TestTokens.valid("42"))).isEqualTo("auth:ip:203.0.113.7");
    }

    @Test
    void missingRemoteAddressFallsBackToASharedBucket() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();

        assertThat(apiResolver.resolve(MockServerWebExchange.from(request)).block()).isEqualTo("api:ip:unknown");
    }

    private static String resolve(ClientKeyResolver resolver, String authorizationHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/api/v1/orders").remoteAddress(CLIENT);
        if (authorizationHeader != null) {
            builder.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        return resolver.resolve(MockServerWebExchange.from(builder.build())).block();
    }
}
