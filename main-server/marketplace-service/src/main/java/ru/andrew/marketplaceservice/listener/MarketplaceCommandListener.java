package ru.andrew.marketplaceservice.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.marketplaceservice.command.MarketplaceCommandType;
import ru.andrew.marketplaceservice.command.ServiceCommand;
import ru.andrew.marketplaceservice.command.ServiceReply;
import ru.andrew.marketplaceservice.dto.CreateProductRequest;
import ru.andrew.marketplaceservice.dto.PriceStep;
import ru.andrew.marketplaceservice.dto.UpdateProductRequest;
import ru.andrew.marketplaceservice.entity.ProductStatus;
import ru.andrew.marketplaceservice.exception.BadRequestException;
import ru.andrew.marketplaceservice.exception.NotFoundException;
import ru.andrew.marketplaceservice.exception.UnauthorizedException;
import ru.andrew.marketplaceservice.service.OrderService;
import ru.andrew.marketplaceservice.service.PriceScenarioService;
import ru.andrew.marketplaceservice.service.ProductService;
import ru.andrew.marketplaceservice.service.ScenarioScheduleService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceCommandListener {

    private final ProductService productService;
    private final OrderService orderService;
    private final PriceScenarioService priceScenarioService;
    private final ScenarioScheduleService scenarioScheduleService;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<PriceStep>> STEP_LIST = new TypeReference<>() {};

    @KafkaListener(topics = "marketplace.commands.v1", groupId = "marketplace-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            MarketplaceCommandType type = MarketplaceCommandType.valueOf(command.commandType());
            log.info("Received marketplace command {} actorId={}", type, command.actorId());
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
            log.error("Failed to process marketplace command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize marketplace reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, MarketplaceCommandType type) {
        Long actorId = command.actorId();
        String actorRole = command.actorRole();
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();

        return switch (type) {
            case CREATE_PRODUCT -> ServiceReply.ok(productService.create(
                    actorRole, objectMapper.convertValue(payload, CreateProductRequest.class)));
            case UPDATE_PRODUCT -> ServiceReply.ok(productService.update(
                    actorRole, asLong(payload.get("productId")),
                    objectMapper.convertValue(payload, UpdateProductRequest.class)));
            case CHANGE_PRODUCT_STATUS -> ServiceReply.ok(productService.changeStatus(
                    actorRole, asLong(payload.get("productId")),
                    parseProductStatus(asString(payload.get("status")))));
            case ADJUST_STOCK -> ServiceReply.ok(productService.adjustStock(
                    actorRole, asLong(payload.get("productId")), asInt(payload.get("delta"))));
            case LIST_PRODUCTS -> ServiceReply.ok(productService.list(
                    actorRole, asString(payload.get("search")), asString(payload.get("category")),
                    asString(payload.get("status")), pageable(payload, "name", Sort.Direction.ASC)));
            case GET_PRODUCT -> ServiceReply.ok(productService.get(
                    actorRole, asLong(payload.get("productId"))));

            case RESERVE_ORDER -> ServiceReply.ok(orderService.reserve(
                    actorId, asString(payload.get("buyerPublicName")),
                    asLong(payload.get("productId")), asInt(payload.get("quantity"))));
            case CONFIRM_ORDER -> ServiceReply.ok(orderService.confirm(
                    actorId, actorRole, asLong(payload.get("orderId")), asLongOrNull(payload.get("transactionId"))));
            case CANCEL_ORDER -> ServiceReply.ok(orderService.cancel(
                    actorId, actorRole, asLong(payload.get("orderId"))));

            case LIST_ORDERS -> ServiceReply.ok(orderService.listOrders(
                    actorRole, asString(payload.get("status")), pageable(payload, "createdAt", Sort.Direction.DESC)));
            case LIST_MY_ORDERS -> ServiceReply.ok(orderService.listMyOrders(
                    actorId, pageable(payload, "createdAt", Sort.Direction.DESC)));
            case GET_ORDER -> ServiceReply.ok(orderService.getOrder(
                    actorId, actorRole, asLong(payload.get("orderId"))));

            case MASS_PRICE_OP -> ServiceReply.ok(priceScenarioService.applyMassPriceOp(
                    actorRole, objectMapper.convertValue(payload, PriceStep.class)));
            case LIST_SCENARIOS -> ServiceReply.ok(priceScenarioService.listScenarios(actorRole));
            case CREATE_SCENARIO -> ServiceReply.ok(priceScenarioService.createScenario(
                    actorRole, asString(payload.get("name")), asString(payload.get("description")),
                    objectMapper.convertValue(payload.get("steps"), STEP_LIST)));
            case UPDATE_SCENARIO -> ServiceReply.ok(priceScenarioService.updateScenario(
                    actorRole, asLong(payload.get("scenarioId")),
                    asString(payload.get("name")), asString(payload.get("description")),
                    payload.get("steps") == null ? null : objectMapper.convertValue(payload.get("steps"), STEP_LIST)));
            case DELETE_SCENARIO -> {
                priceScenarioService.deleteScenario(actorRole, asLong(payload.get("scenarioId")));
                yield ServiceReply.ok(Map.of("deleted", true));
            }
            case APPLY_SCENARIO -> ServiceReply.ok(priceScenarioService.applyScenario(
                    actorRole, asLong(payload.get("scenarioId"))));

            case SCHEDULE_SCENARIO -> ServiceReply.ok(scenarioScheduleService.createSchedule(
                    actorRole, asLong(payload.get("scenarioId")), asString(payload.get("mode")),
                    parseInstant(payload.get("startAt")), asLongOrNull(payload.get("intervalSeconds")),
                    parseInstant(payload.get("endAt"))));
            case LIST_SCHEDULES -> ServiceReply.ok(scenarioScheduleService.listSchedules(actorRole));
            case CANCEL_SCHEDULE -> {
                scenarioScheduleService.cancelSchedule(actorRole, asLong(payload.get("scheduleId")));
                yield ServiceReply.ok(Map.of("cancelled", true));
            }
            case TRIGGER_SCENARIO_NOW -> ServiceReply.ok(scenarioScheduleService.triggerNow(
                    actorRole, asLong(payload.get("scenarioId"))));
        };
    }

    /** Parses an ISO-8601 instant string (null-safe). Returns null when absent or blank. */
    private Instant parseInstant(Object raw) {
        if (raw == null) return null;
        String text = raw.toString().trim();
        if (text.isEmpty()) return null;
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Invalid timestamp (expected ISO-8601): " + text);
        }
    }

    private PageRequest pageable(Map<String, Object> payload, String sortBy, Sort.Direction direction) {
        int page = payload.get("page") instanceof Number n ? n.intValue() : 0;
        int size = payload.get("size") instanceof Number n ? n.intValue() : 20;
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
        return raw == null ? null : ((Number) raw).longValue();
    }

    private Integer asInt(Object raw) {
        return raw == null ? null : ((Number) raw).intValue();
    }

    private ProductStatus parseProductStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("status is required");
        }
        try {
            return ProductStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid status: " + value);
        }
    }
}
