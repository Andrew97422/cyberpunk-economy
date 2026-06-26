package ru.andrew.mainserver.gateway.cards;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CardGatewayService {

    private final KafkaCommandGateway cardsCommandGateway;

    public JsonNode issueCard(AuthenticatedUser actor, JsonNode body) {
        return cardsCommandGateway.send(actor, CardCommandType.ISSUE_CARD.name(), null, body, JsonNode.class);
    }

    public JsonNode blockCard(AuthenticatedUser actor, Long cardId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cardId", cardId);
        payload.put("reason", reason);
        return cardsCommandGateway.send(actor, CardCommandType.BLOCK_CARD.name(), null, payload, JsonNode.class);
    }

    public JsonNode markLost(AuthenticatedUser actor, Long cardId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cardId", cardId);
        payload.put("reason", reason);
        return cardsCommandGateway.send(actor, CardCommandType.MARK_LOST.name(), null, payload, JsonNode.class);
    }

    public JsonNode replaceCard(AuthenticatedUser actor, Long oldCardId, JsonNode body) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("oldCardId", oldCardId);
        if (body != null && body.isObject()) {
            if (body.hasNonNull("newCardUid")) payload.put("newCardUid", body.get("newCardUid").asText());
            if (body.hasNonNull("notes")) payload.put("notes", body.get("notes").asText());
        }
        return cardsCommandGateway.send(actor, CardCommandType.REPLACE_CARD.name(), null, payload, JsonNode.class);
    }

    public JsonNode listByAccount(AuthenticatedUser actor, Long accountId) {
        return cardsCommandGateway.send(actor, CardCommandType.LIST_CARDS_BY_ACCOUNT.name(),
                null, Map.of("accountId", accountId), JsonNode.class);
    }

    public JsonNode getCard(AuthenticatedUser actor, Long cardId) {
        return cardsCommandGateway.send(actor, CardCommandType.GET_CARD.name(),
                null, Map.of("cardId", cardId), JsonNode.class);
    }

    public JsonNode lookupByUid(AuthenticatedUser actor, String cardUid) {
        return cardsCommandGateway.send(actor, CardCommandType.LOOKUP_BY_UID.name(),
                null, Map.of("cardUid", cardUid), JsonNode.class);
    }

    public JsonNode scanCard(AuthenticatedUser actor, String cardUid, String terminalName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cardUid", cardUid);
        payload.put("terminalName", terminalName);
        return cardsCommandGateway.send(actor, CardCommandType.SCAN_CARD.name(), null, payload, JsonNode.class);
    }
}
