package ru.andrew.auditservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.auditservice.entity.AuditLog;
import ru.andrew.auditservice.repository.AuditLogRepository;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Single chokepoint for turning a Kafka envelope into an audit row.
 * Every other listener calls this — the only thing that varies is the topic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditIngestionService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void ingest(String rawMessage) {
        try {
            JsonNode envelope = objectMapper.readTree(rawMessage);
            String eventId = textOrNull(envelope.path("eventId"));

            if (eventId != null && repository.existsByEventId(eventId)) {
                return; // duplicate delivery
            }

            JsonNode payload = envelope.path("payload");

            AuditLog audit = new AuditLog();
            audit.setEventId(eventId == null ? UUID.randomUUID().toString() : eventId);
            audit.setEventType(textOrNull(envelope.path("eventType")));
            audit.setEventSource(textOrNull(envelope.path("source")));
            audit.setAggregateType(textOrNull(envelope.path("aggregateType")));
            audit.setAggregateId(textOrNull(envelope.path("aggregateId")));
            audit.setOccurredAt(parseInstant(textOrNull(envelope.path("occurredAt"))));

            audit.setActorAccountId(longOrNull(payload.path("actorId")));
            if (audit.getActorAccountId() == null) {
                audit.setActorAccountId(longOrNull(payload.path("accountId")));
            }
            audit.setActorPublicName(textOrNull(payload.path("publicName")));
            audit.setActorRole(textOrNull(payload.path("role")));

            audit.setTargetEntityType(textOrNull(payload.path("targetEntityType")));
            audit.setTargetEntityId(textOrNull(payload.path("targetEntityId")));
            audit.setTerminalId(longOrNull(payload.path("terminalId")));
            audit.setTerminalName(textOrNull(payload.path("terminalName")));
            audit.setMessage(textOrNull(payload.path("message")));
            audit.setPayloadJson(payload.isMissingNode() || payload.isNull() ? null : payload.toString());

            if (audit.getEventType() == null) {
                log.warn("Audit ingest: skipping event with no eventType");
                return;
            }

            if (audit.getOccurredAt() == null) {
                audit.setOccurredAt(Instant.now());
            }

            try {
                repository.save(audit);
            } catch (DataIntegrityViolationException ex) {
                // racing duplicate — landed at almost the same time, fine.
                log.debug("Audit ingest: duplicate eventId {}", audit.getEventId());
            }
        } catch (Exception ex) {
            log.error("Failed to ingest audit message: {}", rawMessage, ex);
            throw new RuntimeException("Cannot ingest audit message", ex);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String value = node.asText();
        return value == null || value.isBlank() || "null".equals(value) ? null : value;
    }

    private Long longOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.asLong();
        try {
            return Long.parseLong(node.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            try {
                return java.time.LocalDateTime.parse(raw)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toInstant();
            } catch (DateTimeParseException ex2) {
                return null;
            }
        }
    }
}
