package ru.andrew.mainserver.gateway.access;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

@RestController
@RequestMapping("/pins")
@RequiredArgsConstructor
public class PinController {

    private final AccessGatewayService accessGateway;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode createPin(@RequestBody JsonNode body) {
        return accessGateway.createPin(currentUserService.getCurrentUser(), body);
    }

    @PostMapping("/{pinId}/revoke")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode revokePin(@PathVariable Long pinId) {
        return accessGateway.revokePin(currentUserService.getCurrentUser(), pinId);
    }

    @GetMapping("/by-account/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode listByAccount(@PathVariable Long accountId) {
        return accessGateway.listPinsByAccount(currentUserService.getCurrentUser(), accountId);
    }
}
