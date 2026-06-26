package ru.andrew.analyticsservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.andrew.analyticsservice.service.AnalyticsIngestionService;

/**
 * Fan-in consumer for the economy-relevant event topics. Each topic uses its own
 * group id so analytics offsets stay independent of audit and other consumers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListener {

    private final AnalyticsIngestionService ingestionService;

    @KafkaListener(topics = "banking.events.v1", groupId = "analytics-banking-events")
    public void onBankingEvent(String message) {
        ingestionService.ingestBanking(message);
    }

    @KafkaListener(topics = "marketplace.events", groupId = "analytics-marketplace-events")
    public void onMarketplaceEvent(String message) {
        ingestionService.ingestMarketplace(message);
    }

    @KafkaListener(topics = "account.events", groupId = "analytics-account-events")
    public void onAccountEvent(String message) {
        ingestionService.ingestAccount(message);
    }
}
