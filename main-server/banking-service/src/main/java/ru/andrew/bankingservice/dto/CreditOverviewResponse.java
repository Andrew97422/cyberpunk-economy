package ru.andrew.bankingservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class CreditOverviewResponse {

    private BigDecimal keyRatePct;
    private BigDecimal depositRatePct;
    private BigDecimal loanRatePct;
    private int accrualMinutes;
    private BigDecimal maxLoan;

    private BigDecimal cashlessBalance;
    private BigDecimal totalDeposited;
    private BigDecimal totalDebt;

    private List<DepositView> deposits;
    private List<LoanView> loans;

    @Getter
    @Builder
    public static class DepositView {
        private Long id;
        private BigDecimal principal;
        private BigDecimal currentAmount;
        private Instant openedAt;
        private String status;
    }

    @Getter
    @Builder
    public static class LoanView {
        private Long id;
        private BigDecimal principal;
        private BigDecimal debt;
        private Instant openedAt;
        private String status;
    }
}
