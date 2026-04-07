package com.fizu.authentication.producer;

import com.fizu.authentication.dto.VerificationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailProducerTest {

    @Test
    void sendVerificationCodePublishesKafkaMessage() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, VerificationMessage> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(
                eq("email-verification"),
                eq("user@example.com"),
                eq(new VerificationMessage("user@example.com", "123456"))
        )).thenReturn(CompletableFuture.completedFuture(null));

        EmailProducer emailProducer = new EmailProducer(kafkaTemplate, "email-verification");

        emailProducer.sendVerificationCode("user@example.com", "123456");

        verify(kafkaTemplate).send(
                "email-verification",
                "user@example.com",
                new VerificationMessage("user@example.com", "123456")
        );
    }
}
