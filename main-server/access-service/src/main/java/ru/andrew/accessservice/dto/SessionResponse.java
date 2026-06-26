package ru.andrew.accessservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SessionResponse {
    private Long id;
    private Long accountId;
    private String publicName;
    private String role;
    private String sessionType;
    private Long pinCodeId;
    private Long terminalId;
    private String terminalName;
    private String status;
    private Integer durationMinutes;
    private Instant startedAt;
    private Instant expiresAt;
    private Instant endedAt;
}
