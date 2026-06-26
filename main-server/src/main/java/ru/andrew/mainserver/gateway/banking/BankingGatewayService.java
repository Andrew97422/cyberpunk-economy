package ru.andrew.mainserver.gateway.banking;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.cards.CardGatewayService;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankingGatewayService {

    private final KafkaCommandGateway bankingCommandGateway;
    private final CardGatewayService cardGateway;

    public <T> T send(AuthenticatedUser actor, BankingCommandType type, Object payload, Class<T> resultType) {
        return bankingCommandGateway.send(actor, type.name(), null, payload, resultType);
    }

    public <T> T send(AuthenticatedUser actor, BankingCommandType type, String idempotencyKey,
                      Object payload, Class<T> resultType) {
        return bankingCommandGateway.send(actor, type.name(), idempotencyKey, payload, resultType);
    }

    /**
     * Player-initiated payment: the sender is ALWAYS the current user (server-side, no spoofing),
     * money moves from their own account to the payee. Reuses the TRANSFER command, whose domain
     * logic already permits an account owner to transfer out of their own balance.
     */
    public JsonNode pay(AuthenticatedUser actor, String toPublicName, String currencyType,
                        BigDecimal amount, String comment) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fromPublicName", actor == null ? null : actor.getPublicName());
        payload.put("toPublicName", toPublicName);
        payload.put("currencyType", currencyType);
        payload.put("amount", amount);
        payload.put("comment", comment);
        return bankingCommandGateway.send(actor, BankingCommandType.TRANSFER.name(), null, payload, JsonNode.class);
    }

    /** Resolve a tapped card UID → owner (to show the payee before confirming a payment). */
    public JsonNode resolveCard(AuthenticatedUser actor, String cardUid) {
        return cardGateway.lookupByUid(actor, cardUid);
    }

    // ---- Crypto exchange ----

    public JsonNode cryptoRate(AuthenticatedUser actor) {
        return bankingCommandGateway.send(actor, BankingCommandType.GET_CRYPTO_RATE.name(), null, Map.of(), JsonNode.class);
    }

    public JsonNode cryptoBuy(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.BUY_CRYPTO.name(), null, body, JsonNode.class);
    }

    public JsonNode cryptoSell(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.SELL_CRYPTO.name(), null, body, JsonNode.class);
    }

    public JsonNode cryptoSetMarket(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.SET_CRYPTO_MARKET.name(), null, body, JsonNode.class);
    }

    public JsonNode cryptoShock(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.CRYPTO_SHOCK.name(), null, body, JsonNode.class);
    }

    // ---- Credit (deposits & loans) ----

    public JsonNode creditOverview(AuthenticatedUser actor) {
        return bankingCommandGateway.send(actor, BankingCommandType.GET_CREDIT_OVERVIEW.name(), null, Map.of(), JsonNode.class);
    }

    public JsonNode creditSetPolicy(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.SET_CREDIT_POLICY.name(), null, body, JsonNode.class);
    }

    public JsonNode openDeposit(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.OPEN_DEPOSIT.name(), null, body, JsonNode.class);
    }

    public JsonNode closeDeposit(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.CLOSE_DEPOSIT.name(), null, body, JsonNode.class);
    }

    public JsonNode takeLoan(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.TAKE_LOAN.name(), null, body, JsonNode.class);
    }

    public JsonNode repayLoan(AuthenticatedUser actor, JsonNode body) {
        return bankingCommandGateway.send(actor, BankingCommandType.REPAY_LOAN.name(), null, body, JsonNode.class);
    }
}
