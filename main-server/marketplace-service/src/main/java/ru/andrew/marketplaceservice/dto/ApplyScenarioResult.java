package ru.andrew.marketplaceservice.dto;

/** Summary returned after applying every step of a scenario in order. */
public record ApplyScenarioResult(
        Long scenarioId,
        String name,
        int steps,
        int totalAffected
) {
}
