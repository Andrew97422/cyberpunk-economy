package ru.andrew.mainserver.gateway.news;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsGatewayService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final KafkaCommandGateway newsCommandGateway;
    private final ObjectMapper objectMapper;

    public JsonNode create(AuthenticatedUser actor, JsonNode body) {
        Map<String, Object> payload = toMap(body);
        // Snapshot the author's display name (the worker only receives actorId/role).
        if (actor != null) payload.put("authorPublicName", actor.getPublicName());
        return newsCommandGateway.send(actor, NewsCommandType.CREATE_POST.name(), null, payload, JsonNode.class);
    }

    public JsonNode update(AuthenticatedUser actor, Long newsId, JsonNode body) {
        Map<String, Object> payload = toMap(body);
        payload.put("newsId", newsId);
        return newsCommandGateway.send(actor, NewsCommandType.UPDATE_POST.name(), null, payload, JsonNode.class);
    }

    public JsonNode changeStatus(AuthenticatedUser actor, Long newsId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("newsId", newsId);
        payload.put("status", status);
        return newsCommandGateway.send(actor, NewsCommandType.CHANGE_STATUS.name(), null, payload, JsonNode.class);
    }

    public JsonNode setPinned(AuthenticatedUser actor, Long newsId, boolean pinned) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("newsId", newsId);
        payload.put("pinned", pinned);
        return newsCommandGateway.send(actor, NewsCommandType.SET_PINNED.name(), null, payload, JsonNode.class);
    }

    public JsonNode list(AuthenticatedUser actor, String status, String category, String search, int page, int size) {
        Map<String, Object> payload = new HashMap<>();
        if (status != null) payload.put("status", status);
        if (category != null) payload.put("category", category);
        if (search != null) payload.put("search", search);
        payload.put("page", page);
        payload.put("size", size);
        return newsCommandGateway.send(actor, NewsCommandType.LIST_POSTS.name(), null, payload, JsonNode.class);
    }

    public JsonNode get(AuthenticatedUser actor, Long newsId) {
        return newsCommandGateway.send(actor, NewsCommandType.GET_POST.name(),
                null, Map.of("newsId", newsId), JsonNode.class);
    }

    private Map<String, Object> toMap(JsonNode body) {
        if (body == null || !body.isObject()) return new HashMap<>();
        return objectMapper.convertValue(body, MAP_TYPE);
    }
}
