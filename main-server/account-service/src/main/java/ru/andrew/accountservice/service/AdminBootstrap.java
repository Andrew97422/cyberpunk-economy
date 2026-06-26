package ru.andrew.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.andrew.accountservice.entity.Account;
import ru.andrew.accountservice.entity.AccountStatus;
import ru.andrew.accountservice.entity.Role;
import ru.andrew.accountservice.repository.AccountRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountEventPublisher accountEventPublisher;

    @Value("${app.bootstrap.admin-public-name:admin}")
    private String adminPublicName;

    @Value("${app.bootstrap.admin-password:admin12345}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (accountRepository.findByPublicName(adminPublicName).isPresent()) {
            return;
        }

        Account admin = new Account();
        admin.setPublicName(adminPublicName);
        admin.setCharacterName("System Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setNotes("Bootstrap admin");

        Account saved = accountRepository.save(admin);
        accountEventPublisher.publishCreated(saved);
        log.info("Bootstrap admin created: {}", adminPublicName);
    }
}
