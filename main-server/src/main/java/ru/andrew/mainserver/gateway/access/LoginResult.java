package ru.andrew.mainserver.gateway.access;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginResult(
        SessionInfo session,
        Long accountId,
        String publicName,
        String role
) {
}
