package com.shopflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.exception.ApiError;
import com.shopflow.security.InternalApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /internal/** with a shared secret header. MessageDigest.isEqual compares in
 * constant time, so an attacker can't guess the key character by character from timing.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(String apiKey, ObjectMapper objectMapper) {
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(InternalApiProperties.HEADER);
        if (provided == null || !MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(HttpStatus.UNAUTHORIZED,
                    "Missing or invalid internal API key", request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
