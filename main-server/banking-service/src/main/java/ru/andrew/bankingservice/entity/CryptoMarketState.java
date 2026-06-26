package ru.andrew.bankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single-row (id=1) state of the crypto market. {@code rate} = price of 1 CRYPTO in CASHLESS.
 * The rate evolves by stochastic diffusion (drift + volatility·random + mean reversion toward
 * baseline), with {@code drift} acting as the admin-controlled directional pressure.
 */
@Entity
@Table(name = "crypto_market_state")
@Getter
@Setter
public class CryptoMarketState {

    @Id
    private Long id;

    @Column(name = "rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal rate;

    @Column(name = "baseline", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseline;

    /** Per-tick log-drift bias set by the admin (e.g. +0.02 ≈ +2%/tick up, -0.02 down). */
    @Column(name = "drift", nullable = false)
    private double drift;

    @Column(name = "volatility", nullable = false)
    private double volatility;

    @Column(name = "min_rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal minRate;

    @Column(name = "max_rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxRate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
