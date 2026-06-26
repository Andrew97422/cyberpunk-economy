package ru.andrew.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAccountRequest {

    @NotBlank
    private String publicName;

    private String characterName;

    @NotNull
    private String role;

    @NotNull
    private String status;

    private String password;

    private String notes;
}
