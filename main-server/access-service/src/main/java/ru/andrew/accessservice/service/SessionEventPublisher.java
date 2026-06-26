package ru.andrew.accessservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.andrew.accessservice.entity.GameSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionEventPublisher {

    private static final String TOPIC = "session.events";
    private static final String AGGREGATE_TYPE = "SESSION";
    private static final String SOURCE = "access-service";
    private static final String VERSION = "v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishOpened(GameSession session) {
        publish(session, "session.opened");
    }

    public void publishClosed(GameSession session, String eventType) {
        publish(session, eventType);
    }

    private void publish(GameSession session, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getId());
        payload.put("accountId", session.getAccountId());
        payload.put("role", session.getAccountRole());
        payload.put("publicName", session.getPublicName());
        payload.put("sessionType", session.getSessionType().name());
        payload.put("status", session.getStatus().name());
        payload.put("startedAt", session.getStartedAt() == null ? null : session.getStartedAt().toString());
        payload.put("expiresAt", session.getExpiresAt() == null ? null : session.getExpiresAt().toString());
        payload.put("endedAt", session.getEndedAt() == null ? null : session.getEndedAt().toString());
        payload.put("terminalId", session.getTerminalId());
        payload.put("terminalName", session.getTerminalName());

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", AGGREGATE_TYPE);
        envelope.put("aggregateId", session.getId().toString());
        envelope.put("source", SOURCE);
        envelope.put("version", VERSION);
        envelope.put("occurredAt", LocalDateTime.now().toString());
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, session.getId().toString(), json);
            log.info("Published {} for sessionId={}", eventType, session.getId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize session event {}", eventType, ex);
            throw new IllegalStateException("Cannot serialize session event", ex);
        }
    }
}
