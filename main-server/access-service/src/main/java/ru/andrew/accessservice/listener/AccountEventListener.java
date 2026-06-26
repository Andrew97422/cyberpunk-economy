package ru.andrew.accessservice.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.andrew.accessservice.service.AccountSnapshotService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventListener {

    private final AccountSnapshotService accountSnapshotService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account.events", groupId = "access-account-sync")
    public void onEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                log.warn("Skip account.events: empty payload");
                return;
            }
            Long id = payload.path("id").asLong();
            String publicName = payload.path("publicName").asText(null);
            String role = payload.path("role").asText(null);
            String status = payload.path("status").asText(null);
            accountSnapshotService.upsert(id, publicName, role, status);
        } catch (Exception ex) {
            log.error("Failed to process account.events: {}", message, ex);
            throw new RuntimeException("Cannot process account.events", ex);
        }
    }
}
