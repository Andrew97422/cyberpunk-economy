package ru.andrew.bankingservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.andrew.bankingservice.dto.AccountEventEnvelope;
import ru.andrew.bankingservice.service.AccountSnapshotService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventListener {

    private final AccountSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account.events", groupId = "banking-account-sync")
    public void handle(String message) {
        try {
            AccountEventEnvelope envelope = objectMapper.readValue(message, AccountEventEnvelope.class);
            if (envelope.payload() == null) {
                log.warn("Skip account event: empty payload, eventId={}", envelope.eventId());
                return;
            }
            log.info("Received account event type={}, accountId={}",
                    envelope.eventType(), envelope.payload().id());
            snapshotService.upsert(envelope.payload());
        } catch (Exception ex) {
            log.error("Failed to process account event: {}", message, ex);
            throw new RuntimeException("Cannot process account event", ex);
        }
    }
}