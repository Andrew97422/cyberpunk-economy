package ru.andrew.terminalservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TerminalResponse {
    private Long id;
    private String name;
    private String ipAddress;
    private String location;
    private String terminalType;
    private String status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
