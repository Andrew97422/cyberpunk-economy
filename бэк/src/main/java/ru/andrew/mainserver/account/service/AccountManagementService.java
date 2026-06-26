package ru.andrew.mainserver.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.dto.AccountResponse;
import ru.andrew.mainserver.account.dto.CreateAccountRequest;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.audit.entity.AuditEventType;
import ru.andrew.mainserver.audit.service.AuditService;
import ru.andrew.mainserver.auth.service.CurrentUserService;

@Service
@RequiredArgsConstructor
public class AccountManagementService {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN);

        Account created = accountService.create(request);

        auditService.log(
                AuditEventType.ACCOUNT_CREATED,
                actor.getId(),
                "Account",
                created.getId(),
                null,
                "Account created: " + created.getPublicName(),
                null
        );

        return accountService.toResponse(created);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        return accountService.getResponseById(accountId);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getAccounts(String search, Pageable pageable) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        return accountService.getAccounts(search, pageable);
    }

    @Transactional
    public AccountResponse updateStatus(Long accountId, String status) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN);

        Account updated = accountService.updateStatus(accountId, status);

        auditService.log(
                updated.getStatus().name().equals("BLOCKED")
                        ? AuditEventType.ACCOUNT_BLOCKED
                        : AuditEventType.ACCOUNT_UPDATED,
                actor.getId(),
                "Account",
                updated.getId(),
                null,
                "Account status changed to " + updated.getStatus().name(),
                null
        );

        return accountService.toResponse(updated);
    }

    @Transactional
    public AccountResponse updateRole(Long accountId, String role) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN);

        Account updated = accountService.updateRole(accountId, role);

        auditService.log(
                AuditEventType.ACCOUNT_UPDATED,
                actor.getId(),
                "Account",
                updated.getId(),
                null,
                "Account role changed to " + updated.getRole().name(),
                null
        );

        return accountService.toResponse(updated);
    }

    @Transactional(readOnly = true)
    public AccountResponse getMe() {
        return accountService.getResponseById(currentUserService.getCurrentAccountId());
    }
}