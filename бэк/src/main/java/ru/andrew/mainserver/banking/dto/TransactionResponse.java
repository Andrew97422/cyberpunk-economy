package ru.andrew.mainserver.banking.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class TransactionResponse {
    private Long id;
    private Long accountId;
    private String publicName;
    private Long relatedAccountId;
    private String relatedPublicName;
    private String type;
    private String currencyType;
    private String status;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String comment;
    private Long createdByAccountId;
    private Instant createdAt;
}