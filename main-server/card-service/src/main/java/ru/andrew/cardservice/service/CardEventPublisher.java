package ru.andrew.cardservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.andrew.cardservice.entity.CardBinding;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardEventPublisher {

    private static final String TOPIC = "card.events";
    private static final String AGGREGATE_TYPE = "CARD";
    private static final String SOURCE = "card-service";
    private static final String VERSION = "v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishIssued(CardBinding card)   { publish(card, "card.issued",   null); }
    public void publishBlocked(CardBinding card)  { publish(card, "card.blocked",  null); }
    public void publishLost(CardBinding card)     { publish(card, "card.lost",     null); }
    public void publishReplaced(CardBinding card) { publish(card, "card.replaced", null); }

    public void publishScanned(CardBinding card, Long terminalId, String terminalName) {
        Map<String, Object> extras = new HashMap<>();
        extras.put("terminalId", terminalId);
        extras.put("terminalName", terminalName);
        publish(card, "card.scanned", extras);
    }

    private void publish(CardBinding card, String eventType, Map<String, Object> extras) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cardId", card.getId());
        payload.put("cardUid", card.getCardUid());
        payload.put("accountId", card.getAccountId());
        payload.put("status", card.getStatus().name());
        payload.put("issuedAt", card.getIssuedAt() == null ? null : card.getIssuedAt().toString());
        payload.put("blockedAt", card.getBlockedAt() == null ? null : card.getBlockedAt().toString());
        payload.put("blockedReason", card.getBlockedReason());
        payload.put("replacedByCardId", card.getReplacedByCardId());
        if (extras != null) payload.putAll(extras);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", AGGREGATE_TYPE);
        envelope.put("aggregateId", card.getId().toString());
        envelope.put("source", SOURCE);
        envelope.put("version", VERSION);
        envelope.put("occurredAt", LocalDateTime.now().toString());
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, card.getId().toString(), json);
            log.info("Published {} for cardId={}", eventType, card.getId());
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize card event {}", eventType, ex);
            throw new IllegalStateException("Cannot serialize card event", ex);
        }
    }

    public Instant nowOrIssued(CardBinding card) {
        return card.getIssuedAt() == null ? Instant.now() : card.getIssuedAt();
    }
}
