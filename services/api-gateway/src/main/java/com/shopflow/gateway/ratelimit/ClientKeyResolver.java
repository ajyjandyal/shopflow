package com.shopflow.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides WHO a request is counted against. Each distinct key gets its own token bucket.
 *
 * Keys look like "api:user:42" or "auth:ip:203.0.113.7". The scope prefix ("api"/"auth")
 * keeps the login limiter and the general API limiter in separate buckets even when the
 * client key (e.g. the same IP) is identical.
 *
 * The IP comes from the TCP connection, NOT from the X-Forwarded-For header: that header is
 * written by the client, so trusting it would let anyone dodge the limit by sending a fake
 * value. Behind a real load balancer (Phase 8/11) you'd switch to Spring Cloud Gateway's
 * XForwardedRemoteAddressResolver, configured to trust only your own proxies.
 */
public final class ClientKeyResolver implements KeyResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String scope;
    private final JwtSubjectExtractor jwtSubjectExtractor; // null = always key by IP

    private ClientKeyResolver(String scope, JwtSubjectExtractor jwtSubjectExtractor) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.jwtSubjectExtractor = jwtSubjectExtractor;
    }

    /** For login/register: callers are not authenticated yet, so always count per IP. */
    public static ClientKeyResolver byIp(String scope) {
        return new ClientKeyResolver(scope, null);
    }

    /** Per verified user if a valid JWT is present, otherwise per IP. */
    public static ClientKeyResolver byUserOrIp(String scope, JwtSubjectExtractor jwtSubjectExtractor) {
        return new ClientKeyResolver(scope, Objects.requireNonNull(jwtSubjectExtractor, "jwtSubjectExtractor"));
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> resolveKey(exchange.getRequest()));
    }

    private String resolveKey(ServerHttpRequest request) {
        if (jwtSubjectExtractor != null) {
            Optional<String> userId = bearerToken(request).flatMap(jwtSubjectExtractor::verifiedSubject);
            if (userId.isPresent()) {
                return scope + ":user:" + userId.get();
            }
        }
        return scope + ":ip:" + clientIp(request);
    }

    private static Optional<String> bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }

    private static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown"; // practically never happens with a real TCP connection
        }
        return remote.getAddress().getHostAddress();
    }
}
