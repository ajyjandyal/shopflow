package com.shopflow.catalog;

import com.shopflow.common.exception.ApiError;
import com.shopflow.common.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Wraps every call to product-service and translates HTTP failures into our own
 * exceptions, so the rest of order-service never deals with HTTP details:
 *   409 from product-service -> ConflictException (definite "no", e.g. out of stock)
 *   timeout / refused / 5xx  -> ProductServiceUnavailableException (outcome UNKNOWN)
 */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);
    private static final ParameterizedTypeReference<List<ProductSnapshot>> SNAPSHOT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public ProductClient(RestClient productRestClient) {
        this.restClient = productRestClient;
    }

    public List<ProductSnapshot> getSnapshots(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        String ids = productIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<ProductSnapshot> result = call("load products", () -> restClient.get()
                .uri(uri -> uri.path("/internal/products").queryParam("ids", ids).build())
                .retrieve()
                .body(SNAPSHOT_LIST));
        return result == null ? List.of() : result;
    }

    public ReservationResult reserve(UUID reservationId, Map<Long, Integer> quantities) {
        List<ReserveStockCommand.Item> items = quantities.entrySet().stream()
                .map(entry -> new ReserveStockCommand.Item(entry.getKey(), entry.getValue()))
                .toList();
        return call("reserve stock", () -> restClient.post()
                .uri("/internal/products/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReserveStockCommand(reservationId, items))
                .retrieve()
                .body(ReservationResult.class));
    }

    public void release(UUID reservationId) {
        call("release stock", () -> restClient.post()
                .uri("/internal/products/reservations/{id}/release", reservationId)
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException.Conflict ex) {
            throw new ConflictException(messageFrom(ex, "The request conflicts with current stock"));
        } catch (HttpClientErrorException ex) {
            // Any other 4xx means order-service sent a bad request: that's our bug, not the user's.
            log.error("product-service rejected '{}' with {}", operation, ex.getStatusCode());
            throw new IllegalStateException("Unexpected " + ex.getStatusCode()
                    + " from product-service during " + operation, ex);
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            log.warn("product-service unavailable during '{}': {}", operation, ex.getMessage());
            throw new ProductServiceUnavailableException(ex);
        }
    }

    private static String messageFrom(HttpClientErrorException ex, String fallback) {
        try {
            ApiError error = ex.getResponseBodyAs(ApiError.class);
            return error != null && error.message() != null ? error.message() : fallback;
        } catch (RuntimeException parseFailure) {
            return fallback;
        }
    }
}
