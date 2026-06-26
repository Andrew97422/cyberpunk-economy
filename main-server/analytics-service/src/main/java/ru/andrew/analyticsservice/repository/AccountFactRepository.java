package ru.andrew.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.analyticsservice.entity.AccountFact;

public interface AccountFactRepository extends JpaRepository<AccountFact, Long> {
    boolean existsByEventId(String eventId);
}
