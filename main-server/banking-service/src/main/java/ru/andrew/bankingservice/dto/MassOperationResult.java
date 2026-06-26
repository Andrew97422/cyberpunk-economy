package ru.andrew.bankingservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MassOperationResult {
    private boolean success;
    private String mode;
    private String currencyType;
    private int affected;
    private BigDecimal totalDelta;
    private String message;
}
