package ru.andrew.bankingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** One-time political shock: rate is multiplied by (1 + pct/100). reason is for the log/news. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CryptoShockRequest(BigDecimal pct, String reason) {
}
