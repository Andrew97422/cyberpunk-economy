package ru.andrew.bankingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.bankingservice.entity.CreditPolicy;

public interface CreditPolicyRepository extends JpaRepository<CreditPolicy, Long> {
}
