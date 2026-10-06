package com.shopflow.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * The identity extracted from a verified JWT. Controllers receive it via
 * {@code @AuthenticationPrincipal}. Built purely from token claims, so authenticating a
 * request needs no database query (that is what "stateless" auth buys us).
 */
public record AuthenticatedUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Spring's hasRole('X') checks for an authority named "ROLE_X". */
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
