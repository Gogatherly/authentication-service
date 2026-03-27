package com.fizu.authentication.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "must not be blank")
        String idToken
) {
}
