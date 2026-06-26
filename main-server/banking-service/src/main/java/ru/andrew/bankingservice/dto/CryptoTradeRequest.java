package ru.andrew.bankingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** amount = how much to spend (BUY: in CASHLESS) or how much CRYPTO to sell (SELL). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CryptoTradeRequest(BigDecimal amount) {
}
