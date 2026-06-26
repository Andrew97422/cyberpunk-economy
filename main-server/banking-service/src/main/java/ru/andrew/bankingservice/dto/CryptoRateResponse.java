package ru.andrew.bankingservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class CryptoRateResponse {
    private BigDecimal rate;
    private BigDecimal baseline;
    private double drift;
    private double volatility;
    private BigDecimal minRate;
    private BigDecimal maxRate;
    private Instant updatedAt;
    private List<TickPoint> history;

    @Getter
    @Builder
    public static class TickPoint {
        private BigDecimal rate;
        private Instant at;
    }
}
