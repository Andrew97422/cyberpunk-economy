package ru.andrew.bankingservice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.andrew.bankingservice.entity.Balance;

import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, Long> {

    Optional<Balance> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Balance> findWithLockByAccountId(Long accountId);
}