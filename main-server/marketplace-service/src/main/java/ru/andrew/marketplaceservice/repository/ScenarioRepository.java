package ru.andrew.marketplaceservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.marketplaceservice.entity.Scenario;

import java.util.List;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findAllByOrderByCreatedAtDesc();
}
