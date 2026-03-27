package com.fizu.authentication.controller.dto;

public record VerifyEmailRequest(String email, String code) {
}
