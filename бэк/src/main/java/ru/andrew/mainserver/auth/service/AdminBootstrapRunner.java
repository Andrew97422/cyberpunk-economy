package ru.andrew.mainserver.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.AccountStatus;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.repository.AccountRepository;
import ru.andrew.mainserver.banking.entity.Balance;
import ru.andrew.mainserver.banking.repository.BalanceRepository;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (accountRepository.findByPublicName("admin").isPresent()) {
            return;
        }

        Account admin = new Account();
        admin.setPublicName("admin");
        admin.setCharacterName("System Admin");
        admin.setRole(Role.ADMIN);
        admin.setStatus(AccountStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode("admin12345"));
        admin.setNotes("Bootstrap admin");

        Account saved = accountRepository.save(admin);

        Balance balance = new Balance();
        balance.setAccount(saved);
        balance.setCashlessAmount(BigDecimal.ZERO);
        balance.setCryptoAmount(BigDecimal.ZERO);
        balanceRepository.save(balance);
    }
}