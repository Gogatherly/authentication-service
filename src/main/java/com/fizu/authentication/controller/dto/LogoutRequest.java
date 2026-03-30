package com.fizu.authentication.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "must not be blank")
        String refreshToken
) {
}
