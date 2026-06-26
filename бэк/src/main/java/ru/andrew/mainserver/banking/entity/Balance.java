package ru.andrew.mainserver.banking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.mainserver.account.entity.Account;

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

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

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