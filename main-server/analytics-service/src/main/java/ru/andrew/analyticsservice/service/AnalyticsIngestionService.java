package ru.andrew.analyticsservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.analyticsservice.entity.AccountFact;
import ru.andrew.analyticsservice.entity.MoneyFlow;
import ru.andrew.analyticsservice.entity.OrderFact;
import ru.andrew.analyticsservice.repository.AccountFactRepository;
import ru.andrew.analyticsservice.repository.MoneyFlowRepository;
import ru.andrew.analyticsservice.repository.OrderFactRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Turns Kafka event envelopes into flat analytics fact rows.
 * Each fact table dedups on the envelope's eventId so re-delivery is safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsIngestionService {

    private final MoneyFlowRepository moneyFlowRepository;
    private final OrderFactRepository orderFactRepository;
    private final AccountFactRepository accountFactRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void ingestBanking(String raw) {
        JsonNode env = read(raw);
        if (env == null) return;
        String eventId = text(env.path("eventId"));
        if (eventId == null || moneyFlowRepository.existsByEventId(eventId)) return;
        JsonNode p = env.path("payload");

        MoneyFlow mf = new MoneyFlow();
        mf.setEventId(eventId);
        mf.setEventType(text(env.path("eventType")));
        mf.setOccurredAt(parseInstant(text(env.path("occurredAt"))));
        mf.setOperation(text(p.path("operation")));
        mf.setAccountId(asLong(p.path("accountId")));
        mf.setPublicName(text(p.path("publicName")));
        mf.setCurrencyType(text(p.path("currencyType")));
        mf.setAmount(asDecimal(p.path("amount")));
        mf.setBalanceAfter(asDecimal(p.path("balanceAfter")));
        mf.setRelatedAccountId(asLong(p.path("relatedAccountId")));
        mf.setActorId(asLong(p.path("actorId")));
        if (mf.getAccountId() == null || mf.getCurrencyType() == null) return;
        moneyFlowRepository.save(mf);
    }

    @Transactional
    public void ingestMarketplace(String raw) {
        JsonNode env = read(raw);
        if (env == null) return;
        String eventType = text(env.path("eventType"));
        if (eventType == null || !eventType.startsWith("order.")) return; // only orders matter for economy
        String eventId = text(env.path("eventId"));
        if (eventId == null || orderFactRepository.existsByEventId(eventId)) return;
        JsonNode p = env.path("payload");

        OrderFact of = new OrderFact();
        of.setEventId(eventId);
        of.setEventType(eventType);
        of.setOccurredAt(parseInstant(text(env.path("occurredAt"))));
        of.setOrderId(asLong(p.path("orderId")));
        of.setProductId(asLong(p.path("productId")));
        of.setProductName(text(p.path("productName")));
        of.setBuyerAccountId(asLong(p.path("buyerAccountId")));
        of.setBuyerPublicName(text(p.path("buyerPublicName")));
        of.setQuantity(asInt(p.path("quantity")));
        of.setTotalPrice(asDecimal(p.path("totalPrice")));
        of.setCurrencyType(text(p.path("currencyType")));
        of.setStatus(text(p.path("status")));
        orderFactRepository.save(of);
    }

    @Transactional
    public void ingestAccount(String raw) {
        JsonNode env = read(raw);
        if (env == null) return;
        String eventId = text(env.path("eventId"));
        if (eventId == null || accountFactRepository.existsByEventId(eventId)) return;
        JsonNode p = env.path("payload");

        AccountFact af = new AccountFact();
        af.setEventId(eventId);
        af.setEventType(text(env.path("eventType")));
        af.setOccurredAt(parseInstant(text(env.path("occurredAt"))));
        af.setAccountId(asLong(p.path("id")));
        af.setPublicName(text(p.path("publicName")));
        af.setRole(text(p.path("role")));
        af.setStatus(text(p.path("status")));
        if (af.getAccountId() == null) return;
        accountFactRepository.save(af);
    }

    private JsonNode read(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            log.warn("Analytics: cannot parse event: {}", ex.getMessage());
            return null;
        }
    }

    private String text(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private Long asLong(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asLong();
    }

    private Integer asInt(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asInt();
    }

    private BigDecimal asDecimal(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        try {
            return new BigDecimal(n.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            // publishers emit LocalDateTime.now().toString() (no zone)
        }
        try {
            return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC);
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}
