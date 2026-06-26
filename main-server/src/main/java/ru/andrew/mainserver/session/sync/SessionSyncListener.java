package ru.andrew.mainserver.session.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionSyncListener {

    private final SessionSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "session.events", groupId = "main-server-session-sync")
    public void onEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                log.warn("Skip session.events: empty payload");
                return;
            }
            Long id = payload.path("sessionId").asLong();
            Long accountId = payload.path("accountId").isMissingNode() ? null : payload.path("accountId").asLong();
            String publicName = payload.path("publicName").asText(null);
            String role = payload.path("role").asText(null);
            String sessionType = payload.path("sessionType").asText(null);
            String status = payload.path("status").asText(null);
            Instant startedAt = parseInstant(payload.path("startedAt").asText(null));
            Instant expiresAt = parseInstant(payload.path("expiresAt").asText(null));
            Instant endedAt = parseInstant(payload.path("endedAt").asText(null));

            snapshotService.upsert(id, accountId, publicName, role, sessionType, status, startedAt, expiresAt, endedAt);
        } catch (Exception ex) {
            log.error("Failed to process session.events: {}", message, ex);
            throw new RuntimeException("Cannot process session.events", ex);
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) return null;
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            log.warn("Cannot parse instant: {}", raw);
            return null;
        }
    }
}
