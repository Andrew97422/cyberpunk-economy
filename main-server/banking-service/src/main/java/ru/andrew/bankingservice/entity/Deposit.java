package ru.andrew.bankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A player's interest-bearing deposit. currentAmount compounds each accrual period. */
@Entity
@Table(name = "deposits", indexes = {
        @Index(name = "idx_dep_account", columnList = "account_id"),
        @Index(name = "idx_dep_status", columnList = "status")
})
@Getter
@Setter
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "public_name")
    private String publicName;

    @Column(name = "principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "current_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentAmount;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "last_accrued_at", nullable = false)
    private Instant lastAccruedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // ACTIVE | CLOSED

    @Column(name = "closed_at")
    private Instant closedAt;
}
