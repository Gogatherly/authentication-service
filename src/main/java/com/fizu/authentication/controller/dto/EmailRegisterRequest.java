package com.fizu.authentication.controller.dto;

public record EmailRegisterRequest(String email, String password, String username) {
}
