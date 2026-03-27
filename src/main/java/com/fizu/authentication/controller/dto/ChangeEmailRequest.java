package com.fizu.authentication.controller.dto;

public record ChangeEmailRequest(String currentEmail, String newEmail) {
}
