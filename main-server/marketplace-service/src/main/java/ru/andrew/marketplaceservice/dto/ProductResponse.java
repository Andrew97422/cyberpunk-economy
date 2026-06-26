package ru.andrew.marketplaceservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class ProductResponse {
    private Long id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal basePrice;
    private String currencyType;
    private Integer stockQuantity;
    private String status;
    private String category;
    private String imageUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
