package ru.andrew.accountservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyCredentialsRequest {
    private String publicName;
    private String password;
}
