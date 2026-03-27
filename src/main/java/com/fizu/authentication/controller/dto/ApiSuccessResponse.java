package com.fizu.authentication.controller.dto;

public record ApiSuccessResponse<T>(String status, String message, T data) {
    public static <T> ApiSuccessResponse<T> of(String message, T data) {
        return new ApiSuccessResponse<>("success", message, data);
    }
}
