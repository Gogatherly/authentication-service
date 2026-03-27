package com.fizu.authentication.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("Send verification code {} to email {}", code, email);
    }
}
