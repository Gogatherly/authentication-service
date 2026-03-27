package com.fizu.authentication.service;

public interface EmailService {
    void sendVerificationCode(String email, String code);
}
