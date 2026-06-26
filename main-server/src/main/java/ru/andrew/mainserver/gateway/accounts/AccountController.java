package ru.andrew.mainserver.gateway.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountGatewayService accountGateway;
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public JsonNode me() {
        return accountGateway.getMe(currentUserService.getCurrentUser());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public JsonNode create(@RequestBody JsonNode body) {
        return accountGateway.createAccount(currentUserService.getCurrentUser(), body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode getById(@PathVariable Long id) {
        return accountGateway.getAccount(currentUserService.getCurrentUser(), id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode getAccounts(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String role,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                @RequestParam(defaultValue = "createdAt") String sortBy,
                                @RequestParam(defaultValue = "DESC") String direction) {
        return accountGateway.listAccounts(currentUserService.getCurrentUser(),
                search, role, status, page, size, sortBy, direction);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public JsonNode updateStatus(@PathVariable Long id, @RequestBody JsonNode body) {
        return accountGateway.updateStatus(currentUserService.getCurrentUser(), id, body);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public JsonNode updateRole(@PathVariable Long id, @RequestBody JsonNode body) {
        return accountGateway.updateRole(currentUserService.getCurrentUser(), id, body);
    }
}
