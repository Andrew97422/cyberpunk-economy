package ru.andrew.auditservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditLogFilter(
        String eventType,
        String eventSource,
        Long actorAccountId,
        String aggregateType,
        String aggregateId,
        Instant from,
        Instant to,
        String search
) {
}
