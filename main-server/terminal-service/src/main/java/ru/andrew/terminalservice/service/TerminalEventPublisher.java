package ru.andrew.terminalservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.andrew.terminalservice.entity.Terminal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalEventPublisher {

    private static final String TOPIC = "terminal.events";
    private static final String AGGREGATE_TYPE = "TERMINAL";
    private static final String SOURCE = "terminal-service";
    private static final String VERSION = "v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishRegistered(Terminal terminal) { publish(terminal, "terminal.registered"); }
    public void publishUpdated(Terminal terminal)    { publish(terminal, "terminal.updated"); }
    public void publishBlocked(Terminal terminal)    { publish(terminal, "terminal.blocked"); }
    public void publishActivated(Terminal terminal)  { publish(terminal, "terminal.activated"); }
    public void publishDecommissioned(Terminal terminal) { publish(terminal, "terminal.decommissioned"); }

    private void publish(Terminal terminal, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("terminalId", terminal.getId());
        payload.put("terminalName", terminal.getName());
        payload.put("ipAddress", terminal.getIpAddress());
        payload.put("location", terminal.getLocation());
        payload.put("terminalType", terminal.getTerminalType());
        payload.put("status", terminal.getStatus().name());

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", AGGREGATE_TYPE);
        envelope.put("aggregateId", terminal.getId().toString());
        envelope.put("source", SOURCE);
        envelope.put("version", VERSION);
        envelope.put("occurredAt", LocalDateTime.now().toString());
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, terminal.getId().toString(), json);
            log.info("Published {} for terminalId={}", eventType, terminal.getId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize terminal event {}", eventType, ex);
            throw new IllegalStateException("Cannot serialize terminal event", ex);
        }
    }
}
