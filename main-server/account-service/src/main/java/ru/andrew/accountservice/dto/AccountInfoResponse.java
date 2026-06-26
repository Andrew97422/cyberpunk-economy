package ru.andrew.accountservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountInfoResponse {
    private Long id;
    private String publicName;
    private String role;
    private String status;
}
