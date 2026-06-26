package ru.andrew.marketplaceservice.entity;

/**
 * How a mass price operation transforms each product's price.
 * <ul>
 *   <li>{@code MULTIPLY}     — price = price × value (e.g. value 0.7 => -30%)</li>
 *   <li>{@code INCREASE_PCT} — price = price × (1 + value/100) (e.g. value 20 => +20%)</li>
 *   <li>{@code DECREASE_PCT} — price = price × (1 - value/100) (e.g. value 30 => -30%)</li>
 *   <li>{@code RESET}        — price = basePrice (value ignored)</li>
 * </ul>
 */
public enum PriceOpMode {
    MULTIPLY,
    INCREASE_PCT,
    DECREASE_PCT,
    RESET
}
