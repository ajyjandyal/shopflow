package com.shopflow.user.dto;

import com.shopflow.security.Role;
import com.shopflow.user.User;

import java.time.Instant;

/** Public view of a user. Note: the password hash is never part of any response. */
public record UserResponse(Long id, String email, String fullName, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getCreatedAt());
    }
}
