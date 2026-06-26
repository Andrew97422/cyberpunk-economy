package ru.andrew.auditservice.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceCommand(
        String commandType,
        Long actorId,
        String actorRole,
        String idempotencyKey,
        Map<String, Object> payload
) {
}
