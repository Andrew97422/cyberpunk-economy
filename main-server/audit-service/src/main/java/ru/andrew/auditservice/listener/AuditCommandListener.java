package ru.andrew.auditservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.auditservice.command.AuditCommandType;
import ru.andrew.auditservice.command.ServiceCommand;
import ru.andrew.auditservice.command.ServiceReply;
import ru.andrew.auditservice.dto.AuditLogFilter;
import ru.andrew.auditservice.exception.BadRequestException;
import ru.andrew.auditservice.exception.NotFoundException;
import ru.andrew.auditservice.exception.UnauthorizedException;
import ru.andrew.auditservice.service.AuditQueryService;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditCommandListener {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BANKER = "BANKER";

    private final AuditQueryService auditQueryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "audit.commands.v1", groupId = "audit-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            AuditCommandType type = AuditCommandType.valueOf(command.commandType());
            log.info("Received audit command {} actorId={}", type, command.actorId());
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
            log.error("Failed to process audit command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize audit reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, AuditCommandType type) {
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
        String actorRole = command.actorRole();

        return switch (type) {
            case LIST_LOGS -> {
                requireAdminOrBanker(actorRole);
                yield ServiceReply.ok(auditQueryService.search(
                        buildFilter(payload),
                        intOrDefault(payload.get("page"), 0),
                        intOrDefault(payload.get("size"), 50),
                        false));
            }
            case GET_LOG -> {
                requireAdminOrBanker(actorRole);
                yield ServiceReply.ok(auditQueryService.getById(asLong(payload.get("logId")), false));
            }
            case HACK_LOGS -> {
                String token = asString(payload.get("hackToken"));
                auditQueryService.requireHackerToken(token);
                yield ServiceReply.ok(auditQueryService.search(
                        buildFilter(payload),
                        intOrDefault(payload.get("page"), 0),
                        intOrDefault(payload.get("size"), 50),
                        true));
            }
        };
    }

    private AuditLogFilter buildFilter(Map<String, Object> payload) {
        return new AuditLogFilter(
                asString(payload.get("eventType")),
                asString(payload.get("eventSource")),
                payload.get("actorAccountId") instanceof Number n ? n.longValue() : null,
                asString(payload.get("aggregateType")),
                asString(payload.get("aggregateId")),
                parseInstant(asString(payload.get("from"))),
                parseInstant(asString(payload.get("to"))),
                asString(payload.get("search"))
        );
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }

    private Long asLong(Object raw) {
        if (raw == null) throw new BadRequestException("Missing numeric field");
        return ((Number) raw).longValue();
    }

    private int intOrDefault(Object raw, int defaultValue) {
        if (raw instanceof Number n) return n.intValue();
        return defaultValue;
    }

    private void requireAdminOrBanker(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_BANKER.equals(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }
}
