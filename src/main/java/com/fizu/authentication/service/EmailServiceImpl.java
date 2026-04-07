package com.fizu.authentication.service;

import com.fizu.authentication.producer.EmailProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final EmailProducer emailProducer;

    public EmailServiceImpl(EmailProducer emailProducer) {
        this.emailProducer = emailProducer;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("Queueing verification code delivery for {}", email);
        emailProducer.sendVerificationCode(email, code);
    }
}
