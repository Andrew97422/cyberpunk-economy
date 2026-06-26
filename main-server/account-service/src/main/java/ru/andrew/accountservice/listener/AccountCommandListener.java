package ru.andrew.accountservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.accountservice.command.AccountCommandType;
import ru.andrew.accountservice.command.ServiceCommand;
import ru.andrew.accountservice.command.ServiceReply;
import ru.andrew.accountservice.dto.CreateAccountRequest;
import ru.andrew.accountservice.dto.UpdateAccountRoleRequest;
import ru.andrew.accountservice.dto.UpdateAccountStatusRequest;
import ru.andrew.accountservice.dto.VerifyCredentialsRequest;
import ru.andrew.accountservice.exception.BadRequestException;
import ru.andrew.accountservice.exception.NotFoundException;
import ru.andrew.accountservice.exception.UnauthorizedException;
import ru.andrew.accountservice.service.AccountService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCommandListener {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BANKER = "BANKER";

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "accounts.commands.v1", groupId = "accounts-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            AccountCommandType type = AccountCommandType.valueOf(command.commandType());
            log.info("Received accounts command {} actorId={}", type, command.actorId());
            reply = dispatch(command, type);
        } catch (BadRequestException ex) {
            reply = ServiceReply.error(400, ex.getMessage());
        } catch (UnauthorizedException ex) {
            reply = ServiceReply.error(401, ex.getMessage());
        } catch (NotFoundException ex) {
            reply = ServiceReply.error(404, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            reply = ServiceReply.error(400, "Unknown command: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process accounts command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize accounts reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, AccountCommandType type) {
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
        Long actorId = command.actorId();
        String actorRole = command.actorRole();

        return switch (type) {
            case GET_ME -> {
                requireActor(actorId);
                yield ServiceReply.ok(accountService.getResponseById(actorId));
            }
            case GET_ACCOUNT -> {
                requireAdminOrBanker(actorRole);
                yield ServiceReply.ok(accountService.getResponseById(asLong(payload.get("accountId"))));
            }
            case GET_ACCOUNT_BY_PUBLIC_NAME -> {
                requireAdminOrBanker(actorRole);
                yield ServiceReply.ok(accountService.getByPublicNameResponse(asString(payload.get("publicName"))));
            }
            case GET_ACTIVE_BY_PUBLIC_NAME ->
                    ServiceReply.ok(accountService.getActiveByPublicNameShort(asString(payload.get("publicName"))));
            case LIST_ACCOUNTS -> {
                requireAdminOrBanker(actorRole);
                yield ServiceReply.ok(accountService.getAccounts(
                        asString(payload.get("search")), asString(payload.get("role")),
                        asString(payload.get("status")), pageable(payload)));
            }
            case CREATE_ACCOUNT -> {
                requireAdmin(actorRole);
                yield ServiceReply.ok(accountService.create(
                        objectMapper.convertValue(payload, CreateAccountRequest.class)));
            }
            case UPDATE_STATUS -> {
                requireAdmin(actorRole);
                UpdateAccountStatusRequest req = objectMapper.convertValue(payload, UpdateAccountStatusRequest.class);
                yield ServiceReply.ok(accountService.updateStatus(asLong(payload.get("accountId")), req.getStatus()));
            }
            case UPDATE_ROLE -> {
                requireAdmin(actorRole);
                UpdateAccountRoleRequest req = objectMapper.convertValue(payload, UpdateAccountRoleRequest.class);
                yield ServiceReply.ok(accountService.updateRole(asLong(payload.get("accountId")), req.getRole()));
            }
            case VERIFY_CREDENTIALS -> {
                VerifyCredentialsRequest req = objectMapper.convertValue(payload, VerifyCredentialsRequest.class);
                yield accountService.verifyCredentials(req.getPublicName(), req.getPassword())
                        .map(ServiceReply::ok)
                        .orElseGet(() -> ServiceReply.error(401, "Invalid credentials"));
            }
        };
    }

    private PageRequest pageable(Map<String, Object> payload) {
        int page = payload.get("page") instanceof Number n ? n.intValue() : 0;
        int size = payload.get("size") instanceof Number n ? n.intValue() : 20;
        String sortBy = payload.get("sortBy") instanceof String s && !s.isBlank() ? s : "createdAt";
        Sort.Direction direction = "ASC".equalsIgnoreCase(asString(payload.get("direction")))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Long asLong(Object raw) {
        if (raw == null) throw new BadRequestException("accountId is required");
        return ((Number) raw).longValue();
    }

    private String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }

    private void requireActor(Long actorId) {
        if (actorId == null) throw new UnauthorizedException("Actor is required");
    }

    private void requireAdmin(String role) {
        if (!ROLE_ADMIN.equals(role)) throw new UnauthorizedException("Admin role required");
    }

    private void requireAdminOrBanker(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_BANKER.equals(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }
}
