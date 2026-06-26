package ru.andrew.mainserver.session.dto;

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
    private Long pinCodeId;
    private String terminalName;
    private String status;
    private Integer durationMinutes;
    private Instant startedAt;
    private Instant endedAt;
    private Instant lastSeenAt;
}