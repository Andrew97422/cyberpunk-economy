package ru.andrew.bankingservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class WithdrawRequest {
    @NotBlank
    private String publicName;
    @NotBlank
    private String currencyType;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    private String comment;
}