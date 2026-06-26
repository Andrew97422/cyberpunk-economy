package ru.andrew.bankingservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.entity.Balance;
import ru.andrew.bankingservice.repository.BalanceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class BalanceInitializationService {

    private final BalanceRepository balanceRepository;

    @Transactional
    public void createBalanceIfAbsent(Long accountId) {
        balanceRepository.findByAccountId(accountId).orElseGet(() -> {
            Balance balance = new Balance();
            balance.setAccountId(accountId);
            balance.setCashlessAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            balance.setCryptoAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return balanceRepository.save(balance);
        });
    }
}