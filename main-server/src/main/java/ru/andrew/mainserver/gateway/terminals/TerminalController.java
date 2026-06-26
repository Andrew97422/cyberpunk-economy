package ru.andrew.mainserver.gateway.terminals;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

import java.util.Map;

@RestController
@RequestMapping("/terminals")
@RequiredArgsConstructor
public class TerminalController {

    private final TerminalGatewayService terminalGateway;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public JsonNode register(@RequestBody JsonNode body) {
        return terminalGateway.register(currentUserService.getCurrentUser(), body);
    }

    @PatchMapping("/{terminalId}")
    @PreAuthorize("hasRole('ADMIN')")
    public JsonNode update(@PathVariable Long terminalId, @RequestBody JsonNode body) {
        return terminalGateway.update(currentUserService.getCurrentUser(), terminalId, body);
    }

    @PostMapping("/{terminalId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public JsonNode changeStatus(@PathVariable Long terminalId, @RequestBody Map<String, String> body) {
        String status = body == null ? null : body.get("status");
        return terminalGateway.changeStatus(currentUserService.getCurrentUser(), terminalId, status);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode list(@RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return terminalGateway.list(currentUserService.getCurrentUser(), status, page, size);
    }

    @GetMapping("/{terminalId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode getOne(@PathVariable Long terminalId) {
        return terminalGateway.getById(currentUserService.getCurrentUser(), terminalId);
    }

    @GetMapping("/by-name/{name}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode getByName(@PathVariable String name) {
        return terminalGateway.getByName(currentUserService.getCurrentUser(), name);
    }
}
