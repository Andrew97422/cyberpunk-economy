package ru.andrew.marketplaceservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.marketplaceservice.entity.ScenarioSchedule;

import java.time.Instant;
import java.util.List;

public interface ScenarioScheduleRepository extends JpaRepository<ScenarioSchedule, Long> {

    List<ScenarioSchedule> findByActiveTrueAndNextFireAtLessThanEqual(Instant now);

    List<ScenarioSchedule> findAllByOrderByCreatedAtDesc();
}
