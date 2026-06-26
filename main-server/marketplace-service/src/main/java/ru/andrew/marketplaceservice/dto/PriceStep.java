package ru.andrew.marketplaceservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * A single price-op step. Same shape as the MASS_PRICE_OP payload and as one element of a
 * scenario's steps array.
 *
 * @param mode     one of MULTIPLY, INCREASE_PCT, DECREASE_PCT, RESET
 * @param value    operand for the mode (ignored for RESET; may be null there)
 * @param category optional category filter (empty/null = all categories)
 * @param status   optional product-status filter (empty/null = ACTIVE only)
 * @param delaySeconds delay (in seconds) AFTER the previous step before this step fires; for the
 *                     first step it is relative to the run trigger. Null means 0 (immediate).
 *                     Older stored scenarios omit this field — deserialization tolerates that.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceStep(
        String mode,
        BigDecimal value,
        String category,
        String status,
        Long delaySeconds
) {
}
