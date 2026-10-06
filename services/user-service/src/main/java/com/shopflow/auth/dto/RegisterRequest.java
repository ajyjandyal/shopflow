package com.shopflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Password max is 72 because BCrypt only uses the first 72 bytes of input;
 * accepting longer passwords would silently ignore the rest.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,

        @NotBlank @Size(max = 100)
        String fullName,

        AccountType accountType
) {
}
