package ru.andrew.marketplaceservice.dto;

import java.math.BigDecimal;

/** Summary returned after a single mass price operation. */
public record MassPriceResult(
        int affected,
        String mode,
        BigDecimal value
) {
}
