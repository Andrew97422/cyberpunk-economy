package ru.andrew.marketplaceservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ScheduleResponse {
    private Long id;
    private Long scenarioId;
    private String scenarioName;
    private String mode;
    private Instant startAt;
    private Long intervalSeconds;
    private Instant endAt;
    private boolean active;
    private Instant nextFireAt;
    private Instant lastFiredAt;
    private int fireCount;
    private Instant createdAt;
}
