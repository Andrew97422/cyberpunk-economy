package ru.andrew.mainserver.banking.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class OperationResultResponse {
    private boolean success;
    private String operation;
    private Long accountId;
    private String publicName;
    private Long relatedAccountId;
    private String relatedPublicName;
    private String currencyType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Long transactionId;
    private Long relatedTransactionId;
    private String message;
}