package ru.andrew.mainserver.account.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSyncListener {

    private final AccountSyncService syncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account.events", groupId = "main-server-account-sync")
    public void onEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                log.warn("Skip account.events: empty payload, eventId={}", root.path("eventId").asText());
                return;
            }

            Long id = payload.path("id").asLong();
            String publicName = payload.path("publicName").asText(null);
            String role = payload.path("role").asText(null);
            String status = payload.path("status").asText(null);
            String characterName = payload.path("characterName").isMissingNode() ? null
                    : payload.path("characterName").asText(null);
            String notes = payload.path("notes").isMissingNode() ? null
                    : payload.path("notes").asText(null);

            syncService.upsert(id, publicName, role, status, characterName, notes);
        } catch (Exception ex) {
            log.error("Failed to process account.events: {}", message, ex);
            throw new RuntimeException("Cannot process account.events", ex);
        }
    }
}
