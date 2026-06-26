package ru.andrew.bankingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Admin tuning of the market. Null fields are left unchanged. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CryptoMarketUpdateRequest(
        Double drift,
        Double volatility,
        BigDecimal baseline,
        BigDecimal minRate,
        BigDecimal maxRate
) {
}
