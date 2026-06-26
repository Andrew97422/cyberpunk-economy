package ru.andrew.bankingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.bankingservice.entity.Deposit;

import java.util.List;

public interface DepositRepository extends JpaRepository<Deposit, Long> {
    List<Deposit> findByAccountIdAndStatusOrderByOpenedAtDesc(Long accountId, String status);
    List<Deposit> findByStatus(String status);
}
