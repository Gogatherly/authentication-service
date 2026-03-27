package com.fizu.authentication.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailRegisterRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a well-formed email address")
        String email,
        @NotBlank(message = "must not be blank")
        @Size(min = 8, message = "size must be between 8 and 2147483647")
        String password,
        @NotBlank(message = "must not be blank")
        String username
) {
}
