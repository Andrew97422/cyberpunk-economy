package ru.andrew.bankingservice.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record BankingEventPayload(
        Long transactionId,
        Long accountId,
        String publicName,
        Long relatedAccountId,
        String relatedPublicName,
        String currencyType,
        String operation,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long actorId,
        LocalDateTime occurredAt
) {
}