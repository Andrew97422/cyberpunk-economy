package ru.andrew.bankingservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CryptoTradeResult {
    private boolean success;
    private String side;          // BUY | SELL
    private BigDecimal rate;      // rate used
    private BigDecimal spent;     // amount of source currency given
    private BigDecimal received;  // amount of target currency received
    private BigDecimal cashlessAfter;
    private BigDecimal cryptoAfter;
    private String message;
}
