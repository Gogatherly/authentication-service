package com.fizu.authentication.dto;

public record VerificationMessage(
        String email,
        String code
) {
}
