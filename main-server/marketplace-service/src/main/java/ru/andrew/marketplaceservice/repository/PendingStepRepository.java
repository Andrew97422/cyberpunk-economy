package ru.andrew.marketplaceservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.marketplaceservice.entity.PendingStep;

import java.time.Instant;
import java.util.List;

public interface PendingStepRepository extends JpaRepository<PendingStep, Long> {

    List<PendingStep> findByStatusAndFireAtLessThanEqual(String status, Instant now);

    List<PendingStep> findByScheduleId(Long scheduleId);
}
