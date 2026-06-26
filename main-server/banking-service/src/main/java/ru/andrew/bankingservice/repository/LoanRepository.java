package ru.andrew.bankingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.bankingservice.entity.Loan;

import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByAccountIdAndStatusOrderByOpenedAtDesc(Long accountId, String status);
    List<Loan> findByStatus(String status);
}
