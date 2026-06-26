package ru.andrew.mainserver.banking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TransferRequest {
    @NotBlank
    private String fromPublicName;
    @NotBlank
    private String toPublicName;
    @NotBlank
    private String currencyType;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    private String comment;
}