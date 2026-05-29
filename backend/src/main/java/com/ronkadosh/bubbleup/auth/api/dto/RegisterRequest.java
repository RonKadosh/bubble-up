package com.ronkadosh.bubbleup.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank
        @Pattern(regexp = "^[^\\p{Cntrl}\\p{Cc}\\p{Cf}]+$",
                 message = "displayName contains control characters")
        @Size(min = 1, max = 100)
        String displayName
) {}
