package ru.andrew.mainserver.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.dto.AccountResponse;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.AccountStatus;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.repository.AccountRepository;
import ru.andrew.mainserver.common.exception.NotFoundException;
import ru.andrew.mainserver.common.exception.UnauthorizedException;

/**
 * Read-only facade over the local accounts read-model.
 *
 * All writes (create/update status/role) flow through {@code AccountGatewayService}
 * to the account-service via Kafka commands; that service publishes
 * {@code account.events}, which {@code AccountSyncListener} applies here.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Account getById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public Account getByPublicName(String publicName) {
        return accountRepository.findByPublicName(publicName)
                .orElseThrow(() -> new NotFoundException("Account not found: " + publicName));
    }

    @Transactional(readOnly = true)
    public Account getActiveByPublicName(String publicName) {
        Account account = getByPublicName(publicName);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Account is not active");
        }

        return account;
    }

    @Transactional(readOnly = true)
    public void requireAnyRole(Account account, Role... roles) {
        for (Role role : roles) {
            if (account.getRole() == role) {
                return;
            }
        }
        throw new UnauthorizedException("Role is not allowed");
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getAccounts(String search, Pageable pageable) {
        Page<Account> page = (search == null || search.isBlank())
                ? accountRepository.findAllByOrderByCreatedAtDesc(pageable)
                : accountRepository.findByPublicNameContainingIgnoreCaseOrderByCreatedAtDesc(search.trim(), pageable);

        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AccountResponse getResponseById(Long id) {
        return toResponse(getById(id));
    }

    @Transactional(readOnly = true)
    public AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .publicName(account.getPublicName())
                .characterName(account.getCharacterName())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .notes(account.getNotes())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
