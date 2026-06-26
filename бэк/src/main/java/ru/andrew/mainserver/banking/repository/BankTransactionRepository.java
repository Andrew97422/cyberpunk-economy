package ru.andrew.mainserver.banking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.andrew.mainserver.banking.entity.BankTransaction;

import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    Page<BankTransaction> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BankTransaction> findWithLockById(Long id);
}