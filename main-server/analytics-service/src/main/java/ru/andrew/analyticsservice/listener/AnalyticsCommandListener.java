package ru.andrew.analyticsservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.analyticsservice.command.AnalyticsCommandType;
import ru.andrew.analyticsservice.command.ServiceCommand;
import ru.andrew.analyticsservice.command.ServiceReply;
import ru.andrew.analyticsservice.exception.BadRequestException;
import ru.andrew.analyticsservice.exception.UnauthorizedException;
import ru.andrew.analyticsservice.service.AnalyticsQueryService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsCommandListener {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BANKER = "BANKER";

    private final AnalyticsQueryService queryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "analytics.commands.v1", groupId = "analytics-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            AnalyticsCommandType type = AnalyticsCommandType.valueOf(command.commandType());
            log.info("Received analytics command {} actorId={}", type, command.actorId());
            reply = dispatch(command, type);
        } catch (BadRequestException ex) {
            reply = ServiceReply.error(400, ex.getMessage());
        } catch (UnauthorizedException ex) {
            reply = ServiceReply.error(401, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            reply = ServiceReply.error(400, "Unknown command: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process analytics command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize analytics reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, AnalyticsCommandType type) {
        String actorRole = command.actorRole();
        return switch (type) {
            case OVERVIEW -> {
                requireAdminOrBanker(actorRole);
                yield ServiceReply.ok(queryService.overview());
            }
        };
    }

    private void requireAdminOrBanker(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_BANKER.equals(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }
}
