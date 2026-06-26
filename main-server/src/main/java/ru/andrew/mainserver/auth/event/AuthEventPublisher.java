package ru.andrew.mainserver.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes lightweight {@code auth.events} for things only the gateway sees:
 * failed logins, logout attempts, hack-token use. Audit-service ingests these
 * the same way it ingests other domain events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private static final String TOPIC = "auth.events";
    private static final String AGGREGATE_TYPE = "AUTH";
    private static final String SOURCE = "main-server.auth";
    private static final String VERSION = "v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishAdminLoginFailed(String publicName, String terminalName, String reason) {
        publish("admin.login_failed", publicName, null, terminalName, null, reason,
                Map.of("publicName", publicName));
    }

    public void publishPlayerLoginFailed(String terminalName, String reason) {
        publish("player.login_failed", null, null, terminalName, null, reason, Map.of());
    }

    public void publishAdminLoginSuccess(Long accountId, String publicName, String role,
                                         String terminalName, Long sessionId) {
        publish("admin.login_success", publicName, accountId, terminalName, sessionId, "Admin logged in",
                Map.of("publicName", publicName, "role", role));
    }

    public void publishPlayerLoginSuccess(Long accountId, String publicName, String role,
                                          String terminalName, Long sessionId) {
        publish("player.login_success", publicName, accountId, terminalName, sessionId,
                "Player logged in via PIN",
                Map.of("publicName", publicName, "role", role));
    }

    public void publishLogout(Long accountId, String publicName, Long sessionId) {
        publish("session.logout_requested", publicName, accountId, null, sessionId,
                "Logout via gateway",
                Map.of());
    }

    private void publish(String eventType, String publicName, Long accountId, String terminalName,
                         Long sessionId, String message, Map<String, Object> extras) {
        Map<String, Object> payload = new HashMap<>(extras);
        payload.put("accountId", accountId);
        payload.put("publicName", publicName);
        payload.put("terminalName", terminalName);
        payload.put("sessionId", sessionId);
        payload.put("message", message);
        payload.put("occurredAt", Instant.now().toString());

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", AGGREGATE_TYPE);
        envelope.put("aggregateId", sessionId == null
                ? (accountId == null ? publicName : accountId.toString())
                : sessionId.toString());
        envelope.put("source", SOURCE);
        envelope.put("version", VERSION);
        envelope.put("occurredAt", LocalDateTime.now().toString());
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            String key = sessionId == null ? UUID.randomUUID().toString() : sessionId.toString();
            kafkaTemplate.send(TOPIC, key, json);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize auth event {}", eventType, ex);
        }
    }
}
