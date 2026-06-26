package ru.andrew.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.analyticsservice.entity.MoneyFlow;

public interface MoneyFlowRepository extends JpaRepository<MoneyFlow, Long> {
    boolean existsByEventId(String eventId);
}
