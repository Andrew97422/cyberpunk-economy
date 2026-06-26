package ru.andrew.accessservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.accessservice.command.AccessCommandType;
import ru.andrew.accessservice.command.ServiceCommand;
import ru.andrew.accessservice.command.ServiceReply;
import ru.andrew.accessservice.exception.BadRequestException;
import ru.andrew.accessservice.exception.NotFoundException;
import ru.andrew.accessservice.exception.UnauthorizedException;
import ru.andrew.accessservice.service.AccessService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessCommandListener {

    private final AccessService accessService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "access.commands.v1", groupId = "access-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            AccessCommandType type = AccessCommandType.valueOf(command.commandType());
            log.info("Received access command {} actorId={}", type, command.actorId());
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
            log.error("Failed to process access command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize access reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, AccessCommandType type) {
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
        Long actorId = command.actorId();
        String actorRole = command.actorRole();

        return switch (type) {
            case CREATE_PIN -> ServiceReply.ok(accessService.createPin(
                    actorRole,
                    actorId,
                    asString(payload.get("publicName")),
                    asString(payload.get("rawPin")),
                    asInt(payload.get("durationMinutes")),
                    asString(payload.get("comment"))));
            case REVOKE_PIN -> ServiceReply.ok(accessService.revokePin(actorRole, asLong(payload.get("pinId"))));
            case LIST_PINS_BY_ACCOUNT -> ServiceReply.ok(
                    accessService.listPinsByAccount(actorRole, asLong(payload.get("accountId"))));
            case LOGIN_BY_PIN -> ServiceReply.ok(accessService.loginByPin(
                    asString(payload.get("pin")),
                    asLongOrNull(payload.get("terminalId")),
                    asString(payload.get("terminalName"))));
            case OPEN_SERVICE_SESSION -> ServiceReply.ok(accessService.openServiceSession(
                    asString(payload.get("publicName")),
                    asString(payload.get("pin")),
                    asInt(payload.get("durationMinutes")),
                    asLongOrNull(payload.get("terminalId")),
                    asString(payload.get("terminalName"))));
            case OPEN_VERIFIED_SESSION -> ServiceReply.ok(accessService.openVerifiedSession(
                    asLong(payload.get("accountId")),
                    asInt(payload.get("durationMinutes")),
                    asLongOrNull(payload.get("terminalId")),
                    asString(payload.get("terminalName"))));
            case GET_SESSION -> ServiceReply.ok(accessService.getSession(asLong(payload.get("sessionId"))));
            case LIST_ACTIVE_SESSIONS -> ServiceReply.ok(accessService.listActiveSessions(actorRole, pageable(payload)));
            case TERMINATE_SESSION -> ServiceReply.ok(
                    accessService.terminateSession(actorRole, asLong(payload.get("sessionId"))));
            case LOGOUT_SESSION -> ServiceReply.ok(accessService.logoutSession(asLong(payload.get("sessionId"))));
        };
    }

    private PageRequest pageable(Map<String, Object> payload) {
        int page = payload.get("page") instanceof Number n ? n.intValue() : 0;
        int size = payload.get("size") instanceof Number n ? n.intValue() : 20;
        String sortBy = payload.get("sortBy") instanceof String s && !s.isBlank() ? s : "startedAt";
        Sort.Direction direction = "ASC".equalsIgnoreCase(asString(payload.get("direction")))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }

    private Long asLong(Object raw) {
        if (raw == null) throw new BadRequestException("Missing numeric field");
        return ((Number) raw).longValue();
    }

    private Long asLongOrNull(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.longValue();
        return null;
    }

    private Integer asInt(Object raw) {
        if (raw == null) throw new BadRequestException("Missing duration field");
        return ((Number) raw).intValue();
    }
}
