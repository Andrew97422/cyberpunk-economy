package ru.andrew.analyticsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per banking balance-movement event (deposit/withdraw/transfer/adjust/reversal).
 * balanceAfter lets us reconstruct each account's current balance (latest per account+currency).
 */
@Entity
@Table(name = "money_flow", indexes = {
        @Index(name = "idx_mf_account", columnList = "accountId"),
        @Index(name = "idx_mf_occurred", columnList = "occurredAt")
})
@Getter
@Setter
public class MoneyFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String eventId;

    private Instant occurredAt;
    private String eventType;
    private String operation;
    private Long accountId;
    private String publicName;
    private String currencyType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Long relatedAccountId;
    private Long actorId;
}
