package ru.andrew.newsservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.newsservice.command.NewsCommandType;
import ru.andrew.newsservice.command.ServiceCommand;
import ru.andrew.newsservice.command.ServiceReply;
import ru.andrew.newsservice.dto.CreateNewsRequest;
import ru.andrew.newsservice.dto.UpdateNewsRequest;
import ru.andrew.newsservice.entity.NewsStatus;
import ru.andrew.newsservice.exception.BadRequestException;
import ru.andrew.newsservice.exception.NotFoundException;
import ru.andrew.newsservice.exception.UnauthorizedException;
import ru.andrew.newsservice.service.NewsService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCommandListener {

    private final NewsService newsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "news.commands.v1", groupId = "news-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            NewsCommandType type = NewsCommandType.valueOf(command.commandType());
            log.info("Received news command {} actorId={}", type, command.actorId());
            reply = dispatch(command, type);
        } catch (BadRequestException ex) {
            reply = ServiceReply.error(400, ex.getMessage());
        } catch (UnauthorizedException ex) {
            reply = ServiceReply.error(403, ex.getMessage());
        } catch (NotFoundException ex) {
            reply = ServiceReply.error(404, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            reply = ServiceReply.error(400, "Unknown command: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process news command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize news reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, NewsCommandType type) {
        Long actorId = command.actorId();
        String actorRole = command.actorRole();
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();

        return switch (type) {
            case CREATE_POST -> ServiceReply.ok(newsService.create(
                    actorId, actorRole, asString(payload.get("authorPublicName")),
                    objectMapper.convertValue(payload, CreateNewsRequest.class)));
            case UPDATE_POST -> ServiceReply.ok(newsService.update(
                    actorRole, asLong(payload.get("newsId")),
                    objectMapper.convertValue(payload, UpdateNewsRequest.class)));
            case CHANGE_STATUS -> ServiceReply.ok(newsService.changeStatus(
                    actorRole, asLong(payload.get("newsId")), parseStatus(asString(payload.get("status")))));
            case SET_PINNED -> ServiceReply.ok(newsService.setPinned(
                    actorRole, asLong(payload.get("newsId")), Boolean.TRUE.equals(payload.get("pinned"))));
            case LIST_POSTS -> ServiceReply.ok(newsService.list(
                    actorRole, asString(payload.get("status")), asString(payload.get("category")),
                    asString(payload.get("search")), pageable(payload)));
            case GET_POST -> ServiceReply.ok(newsService.get(
                    actorRole, asLong(payload.get("newsId"))));
        };
    }

    private PageRequest pageable(Map<String, Object> payload) {
        int page = payload.get("page") instanceof Number n ? n.intValue() : 0;
        int size = payload.get("size") instanceof Number n ? n.intValue() : 20;
        return PageRequest.of(page, size);
    }

    private String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }

    private Long asLong(Object raw) {
        if (raw == null) throw new BadRequestException("newsId is required");
        return ((Number) raw).longValue();
    }

    private NewsStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("status is required");
        }
        try {
            return NewsStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid status: " + value);
        }
    }
}
