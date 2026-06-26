package ru.andrew.mainserver.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {
    private String token;
    private Long accountId;
    private String publicName;
    private String role;
    private Long sessionId;
}