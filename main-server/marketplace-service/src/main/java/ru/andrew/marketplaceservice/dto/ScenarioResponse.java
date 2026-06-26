package ru.andrew.marketplaceservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ScenarioResponse {
    private Long id;
    private String name;
    private String description;
    private List<PriceStep> steps;
    private Instant createdAt;
}
