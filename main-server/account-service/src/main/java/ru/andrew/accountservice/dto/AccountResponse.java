package ru.andrew.accountservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AccountResponse {
    private Long id;
    private String publicName;
    private String characterName;
    private String role;
    private String status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
