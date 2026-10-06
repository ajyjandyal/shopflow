package com.shopflow.security;

/** Shared by all services: every service must understand the roles carried inside a JWT. */
public enum Role {
    USER,
    SELLER,
    ADMIN
}
