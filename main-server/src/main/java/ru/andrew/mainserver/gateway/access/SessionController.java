package ru.andrew.mainserver.gateway.access;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final AccessGatewayService accessGateway;
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public JsonNode getCurrentSession() {
        return accessGateway.getSession(currentUserService.getCurrentUser(),
                currentUserService.getCurrentSessionId());
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode listActive(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(defaultValue = "startedAt") String sortBy,
                               @RequestParam(defaultValue = "DESC") String direction) {
        return accessGateway.listActiveSessions(
                currentUserService.getCurrentUser(), page, size, sortBy, direction);
    }

    @PostMapping("/{sessionId}/terminate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode terminate(@PathVariable Long sessionId) {
        return accessGateway.terminateSession(currentUserService.getCurrentUser(), sessionId);
    }
}
