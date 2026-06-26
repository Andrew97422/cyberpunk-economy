package ru.andrew.cardservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.cardservice.command.CardCommandType;
import ru.andrew.cardservice.command.ServiceCommand;
import ru.andrew.cardservice.command.ServiceReply;
import ru.andrew.cardservice.exception.BadRequestException;
import ru.andrew.cardservice.exception.NotFoundException;
import ru.andrew.cardservice.exception.UnauthorizedException;
import ru.andrew.cardservice.service.CardService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardCommandListener {

    private final CardService cardService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "cards.commands.v1", groupId = "cards-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            CardCommandType type = CardCommandType.valueOf(command.commandType());
            log.info("Received cards command {} actorId={}", type, command.actorId());
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
            log.error("Failed to process cards command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize cards reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, CardCommandType type) {
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
        Long actorId = command.actorId();
        String actorRole = command.actorRole();

        return switch (type) {
            case ISSUE_CARD -> ServiceReply.ok(cardService.issueCard(
                    actorRole, actorId,
                    asString(payload.get("publicName")),
                    asString(payload.get("cardUid")),
                    asString(payload.get("notes"))));
            case BLOCK_CARD -> ServiceReply.ok(cardService.blockCard(
                    actorRole, actorId,
                    asLong(payload.get("cardId")),
                    asString(payload.get("reason"))));
            case MARK_LOST -> ServiceReply.ok(cardService.markLost(
                    actorRole, actorId,
                    asLong(payload.get("cardId")),
                    asString(payload.get("reason"))));
            case REPLACE_CARD -> ServiceReply.ok(cardService.replaceCard(
                    actorRole, actorId,
                    asLong(payload.get("oldCardId")),
                    asString(payload.get("newCardUid")),
                    asString(payload.get("notes"))));
            case LIST_CARDS_BY_ACCOUNT -> ServiceReply.ok(cardService.listByAccount(
                    actorRole, asLong(payload.get("accountId"))));
            case GET_CARD -> ServiceReply.ok(cardService.getCard(
                    actorRole, asLong(payload.get("cardId"))));
            case LOOKUP_BY_UID -> ServiceReply.ok(cardService.lookupByUid(
                    asString(payload.get("cardUid"))));
            case SCAN_CARD -> ServiceReply.ok(cardService.scanCard(
                    asString(payload.get("cardUid")),
                    asLongOrNull(payload.get("terminalId")),
                    asString(payload.get("terminalName"))));
        };
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
}
