package ru.andrew.bankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One historical rate point (for the chart). */
@Entity
@Table(name = "crypto_tick", indexes = @Index(name = "idx_crypto_tick_created", columnList = "created_at"))
@Getter
@Setter
public class CryptoTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal rate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
