package ru.andrew.mainserver.banking.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BalanceResponse {
    private Long accountId;
    private String publicName;
    private BigDecimal cashlessAmount;
    private BigDecimal cryptoAmount;
}