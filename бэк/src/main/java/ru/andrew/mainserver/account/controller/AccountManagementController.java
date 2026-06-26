package ru.andrew.mainserver.account.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.account.dto.AccountResponse;
import ru.andrew.mainserver.account.dto.CreateAccountRequest;
import ru.andrew.mainserver.account.dto.UpdateAccountRoleRequest;
import ru.andrew.mainserver.account.dto.UpdateAccountStatusRequest;
import ru.andrew.mainserver.account.service.AccountManagementService;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountManagementController {

    private final AccountManagementService accountManagementService;

    @GetMapping("/me")
    public AccountResponse me() {
        return accountManagementService.getMe();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountManagementService.createAccount(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public AccountResponse getById(@PathVariable Long id) {
        return accountManagementService.getAccount(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public Page<AccountResponse> getAccounts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        return accountManagementService.getAccounts(
                search,
                PageRequest.of(page, size, Sort.by(direction, sortBy))
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse updateStatus(@PathVariable Long id,
                                        @Valid @RequestBody UpdateAccountStatusRequest request) {
        return accountManagementService.updateStatus(id, request.getStatus());
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse updateRole(@PathVariable Long id,
                                      @Valid @RequestBody UpdateAccountRoleRequest request) {
        return accountManagementService.updateRole(id, request.getRole());
    }
}