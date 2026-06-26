package ru.andrew.auditservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.andrew.auditservice.service.AuditIngestionService;

/**
 * Fan-in consumer: every event topic from the rest of the system funnels in here.
 * Each topic gets its own group id so we don't accidentally share offsets with
 * the other consumers that already listen to these (e.g. main-server's snapshots).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    private final AuditIngestionService ingestionService;

    @KafkaListener(topics = "account.events", groupId = "audit-account-events")
    public void onAccountEvent(String message) {
        ingestionService.ingest(message);
    }

    @KafkaListener(topics = "session.events", groupId = "audit-session-events")
    public void onSessionEvent(String message) {
        ingestionService.ingest(message);
    }

    @KafkaListener(topics = "banking.events.v1", groupId = "audit-banking-events")
    public void onBankingEvent(String message) {
        ingestionService.ingest(message);
    }

    @KafkaListener(topics = "auth.events", groupId = "audit-auth-events")
    public void onAuthEvent(String message) {
        ingestionService.ingest(message);
    }

    @KafkaListener(topics = "card.events", groupId = "audit-card-events")
    public void onCardEvent(String message) {
        ingestionService.ingest(message);
    }

    @KafkaListener(topics = "terminal.events", groupId = "audit-terminal-events")
    public void onTerminalEvent(String message) {
        ingestionService.ingest(message);
    }
}
