package com.fizu.authentication.producer;

import com.fizu.authentication.dto.VerificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailProducer {

    private final KafkaTemplate<String, VerificationMessage> kafkaTemplate;
    private final String topicName;

    public EmailProducer(
            KafkaTemplate<String, VerificationMessage> kafkaTemplate,
            @Value("${app.kafka.topic.email-verification}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void sendVerificationCode(String email, String code) {
        VerificationMessage message = new VerificationMessage(email, code);

        kafkaTemplate.send(topicName, email, message)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish email verification event for {} to topic {}", email, topicName, error);
                        return;
                    }

                    log.info("Published email verification event for {} to topic {}", email, topicName);
                });
    }
}
