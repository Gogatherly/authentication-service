package com.fizu.authentication.exception;

public class GoogleTokenVerificationException extends RuntimeException {
    public GoogleTokenVerificationException(String message) {
        super(message);
    }

    public GoogleTokenVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
