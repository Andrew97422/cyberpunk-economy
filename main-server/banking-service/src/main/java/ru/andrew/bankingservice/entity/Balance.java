package ru.andrew.bankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "balances")
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true)
    private Long accountId;   // вместо @OneToOne Account

    @Column(name = "cashless_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashlessAmount;

    @Column(name = "crypto_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal cryptoAmount;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = Instant.now();
    }
}