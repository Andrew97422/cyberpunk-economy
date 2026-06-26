package ru.andrew.bankingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Generic credit action input: amount (+ optional target id for close/repay). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditActionRequest(BigDecimal amount, Long depositId, Long loanId) {
}
