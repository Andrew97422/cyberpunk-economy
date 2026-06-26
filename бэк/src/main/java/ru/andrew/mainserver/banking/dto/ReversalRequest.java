package ru.andrew.mainserver.banking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReversalRequest {
    @NotNull
    private Long transactionId;
    @NotBlank
    private String comment;
}