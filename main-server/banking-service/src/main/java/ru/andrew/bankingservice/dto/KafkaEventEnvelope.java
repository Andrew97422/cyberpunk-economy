package ru.andrew.bankingservice.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record KafkaEventEnvelope(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String source,
        String version,
        LocalDateTime occurredAt,
        Object payload
) {
}