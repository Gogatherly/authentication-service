package com.fizu.authentication.controller.dto;

public record ApiErrorResponse(String status, String message, Object errors) {
    public static ApiErrorResponse of(String message, Object errors) {
        return new ApiErrorResponse("error", message, errors);
    }
}
