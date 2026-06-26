package ru.andrew.mainserver.gateway.access;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;
import ru.andrew.mainserver.session.sync.SessionSnapshotService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccessGatewayService {

    private final KafkaCommandGateway accessCommandGateway;
    private final SessionSnapshotService sessionSnapshotService;

    public JsonNode createPin(AuthenticatedUser actor, JsonNode body) {
        return accessCommandGateway.send(actor, AccessCommandType.CREATE_PIN.name(),
                null, body, JsonNode.class);
    }

    public JsonNode revokePin(AuthenticatedUser actor, Long pinId) {
        return accessCommandGateway.send(actor, AccessCommandType.REVOKE_PIN.name(),
                null, Map.of("pinId", pinId), JsonNode.class);
    }

    public JsonNode listPinsByAccount(AuthenticatedUser actor, Long accountId) {
        return accessCommandGateway.send(actor, AccessCommandType.LIST_PINS_BY_ACCOUNT.name(),
                null, Map.of("accountId", accountId), JsonNode.class);
    }

    public LoginResult loginByPin(String rawPin, String terminalName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pin", rawPin);
        payload.put("terminalName", terminalName);
        LoginResult result = accessCommandGateway.send(null, AccessCommandType.LOGIN_BY_PIN.name(),
                null, payload, LoginResult.class);
        eagerSync(result);
        return result;
    }

    public LoginResult openVerifiedSession(Long accountId, int durationMinutes, String terminalName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", accountId);
        payload.put("durationMinutes", durationMinutes);
        payload.put("terminalName", terminalName);
        LoginResult result = accessCommandGateway.send(null, AccessCommandType.OPEN_VERIFIED_SESSION.name(),
                null, payload, LoginResult.class);
        eagerSync(result);
        return result;
    }

    public LoginResult openServiceSession(String publicName, String rawPin, int durationMinutes,
                                          String terminalName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("publicName", publicName);
        payload.put("pin", rawPin);
        payload.put("durationMinutes", durationMinutes);
        payload.put("terminalName", terminalName);
        LoginResult result = accessCommandGateway.send(null, AccessCommandType.OPEN_SERVICE_SESSION.name(),
                null, payload, LoginResult.class);
        eagerSync(result);
        return result;
    }

    public JsonNode getSession(AuthenticatedUser actor, Long sessionId) {
        return accessCommandGateway.send(actor, AccessCommandType.GET_SESSION.name(),
                null, Map.of("sessionId", sessionId), JsonNode.class);
    }

    public JsonNode listActiveSessions(AuthenticatedUser actor, int page, int size,
                                       String sortBy, String direction) {
        return accessCommandGateway.send(actor, AccessCommandType.LIST_ACTIVE_SESSIONS.name(),
                null, Map.of("page", page, "size", size, "sortBy", sortBy, "direction", direction),
                JsonNode.class);
    }

    public JsonNode terminateSession(AuthenticatedUser actor, Long sessionId) {
        JsonNode result = accessCommandGateway.send(actor, AccessCommandType.TERMINATE_SESSION.name(),
                null, Map.of("sessionId", sessionId), JsonNode.class);
        markEndedLocally(sessionId, "TERMINATED");
        return result;
    }

    public JsonNode logoutSession(AuthenticatedUser actor, Long sessionId) {
        JsonNode result = accessCommandGateway.send(actor, AccessCommandType.LOGOUT_SESSION.name(),
                null, Map.of("sessionId", sessionId), JsonNode.class);
        markEndedLocally(sessionId, "LOGGED_OUT");
        return result;
    }

    private void eagerSync(LoginResult result) {
        if (result == null || result.session() == null || result.session().id() == null) return;
        SessionInfo s = result.session();
        sessionSnapshotService.upsert(
                s.id(),
                s.accountId() == null ? result.accountId() : s.accountId(),
                s.publicName() == null ? result.publicName() : s.publicName(),
                s.role() == null ? result.role() : s.role(),
                s.sessionType(),
                s.status(),
                s.startedAt(),
                s.expiresAt(),
                s.endedAt()
        );
    }

    private void markEndedLocally(Long sessionId, String status) {
        sessionSnapshotService.findById(sessionId).ifPresent(snap ->
                sessionSnapshotService.upsert(
                        snap.getId(), null, null, null, null, status, null, null, Instant.now()));
    }
}
