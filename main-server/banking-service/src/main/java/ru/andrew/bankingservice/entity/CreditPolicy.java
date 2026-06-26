package ru.andrew.bankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single-row (id=1) credit policy. The admin-set {@code keyRatePct} is the central rate;
 * deposit rate = keyRate − depositSpread (floored at 0), loan rate = keyRate + loanSpread.
 * Rates are "% per accrual period"; the period is {@code accrualMinutes}.
 */
@Entity
@Table(name = "credit_policy")
@Getter
@Setter
public class CreditPolicy {

    @Id
    private Long id;

    @Column(name = "key_rate_pct", nullable = false, precision = 19, scale = 2)
    private BigDecimal keyRatePct;

    @Column(name = "deposit_spread_pct", nullable = false, precision = 19, scale = 2)
    private BigDecimal depositSpreadPct;

    @Column(name = "loan_spread_pct", nullable = false, precision = 19, scale = 2)
    private BigDecimal loanSpreadPct;

    @Column(name = "accrual_minutes", nullable = false)
    private int accrualMinutes;

    @Column(name = "max_loan", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxLoan;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
