package ru.andrew.mainserver.gateway.audit;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditGatewayService auditGateway;
    private final CurrentUserService currentUserService;

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode listLogs(@RequestParam(required = false) String eventType,
                             @RequestParam(required = false) String eventSource,
                             @RequestParam(required = false) Long actorAccountId,
                             @RequestParam(required = false) String aggregateType,
                             @RequestParam(required = false) String aggregateId,
                             @RequestParam(required = false) String from,
                             @RequestParam(required = false) String to,
                             @RequestParam(required = false) String search,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "50") int size) {
        Map<String, Object> filters = filterMap(eventType, eventSource, actorAccountId,
                aggregateType, aggregateId, from, to, search);
        return auditGateway.listLogs(currentUserService.getCurrentUser(), filters, page, size);
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode getLog(@PathVariable Long id) {
        return auditGateway.getLog(currentUserService.getCurrentUser(), id);
    }

    /**
     * "Флешка Бога" — anyone who finds the hack token (NFC chip, USB stick,
     * QR code on the playfield) can read raw audit payloads. No JWT required.
     */
    @PostMapping("/hack")
    public JsonNode hack(@RequestHeader("X-Hack-Token") String token,
                         @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> filters = body == null ? Map.of() : new HashMap<>(body);
        int page = filters.get("page") instanceof Number n ? n.intValue() : 0;
        int size = filters.get("size") instanceof Number n ? n.intValue() : 50;
        filters.remove("page");
        filters.remove("size");
        return auditGateway.hackLogs(token, filters, page, size);
    }

    private Map<String, Object> filterMap(String eventType, String eventSource, Long actorAccountId,
                                          String aggregateType, String aggregateId,
                                          String from, String to, String search) {
        Map<String, Object> filters = new HashMap<>();
        if (eventType != null) filters.put("eventType", eventType);
        if (eventSource != null) filters.put("eventSource", eventSource);
        if (actorAccountId != null) filters.put("actorAccountId", actorAccountId);
        if (aggregateType != null) filters.put("aggregateType", aggregateType);
        if (aggregateId != null) filters.put("aggregateId", aggregateId);
        if (from != null) filters.put("from", from);
        if (to != null) filters.put("to", to);
        if (search != null) filters.put("search", search);
        return filters;
    }
}
