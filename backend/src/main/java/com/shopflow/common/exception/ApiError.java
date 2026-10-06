package com.shopflow.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * The single error format returned by every endpoint. Clients (and the React frontend
 * later) can rely on this shape instead of parsing different error formats.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, null);
    }

    public static ApiError withFieldErrors(String message, String path, Map<String, String> fieldErrors) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
