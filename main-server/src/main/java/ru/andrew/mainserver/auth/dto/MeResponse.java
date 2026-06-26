package ru.andrew.mainserver.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeResponse {
    private Long accountId;
    private String publicName;
    private String role;
    private Long sessionId;
}