package ru.andrew.mainserver.gateway.accounts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountInfo(
        Long id,
        String publicName,
        String role,
        String status
) {
}
