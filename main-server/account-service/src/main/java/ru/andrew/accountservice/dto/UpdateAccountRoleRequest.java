package ru.andrew.accountservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAccountRoleRequest {

    @NotNull
    private String role;
}
