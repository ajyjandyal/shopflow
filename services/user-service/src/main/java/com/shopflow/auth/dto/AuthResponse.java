package com.shopflow.auth.dto;

import com.shopflow.user.dto.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
}
