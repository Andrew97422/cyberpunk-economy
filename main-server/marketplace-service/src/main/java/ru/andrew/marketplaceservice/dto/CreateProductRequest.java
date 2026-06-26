package ru.andrew.marketplaceservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateProductRequest {
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private String currencyType;
    private Integer stockQuantity;
    private String category;
    private String imageUrl;
}
