package ru.andrew.mainserver.gateway.terminals;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TerminalGatewayService {

    private final KafkaCommandGateway terminalsCommandGateway;

    public JsonNode register(AuthenticatedUser actor, JsonNode body) {
        return terminalsCommandGateway.send(actor, TerminalCommandType.REGISTER_TERMINAL.name(),
                null, body, JsonNode.class);
    }

    public JsonNode update(AuthenticatedUser actor, Long terminalId, JsonNode body) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("terminalId", terminalId);
        if (body != null && body.isObject()) {
            if (body.hasNonNull("ipAddress")) payload.put("ipAddress", body.get("ipAddress").asText());
            if (body.hasNonNull("location")) payload.put("location", body.get("location").asText());
            if (body.hasNonNull("terminalType")) payload.put("terminalType", body.get("terminalType").asText());
            if (body.hasNonNull("notes")) payload.put("notes", body.get("notes").asText());
        }
        return terminalsCommandGateway.send(actor, TerminalCommandType.UPDATE_TERMINAL.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode changeStatus(AuthenticatedUser actor, Long terminalId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("terminalId", terminalId);
        payload.put("status", status);
        return terminalsCommandGateway.send(actor, TerminalCommandType.CHANGE_STATUS.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode list(AuthenticatedUser actor, String statusFilter, int page, int size) {
        Map<String, Object> payload = new HashMap<>();
        if (statusFilter != null) payload.put("status", statusFilter);
        payload.put("page", page);
        payload.put("size", size);
        return terminalsCommandGateway.send(actor, TerminalCommandType.LIST_TERMINALS.name(),
                null, payload, JsonNode.class);
    }

    public JsonNode getById(AuthenticatedUser actor, Long terminalId) {
        return terminalsCommandGateway.send(actor, TerminalCommandType.GET_TERMINAL.name(),
                null, Map.of("terminalId", terminalId), JsonNode.class);
    }

    public JsonNode getByName(AuthenticatedUser actor, String name) {
        return terminalsCommandGateway.send(actor, TerminalCommandType.GET_BY_NAME.name(),
                null, Map.of("name", name), JsonNode.class);
    }
}
