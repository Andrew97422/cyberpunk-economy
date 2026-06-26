package ru.andrew.bankingservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.dto.KafkaEventEnvelope;
import ru.andrew.bankingservice.entity.OutboxEventEntity;
import ru.andrew.bankingservice.entity.enums.OutboxStatus;
import ru.andrew.bankingservice.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final String TOPIC = "banking.events.v1";
    private static final String SOURCE = "main-server.banking";
    private static final String VERSION = "v1";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.NEW,
                PageRequest.of(0, 50)
        );

        for (OutboxEventEntity event : events) {
            try {
                JsonNode payloadNode = objectMapper.readTree(event.getPayload());

                KafkaEventEnvelope envelope = KafkaEventEnvelope.builder()
                        .eventId(event.getId().toString())
                        .eventType(event.getEventType())
                        .aggregateType(event.getAggregateType())
                        .aggregateId(event.getAggregateId())
                        .source(SOURCE)
                        .version(VERSION)
                        .occurredAt(event.getCreatedAt())
                        .payload(payloadNode)
                        .build();

                String message = objectMapper.writeValueAsString(envelope);

                kafkaTemplate.send(TOPIC, event.getAggregateId(), message).get();

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(LocalDateTime.now());

                log.info("Published outbox event {} to topic {}", event.getId(), TOPIC);
            } catch (Exception ex) {
                log.error("Failed to publish outbox event {}", event.getId(), ex);
                event.setStatus(OutboxStatus.FAILED);
            }
        }
    }
}