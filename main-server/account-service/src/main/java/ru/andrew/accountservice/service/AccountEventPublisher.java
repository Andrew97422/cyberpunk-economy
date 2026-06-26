package ru.andrew.accountservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.andrew.accountservice.entity.Account;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    private static final String TOPIC = "account.events";
    private static final String AGGREGATE_TYPE = "ACCOUNT";
    private static final String SOURCE = "account-service";
    private static final String VERSION = "v1";
    private static final String EVENT_CREATED = "account.created";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCreated(Account account) {
        publish(account, EVENT_CREATED);
    }

    public void publishUpdated(Account account, String eventType) {
        publish(account, eventType);
    }

    private void publish(Account account, String eventType) {
        Map<String, Object> envelope = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", eventType,
                "aggregateType", AGGREGATE_TYPE,
                "aggregateId", account.getId().toString(),
                "source", SOURCE,
                "version", VERSION,
                "occurredAt", LocalDateTime.now().toString(),
                "payload", Map.of(
                        "id", account.getId(),
                        "publicName", account.getPublicName(),
                        "role", account.getRole().name(),
                        "status", account.getStatus().name()
                )
        );

        try {
            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, account.getId().toString(), message);
            log.info("Published account event {} for accountId={}", eventType, account.getId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize account event {} for accountId={}", eventType, account.getId(), ex);
            throw new IllegalStateException("Cannot serialize account event", ex);
        }
    }
}
