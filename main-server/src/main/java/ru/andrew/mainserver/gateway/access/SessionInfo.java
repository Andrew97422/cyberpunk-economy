package ru.andrew.mainserver.gateway.access;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionInfo(
        Long id,
        Long accountId,
        String publicName,
        String role,
        String sessionType,
        String status,
        Integer durationMinutes,
        Instant startedAt,
        Instant expiresAt,
        Instant endedAt,
        Long pinCodeId,
        Long terminalId,
        String terminalName
) {
}
