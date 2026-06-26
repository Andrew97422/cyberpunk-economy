package ru.andrew.accessservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResult {
    private SessionResponse session;
    private Long accountId;
    private String publicName;
    private String role;
}
