package ru.andrew.marketplaceservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class OrderResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long buyerAccountId;
    private String buyerPublicName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String currencyType;
    private String status;
    private Long transactionId;
    private Instant createdAt;
    private Instant updatedAt;
}
