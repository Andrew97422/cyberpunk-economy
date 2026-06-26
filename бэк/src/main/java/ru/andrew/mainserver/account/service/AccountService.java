package ru.andrew.mainserver.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.dto.AccountResponse;
import ru.andrew.mainserver.account.dto.CreateAccountRequest;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.AccountStatus;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.repository.AccountRepository;
import ru.andrew.mainserver.banking.entity.Balance;
import ru.andrew.mainserver.banking.repository.BalanceRepository;
import ru.andrew.mainserver.common.exception.BadRequestException;
import ru.andrew.mainserver.common.exception.NotFoundException;
import ru.andrew.mainserver.common.exception.UnauthorizedException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final PasswordEncoder passwordEncoder;

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

    @Transactional
    public Account create(CreateAccountRequest request) {
        if (accountRepository.existsByPublicNameIgnoreCase(request.getPublicName())) {
            throw new BadRequestException("Account with this publicName already exists");
        }

        Role role = parseRole(request.getRole());
        AccountStatus status = parseStatus(request.getStatus());

        if ((role == Role.ADMIN || role == Role.BANKER || role == Role.DEVELOPER)
                && (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new BadRequestException("Password is required for service accounts");
        }

        Account account = new Account();
        account.setPublicName(request.getPublicName().trim());
        account.setCharacterName(request.getCharacterName());
        account.setRole(role);
        account.setStatus(status);
        account.setNotes(request.getNotes());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        Account saved = accountRepository.save(account);

        Balance balance = new Balance();
        balance.setAccount(saved);
        balance.setCashlessAmount(BigDecimal.ZERO);
        balance.setCryptoAmount(BigDecimal.ZERO);
        balanceRepository.save(balance);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getAccounts(String search, Pageable pageable) {
        Page<Account> page = (search == null || search.isBlank())
                ? accountRepository.findAllByOrderByCreatedAtDesc(pageable)
                : accountRepository.findByPublicNameContainingIgnoreCaseOrderByCreatedAtDesc(search.trim(), pageable);

        return page.map(this::toResponse);
    }

    @Transactional
    public Account updateStatus(Long accountId, String status) {
        Account account = getById(accountId);
        account.setStatus(parseStatus(status));
        return accountRepository.save(account);
    }

    @Transactional
    public Account updateRole(Long accountId, String role) {
        Account account = getById(accountId);
        account.setRole(parseRole(role));
        return accountRepository.save(account);
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

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid role: " + value);
        }
    }

    private AccountStatus parseStatus(String value) {
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid status: " + value);
        }
    }
}