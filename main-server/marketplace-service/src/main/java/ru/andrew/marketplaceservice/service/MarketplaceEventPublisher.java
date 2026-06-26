package ru.andrew.marketplaceservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.andrew.marketplaceservice.entity.MarketOrder;
import ru.andrew.marketplaceservice.entity.Product;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceEventPublisher {

    private static final String TOPIC = "marketplace.events";
    private static final String SOURCE = "marketplace-service";
    private static final String VERSION = "v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishProductCreated(Product product) { publishProduct(product, "product.created"); }
    public void publishProductUpdated(Product product) { publishProduct(product, "product.updated"); }
    public void publishProductStatusChanged(Product product) { publishProduct(product, "product.status_changed"); }

    /** Summary event for a mass price operation (one event per applied op, for audit). */
    public void publishMassPriceApplied(String mode, java.math.BigDecimal value, int affected,
                                        String category, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", mode);
        payload.put("value", value);
        payload.put("affected", affected);
        payload.put("category", category);
        payload.put("status", status);
        publish("PRICE_OP", 0L, "price.mass_applied", payload);
    }

    /** Summary event emitted after a full scenario is applied. */
    public void publishScenarioApplied(Long scenarioId, String name, int steps, int totalAffected) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scenarioId", scenarioId);
        payload.put("name", name);
        payload.put("steps", steps);
        payload.put("totalAffected", totalAffected);
        publish("SCENARIO", scenarioId == null ? 0L : scenarioId, "scenario.applied", payload);
    }

    public void publishOrderCreated(MarketOrder order) { publishOrder(order, "order.created"); }
    public void publishOrderPaid(MarketOrder order) { publishOrder(order, "order.paid"); }
    public void publishOrderCancelled(MarketOrder order) { publishOrder(order, "order.cancelled"); }

    private void publishProduct(Product product, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", product.getId());
        payload.put("sku", product.getSku());
        payload.put("name", product.getName());
        payload.put("price", product.getPrice());
        payload.put("currencyType", product.getCurrencyType().name());
        payload.put("stockQuantity", product.getStockQuantity());
        payload.put("status", product.getStatus().name());
        publish("PRODUCT", product.getId(), eventType, payload);
    }

    private void publishOrder(MarketOrder order, String eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.getId());
        payload.put("productId", order.getProductId());
        payload.put("productName", order.getProductName());
        payload.put("buyerAccountId", order.getBuyerAccountId());
        payload.put("buyerPublicName", order.getBuyerPublicName());
        payload.put("quantity", order.getQuantity());
        payload.put("totalPrice", order.getTotalPrice());
        payload.put("currencyType", order.getCurrencyType().name());
        payload.put("status", order.getStatus().name());
        payload.put("transactionId", order.getTransactionId());
        publish("ORDER", order.getId(), eventType, payload);
    }

    private void publish(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("source", SOURCE);
        envelope.put("version", VERSION);
        envelope.put("occurredAt", LocalDateTime.now().toString());
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(TOPIC, aggregateId.toString(), json);
            log.info("Published {} for {}#{}", eventType, aggregateType, aggregateId);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize marketplace event {}", eventType, ex);
            throw new IllegalStateException("Cannot serialize marketplace event", ex);
        }
    }
}
