package ru.andrew.mainserver.gateway.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.account.sync.AccountSyncService;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.core.KafkaCommandGateway;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountGatewayService {

    private final KafkaCommandGateway accountsCommandGateway;
    private final AccountSyncService accountSyncService;

    public JsonNode getMe(AuthenticatedUser actor) {
        return accountsCommandGateway.send(actor, AccountCommandType.GET_ME.name(), null, Map.of(), JsonNode.class);
    }

    public JsonNode getAccount(AuthenticatedUser actor, Long accountId) {
        return accountsCommandGateway.send(actor, AccountCommandType.GET_ACCOUNT.name(),
                null, Map.of("accountId", accountId), JsonNode.class);
    }

    public JsonNode listAccounts(AuthenticatedUser actor, String search, String role, String status,
                                 int page, int size, String sortBy, String direction) {
        return accountsCommandGateway.send(actor, AccountCommandType.LIST_ACCOUNTS.name(), null,
                Map.of(
                        "search", search == null ? "" : search,
                        "role", role == null ? "" : role,
                        "status", status == null ? "" : status,
                        "page", page,
                        "size", size,
                        "sortBy", sortBy,
                        "direction", direction
                ),
                JsonNode.class);
    }

    public JsonNode createAccount(AuthenticatedUser actor, JsonNode body) {
        JsonNode result = accountsCommandGateway.send(actor, AccountCommandType.CREATE_ACCOUNT.name(),
                null, body, JsonNode.class);
        eagerSync(result);
        return result;
    }

    public JsonNode updateStatus(AuthenticatedUser actor, Long accountId, JsonNode body) {
        JsonNode payload = mergeId(body, accountId);
        JsonNode result = accountsCommandGateway.send(actor, AccountCommandType.UPDATE_STATUS.name(),
                null, payload, JsonNode.class);
        eagerSync(result);
        return result;
    }

    public JsonNode updateRole(AuthenticatedUser actor, Long accountId, JsonNode body) {
        JsonNode payload = mergeId(body, accountId);
        JsonNode result = accountsCommandGateway.send(actor, AccountCommandType.UPDATE_ROLE.name(),
                null, payload, JsonNode.class);
        eagerSync(result);
        return result;
    }

    public Optional<AccountInfo> verifyCredentials(String publicName, String password) {
        try {
            AccountInfo info = accountsCommandGateway.send(
                    null,
                    AccountCommandType.VERIFY_CREDENTIALS.name(),
                    null,
                    Map.of("publicName", publicName, "password", password),
                    AccountInfo.class);
            return Optional.ofNullable(info);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 401) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private JsonNode mergeId(JsonNode body, Long accountId) {
        com.fasterxml.jackson.databind.node.ObjectNode obj = body == null || !body.isObject()
                ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                : ((com.fasterxml.jackson.databind.node.ObjectNode) body.deepCopy());
        obj.put("accountId", accountId);
        return obj;
    }

    private void eagerSync(JsonNode result) {
        if (result == null || result.isNull()) return;
        Long id = result.path("id").isMissingNode() ? null : result.path("id").asLong();
        if (id == null || id == 0) return;
        accountSyncService.upsert(
                id,
                result.path("publicName").asText(null),
                result.path("role").asText(null),
                result.path("status").asText(null),
                result.path("characterName").isMissingNode() ? null : result.path("characterName").asText(null),
                result.path("notes").isMissingNode() ? null : result.path("notes").asText(null)
        );
    }
}
