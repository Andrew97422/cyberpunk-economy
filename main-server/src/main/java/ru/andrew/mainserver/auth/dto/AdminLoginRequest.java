package ru.andrew.mainserver.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminLoginRequest {

    @NotBlank
    private String publicName;

    @NotBlank
    private String password;
}