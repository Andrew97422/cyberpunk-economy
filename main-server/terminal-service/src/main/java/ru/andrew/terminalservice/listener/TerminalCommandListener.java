package ru.andrew.terminalservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.terminalservice.command.ServiceCommand;
import ru.andrew.terminalservice.command.ServiceReply;
import ru.andrew.terminalservice.command.TerminalCommandType;
import ru.andrew.terminalservice.entity.TerminalStatus;
import ru.andrew.terminalservice.exception.BadRequestException;
import ru.andrew.terminalservice.exception.NotFoundException;
import ru.andrew.terminalservice.exception.UnauthorizedException;
import ru.andrew.terminalservice.service.TerminalService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalCommandListener {

    private final TerminalService terminalService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "terminals.commands.v1", groupId = "terminals-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            TerminalCommandType type = TerminalCommandType.valueOf(command.commandType());
            log.info("Received terminals command {} actorId={}", type, command.actorId());
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
            log.error("Failed to process terminals command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize terminals reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, TerminalCommandType type) {
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
        String actorRole = command.actorRole();

        return switch (type) {
            case REGISTER_TERMINAL -> ServiceReply.ok(terminalService.register(
                    actorRole,
                    asString(payload.get("name")),
                    asString(payload.get("ipAddress")),
                    asString(payload.get("location")),
                    asString(payload.get("terminalType")),
                    asString(payload.get("notes"))));
            case UPDATE_TERMINAL -> ServiceReply.ok(terminalService.update(
                    actorRole,
                    asLong(payload.get("terminalId")),
                    asString(payload.get("ipAddress")),
                    asString(payload.get("location")),
                    asString(payload.get("terminalType")),
                    asString(payload.get("notes"))));
            case CHANGE_STATUS -> ServiceReply.ok(terminalService.changeStatus(
                    actorRole,
                    asLong(payload.get("terminalId")),
                    parseStatus(asString(payload.get("status")))));
            case LIST_TERMINALS -> ServiceReply.ok(terminalService.list(
                    actorRole, parseStatusOrNull(asString(payload.get("status"))), pageable(payload)));
            case GET_TERMINAL -> ServiceReply.ok(terminalService.getResponseById(
                    actorRole, asLong(payload.get("terminalId"))));
            case GET_BY_NAME -> ServiceReply.ok(terminalService.getByName(asString(payload.get("name"))));
        };
    }

    private PageRequest pageable(Map<String, Object> payload) {
        int page = payload.get("page") instanceof Number n ? n.intValue() : 0;
        int size = payload.get("size") instanceof Number n ? n.intValue() : 20;
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
    }

    private String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }

    private Long asLong(Object raw) {
        if (raw == null) throw new BadRequestException("Missing numeric field");
        return ((Number) raw).longValue();
    }

    private TerminalStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("status is required");
        }
        try {
            return TerminalStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid status: " + value);
        }
    }

    private TerminalStatus parseStatusOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TerminalStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
