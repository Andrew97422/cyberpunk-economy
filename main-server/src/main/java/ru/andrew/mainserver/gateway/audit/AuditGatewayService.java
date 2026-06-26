package ru.andrew.mainserver.gateway.audit;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditGatewayService {

    private final KafkaCommandGateway auditCommandGateway;

    public JsonNode listLogs(AuthenticatedUser actor, Map<String, Object> filters, int page, int size) {
        Map<String, Object> payload = new HashMap<>(filters);
        payload.put("page", page);
        payload.put("size", size);
        return auditCommandGateway.send(actor, AuditCommandType.LIST_LOGS.name(), null, payload, JsonNode.class);
    }

    public JsonNode getLog(AuthenticatedUser actor, Long logId) {
        return auditCommandGateway.send(actor, AuditCommandType.GET_LOG.name(),
                null, Map.of("logId", logId), JsonNode.class);
    }

    public JsonNode hackLogs(String hackToken, Map<String, Object> filters, int page, int size) {
        Map<String, Object> payload = new HashMap<>(filters);
        payload.put("hackToken", hackToken);
        payload.put("page", page);
        payload.put("size", size);
        return auditCommandGateway.send(null, AuditCommandType.HACK_LOGS.name(), null, payload, JsonNode.class);
    }
}
