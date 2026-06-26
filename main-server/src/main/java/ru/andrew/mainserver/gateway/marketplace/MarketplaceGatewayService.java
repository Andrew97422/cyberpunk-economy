package ru.andrew.mainserver.gateway.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.banking.BankingCommandType;
import ru.andrew.mainserver.gateway.cards.CardGatewayService;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Gateway facade for the marketplace. Catalog operations are simple pass-throughs;
 * a purchase is a 3-step saga orchestrated here across marketplace + banking:
 * <ol>
 *   <li>RESERVE_ORDER — reserve stock, create a PENDING_PAYMENT order</li>
 *   <li>PURCHASE_CHARGE — debit the buyer's own account (variant B)</li>
 *   <li>CONFIRM_ORDER — mark the order PAID</li>
 * </ol>
 * Each step compensates the previous one on failure (cancel/refund).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceGatewayService {

    private final KafkaCommandGateway marketplaceCommandGateway;
    private final KafkaCommandGateway bankingCommandGateway;
    private final CardGatewayService cardGateway;

    // ---- Catalog (management) ----

    public JsonNode createProduct(AuthenticatedUser actor, JsonNode body) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.CREATE_PRODUCT.name(),
                null, body, JsonNode.class);
    }

    public JsonNode updateProduct(AuthenticatedUser actor, Long productId, JsonNode body) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        if (body != null && body.isObject()) {
            if (body.has("name")) payload.put("name", body.get("name").asText(null));
            if (body.has("description")) payload.put("description", body.get("description").asText(null));
            if (body.hasNonNull("price")) payload.put("price", new BigDecimal(body.get("price").asText()));
            if (body.has("category")) payload.put("category", body.get("category").asText(null));
            if (body.has("imageUrl")) payload.put("imageUrl", body.get("imageUrl").asText(null));
        }
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.UPDATE_PRODUCT.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode changeProductStatus(AuthenticatedUser actor, Long productId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("status", status);
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.CHANGE_PRODUCT_STATUS.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode adjustStock(AuthenticatedUser actor, Long productId, Integer delta) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("delta", delta);
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.ADJUST_STOCK.name(),
                null, payload, JsonNode.class);
    }

    // ---- Catalog (browse) ----

    public JsonNode listProducts(AuthenticatedUser actor, String search, String category,
                                 String status, int page, int size) {
        Map<String, Object> payload = new HashMap<>();
        if (search != null) payload.put("search", search);
        if (category != null) payload.put("category", category);
        if (status != null) payload.put("status", status);
        payload.put("page", page);
        payload.put("size", size);
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.LIST_PRODUCTS.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode getProduct(AuthenticatedUser actor, Long productId) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.GET_PRODUCT.name(),
                null, Map.of("productId", productId), JsonNode.class);
    }

    // ---- Orders ----

    public JsonNode listMyOrders(AuthenticatedUser actor, int page, int size) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.LIST_MY_ORDERS.name(),
                null, Map.of("page", page, "size", size), JsonNode.class);
    }

    public JsonNode listOrders(AuthenticatedUser actor, String status, int page, int size) {
        Map<String, Object> payload = new HashMap<>();
        if (status != null) payload.put("status", status);
        payload.put("page", page);
        payload.put("size", size);
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.LIST_ORDERS.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode getOrder(AuthenticatedUser actor, Long orderId) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.GET_ORDER.name(),
                null, Map.of("orderId", orderId), JsonNode.class);
    }

    // ---- Price scenarios (ADMIN/BANKER) ----

    public JsonNode massPriceOp(AuthenticatedUser actor, JsonNode body) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.MASS_PRICE_OP.name(),
                null, body, JsonNode.class);
    }

    public JsonNode listScenarios(AuthenticatedUser actor) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.LIST_SCENARIOS.name(),
                null, Map.of(), JsonNode.class);
    }

    public JsonNode createScenario(AuthenticatedUser actor, JsonNode body) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.CREATE_SCENARIO.name(),
                null, body, JsonNode.class);
    }

    public JsonNode updateScenario(AuthenticatedUser actor, Long scenarioId, JsonNode body) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scenarioId", scenarioId);
        if (body != null && body.isObject()) {
            if (body.has("name")) payload.put("name", body.get("name").asText(null));
            if (body.has("description")) payload.put("description", body.get("description").asText(null));
            if (body.has("steps")) payload.put("steps", body.get("steps"));
        }
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.UPDATE_SCENARIO.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode deleteScenario(AuthenticatedUser actor, Long scenarioId) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.DELETE_SCENARIO.name(),
                null, Map.of("scenarioId", scenarioId), JsonNode.class);
    }

    public JsonNode applyScenario(AuthenticatedUser actor, Long scenarioId) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.APPLY_SCENARIO.name(),
                null, Map.of("scenarioId", scenarioId), JsonNode.class);
    }

    public JsonNode scheduleScenario(AuthenticatedUser actor, Long scenarioId, JsonNode body) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scenarioId", scenarioId);
        if (body != null && body.isObject()) {
            if (body.hasNonNull("mode")) payload.put("mode", body.get("mode").asText());
            if (body.hasNonNull("startAt")) payload.put("startAt", body.get("startAt").asText());
            if (body.hasNonNull("intervalSeconds")) payload.put("intervalSeconds", body.get("intervalSeconds").asLong());
            if (body.hasNonNull("endAt")) payload.put("endAt", body.get("endAt").asText());
        }
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.SCHEDULE_SCENARIO.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode triggerScenarioNow(AuthenticatedUser actor, Long scenarioId) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.TRIGGER_SCENARIO_NOW.name(),
                null, Map.of("scenarioId", scenarioId), JsonNode.class);
    }

    public JsonNode listSchedules(AuthenticatedUser actor) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.LIST_SCHEDULES.name(),
                null, Map.of(), JsonNode.class);
    }

    public JsonNode cancelSchedule(AuthenticatedUser actor, Long scheduleId) {
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.CANCEL_SCHEDULE.name(),
                null, Map.of("scheduleId", scheduleId), JsonNode.class);
    }

    // ---- Purchase saga ----

    /**
     * Card-tap purchase at a terminal. A cashier (operator) rings up a product; the player
     * taps their card; we resolve the card → owner account, reserve the order for THAT player,
     * charge their account, and confirm. Same saga/compensation as {@link #purchase}, but the
     * buyer is the card owner (not the cashier) and the charge is an operator-authorised debit.
     */
    public JsonNode posPurchase(AuthenticatedUser cashier, Long productId, Integer quantity, String cardUid) {
        if (cardUid == null || cardUid.isBlank()) {
            throw new IllegalArgumentException("Не передан код карты");
        }
        JsonNode card;
        try {
            card = cardGateway.lookupByUid(cashier, cardUid.trim());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Карта не найдена");
        }
        if (card == null || !card.hasNonNull("accountId")) {
            throw new IllegalStateException("Карта не привязана к счёту");
        }
        String cardStatus = card.path("cardStatus").asText("");
        if ("BLOCKED".equals(cardStatus) || "LOST".equals(cardStatus) || "REPLACED".equals(cardStatus)) {
            throw new IllegalStateException("Карта недоступна (" + cardStatus + ")");
        }
        if (!"ACTIVE".equals(card.path("accountStatus").asText(""))) {
            throw new IllegalStateException("Счёт владельца карты неактивен");
        }
        Long buyerAccountId = card.get("accountId").asLong();
        String buyerName = card.path("publicName").asText(null);

        // Synthetic buyer actor so the order belongs to the card owner, not the cashier.
        AuthenticatedUser buyer = AuthenticatedUser.builder()
                .accountId(buyerAccountId)
                .publicName(buyerName)
                .role("PLAYER")
                .build();

        Map<String, Object> reservePayload = new HashMap<>();
        reservePayload.put("productId", productId);
        reservePayload.put("quantity", quantity == null ? 1 : quantity);
        reservePayload.put("buyerPublicName", buyerName);
        JsonNode order = marketplaceCommandGateway.send(buyer, MarketplaceCommandType.RESERVE_ORDER.name(),
                null, reservePayload, JsonNode.class);

        Long orderId = order.get("id").asLong();
        String currencyType = order.get("currencyType").asText();
        BigDecimal total = new BigDecimal(order.get("totalPrice").asText());
        String productName = order.path("productName").asText("товар");

        JsonNode charge;
        try {
            Map<String, Object> withdrawPayload = new HashMap<>();
            withdrawPayload.put("publicName", buyerName);
            withdrawPayload.put("currencyType", currencyType);
            withdrawPayload.put("amount", total);
            withdrawPayload.put("comment", "Покупка по карте: " + productName + " (заказ #" + orderId + ")");
            charge = bankingCommandGateway.send(cashier, BankingCommandType.WITHDRAW.name(),
                    "pos-order-" + orderId, withdrawPayload, JsonNode.class);
        } catch (RuntimeException ex) {
            safeCancel(cashier, orderId);
            throw ex;
        }

        Long transactionId = charge.hasNonNull("transactionId") ? charge.get("transactionId").asLong() : null;
        try {
            Map<String, Object> confirmPayload = new HashMap<>();
            confirmPayload.put("orderId", orderId);
            confirmPayload.put("transactionId", transactionId);
            return marketplaceCommandGateway.send(cashier, MarketplaceCommandType.CONFIRM_ORDER.name(),
                    null, confirmPayload, JsonNode.class);
        } catch (RuntimeException ex) {
            safeRefund(cashier, transactionId, orderId);
            safeCancel(cashier, orderId);
            throw ex;
        }
    }

    public JsonNode purchase(AuthenticatedUser actor, Long productId, Integer quantity) {
        Map<String, Object> reservePayload = new HashMap<>();
        reservePayload.put("productId", productId);
        reservePayload.put("quantity", quantity == null ? 1 : quantity);
        reservePayload.put("buyerPublicName", actor == null ? null : actor.getPublicName());

        JsonNode order = marketplaceCommandGateway.send(actor, MarketplaceCommandType.RESERVE_ORDER.name(),
                null, reservePayload, JsonNode.class);

        Long orderId = order.get("id").asLong();
        String currencyType = order.get("currencyType").asText();
        BigDecimal total = new BigDecimal(order.get("totalPrice").asText());

        JsonNode charge;
        try {
            Map<String, Object> chargePayload = new HashMap<>();
            chargePayload.put("currencyType", currencyType);
            chargePayload.put("amount", total);
            chargePayload.put("comment", "Marketplace order #" + orderId);
            charge = bankingCommandGateway.send(actor, BankingCommandType.PURCHASE_CHARGE.name(),
                    "market-order-" + orderId, chargePayload, JsonNode.class);
        } catch (RuntimeException ex) {
            safeCancel(actor, orderId);
            throw ex;
        }

        Long transactionId = charge.hasNonNull("transactionId") ? charge.get("transactionId").asLong() : null;
        try {
            Map<String, Object> confirmPayload = new HashMap<>();
            confirmPayload.put("orderId", orderId);
            confirmPayload.put("transactionId", transactionId);
            return marketplaceCommandGateway.send(actor, MarketplaceCommandType.CONFIRM_ORDER.name(),
                    null, confirmPayload, JsonNode.class);
        } catch (RuntimeException ex) {
            // Payment went through but the order could not be confirmed — refund and cancel.
            safeRefund(actor, transactionId, orderId);
            safeCancel(actor, orderId);
            throw ex;
        }
    }

    /**
     * Manual cancel (ADMIN/BANKER). If the order was already paid, reverse the charge first
     * so the buyer is refunded, then release the reserved stock.
     */
    public JsonNode cancelOrder(AuthenticatedUser actor, Long orderId) {
        JsonNode order = getOrder(actor, orderId);
        String status = order.path("status").asText("");
        Long txId = order.hasNonNull("transactionId") ? order.get("transactionId").asLong() : null;
        if ("PAID".equals(status) && txId != null) {
            Map<String, Object> reversePayload = new HashMap<>();
            reversePayload.put("transactionId", txId);
            reversePayload.put("comment", "Refund for cancelled marketplace order #" + orderId);
            bankingCommandGateway.send(actor, BankingCommandType.REVERSE.name(),
                    "market-refund-" + orderId, reversePayload, JsonNode.class);
        }
        return marketplaceCommandGateway.send(actor, MarketplaceCommandType.CANCEL_ORDER.name(),
                null, Map.of("orderId", orderId), JsonNode.class);
    }

    // ---- Compensation helpers (best-effort) ----

    private void safeCancel(AuthenticatedUser actor, Long orderId) {
        try {
            marketplaceCommandGateway.send(actor, MarketplaceCommandType.CANCEL_ORDER.name(),
                    null, Map.of("orderId", orderId), JsonNode.class);
        } catch (RuntimeException ex) {
            log.error("Failed to cancel order {} during saga compensation", orderId, ex);
        }
    }

    private void safeRefund(AuthenticatedUser actor, Long transactionId, Long orderId) {
        if (transactionId == null) return;
        try {
            // Reversal requires an operator role; the buyer cannot reverse their own charge,
            // so this internal compensation runs under an elevated system actor.
            AuthenticatedUser system = AuthenticatedUser.builder()
                    .accountId(actor == null ? null : actor.getAccountId())
                    .publicName(actor == null ? null : actor.getPublicName())
                    .role("ADMIN")
                    .build();
            Map<String, Object> reversePayload = new HashMap<>();
            reversePayload.put("transactionId", transactionId);
            reversePayload.put("comment", "Auto-refund: order #" + orderId + " confirmation failed");
            bankingCommandGateway.send(system, BankingCommandType.REVERSE.name(),
                    "market-refund-" + orderId, reversePayload, JsonNode.class);
        } catch (RuntimeException ex) {
            log.error("Failed to refund tx {} for order {} during saga compensation", transactionId, orderId, ex);
        }
    }
}
