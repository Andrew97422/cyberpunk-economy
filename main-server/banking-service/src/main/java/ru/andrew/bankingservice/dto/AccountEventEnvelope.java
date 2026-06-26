package ru.andrew.bankingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountEventEnvelope(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        AccountEvent payload
) {
}