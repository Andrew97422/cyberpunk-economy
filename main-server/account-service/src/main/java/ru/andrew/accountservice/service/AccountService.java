package ru.andrew.accountservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.accountservice.dto.AccountInfoResponse;
import ru.andrew.accountservice.dto.AccountResponse;
import ru.andrew.accountservice.dto.CreateAccountRequest;
import ru.andrew.accountservice.entity.Account;
import ru.andrew.accountservice.entity.AccountStatus;
import ru.andrew.accountservice.entity.Role;
import ru.andrew.accountservice.exception.BadRequestException;
import ru.andrew.accountservice.exception.NotFoundException;
import ru.andrew.accountservice.exception.UnauthorizedException;
import ru.andrew.accountservice.repository.AccountRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEventPublisher accountEventPublisher;

    @Transactional(readOnly = true)
    public Account getById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public AccountResponse getResponseById(Long id) {
        return toResponse(getById(id));
    }

    @Transactional(readOnly = true)
    public AccountResponse getByPublicNameResponse(String publicName) {
        Account account = accountRepository.findByPublicName(publicName)
                .orElseThrow(() -> new NotFoundException("Account not found: " + publicName));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountInfoResponse getActiveByPublicNameShort(String publicName) {
        Account account = accountRepository.findByPublicName(publicName)
                .orElseThrow(() -> new NotFoundException("Account not found: " + publicName));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Account is not active");
        }
        return AccountInfoResponse.builder()
                .id(account.getId())
                .publicName(account.getPublicName())
                .role(account.getRole().name())
                .status(account.getStatus().name())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getAccounts(String search, String role, String status, Pageable pageable) {
        String s = search == null ? "" : search.trim();
        return accountRepository.search(s, parseRoleOrNull(role), parseStatusOrNull(status), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
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
        accountEventPublisher.publishCreated(saved);
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse updateStatus(Long accountId, String status) {
        Account account = getById(accountId);
        account.setStatus(parseStatus(status));
        Account saved = accountRepository.save(account);
        accountEventPublisher.publishUpdated(saved, "account.status_changed");
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse updateRole(Long accountId, String role) {
        Account account = getById(accountId);
        account.setRole(parseRole(role));
        Account saved = accountRepository.save(account);
        accountEventPublisher.publishUpdated(saved, "account.role_changed");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<AccountInfoResponse> verifyCredentials(String publicName, String password) {
        if (publicName == null || password == null) return Optional.empty();
        return accountRepository.findByPublicName(publicName)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .filter(a -> a.getPasswordHash() != null
                        && passwordEncoder.matches(password, a.getPasswordHash()))
                .map(a -> AccountInfoResponse.builder()
                        .id(a.getId())
                        .publicName(a.getPublicName())
                        .role(a.getRole().name())
                        .status(a.getStatus().name())
                        .build());
    }

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

    private Role parseRoleOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Role.valueOf(value.trim().toUpperCase()); } catch (Exception ex) { return null; }
    }

    private AccountStatus parseStatusOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try { return AccountStatus.valueOf(value.trim().toUpperCase()); } catch (Exception ex) { return null; }
    }
}
