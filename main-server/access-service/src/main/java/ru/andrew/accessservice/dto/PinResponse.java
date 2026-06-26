package ru.andrew.accessservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PinResponse {
    private Long id;
    private Long accountId;
    private String publicName;
    private String status;
    private Integer durationMinutes;
    private Instant createdAt;
    private Instant usedAt;
    private String comment;
}
