package com.shopflow.gateway.ratelimit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gives 429 responses the same JSON error shape every other ShopFlow error uses.
 *
 * The built-in RequestRateLimiter filter rejects a request by setting status 429 and
 * completing the response with an EMPTY body. This global filter runs first and wraps the
 * response: if it is completed with 429 and no body, we write the standard JSON instead.
 * Responses proxied from services are written with writeWith(), not setComplete(), so they
 * pass through untouched.
 */
@Component
public class RateLimitErrorResponseFilter implements GlobalFilter, Ordered {

    static final String MESSAGE = "Too many requests. Please slow down and try again shortly.";

    private final ObjectMapper objectMapper;

    public RateLimitErrorResponseFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        ServerHttpResponse decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> setComplete() {
                HttpStatusCode status = getStatusCode();
                if (status != null && status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    byte[] body = errorBody(path);
                    getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    getHeaders().setContentLength(body.length);
                    DataBuffer buffer = bufferFactory().wrap(body);
                    return writeWith(Mono.just(buffer));
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private byte[] errorBody(String path) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        error.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        error.put("message", MESSAGE);
        error.put("path", path);
        try {
            return objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize rate-limit error body", ex);
        }
    }

    @Override
    public int getOrder() {
        // Before every other filter, so the wrapped response is the one the rate limiter sees.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
