package ru.andrew.bankingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Admin tuning of the credit policy. Null fields are left unchanged. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SetCreditPolicyRequest(
        BigDecimal keyRatePct,
        BigDecimal depositSpreadPct,
        BigDecimal loanSpreadPct,
        Integer accrualMinutes,
        BigDecimal maxLoan
) {
}
