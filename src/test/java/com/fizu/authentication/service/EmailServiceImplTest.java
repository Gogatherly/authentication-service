package com.fizu.authentication.service;

import com.fizu.authentication.producer.EmailProducer;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailServiceImplTest {

    @Test
    void sendVerificationCodeDelegatesToProducer() {
        EmailProducer emailProducer = mock(EmailProducer.class);
        EmailServiceImpl emailService = new EmailServiceImpl(emailProducer);

        emailService.sendVerificationCode("user@example.com", "123456");

        verify(emailProducer).sendVerificationCode("user@example.com", "123456");
    }
}
