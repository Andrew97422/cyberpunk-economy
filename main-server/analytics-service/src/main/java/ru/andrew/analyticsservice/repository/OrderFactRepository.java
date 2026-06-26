package ru.andrew.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.analyticsservice.entity.OrderFact;

public interface OrderFactRepository extends JpaRepository<OrderFact, Long> {
    boolean existsByEventId(String eventId);
}
