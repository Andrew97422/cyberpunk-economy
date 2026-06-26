package ru.andrew.bankingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.dto.CreditOverviewResponse;
import ru.andrew.bankingservice.dto.SetCreditPolicyRequest;
import ru.andrew.bankingservice.entity.CreditPolicy;
import ru.andrew.bankingservice.entity.Deposit;
import ru.andrew.bankingservice.entity.Loan;
import ru.andrew.bankingservice.exception.BadRequestException;
import ru.andrew.bankingservice.repository.BalanceRepository;
import ru.andrew.bankingservice.repository.CreditPolicyRepository;
import ru.andrew.bankingservice.repository.DepositRepository;
import ru.andrew.bankingservice.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Owns the central credit policy (admin key rate) and accrues interest on a timer.
 * Deposit rate = keyRate − depositSpread (≥0); loan rate = keyRate + loanSpread.
 * Rates are "% per accrual period" of {@code accrualMinutes} minutes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final long POLICY_ID = 1L;
    public static final String ACTIVE = "ACTIVE";
    public static final String CLOSED = "CLOSED";
    public static final String REPAID = "REPAID";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BANKER = "BANKER";

    private final CreditPolicyRepository policyRepository;
    private final DepositRepository depositRepository;
    private final LoanRepository loanRepository;
    private final BalanceRepository balanceRepository;

    @Transactional
    public CreditPolicy getOrCreatePolicy() {
        return policyRepository.findById(POLICY_ID).orElseGet(() -> {
            CreditPolicy p = new CreditPolicy();
            p.setId(POLICY_ID);
            p.setKeyRatePct(new BigDecimal("10.00"));
            p.setDepositSpreadPct(new BigDecimal("4.00"));
            p.setLoanSpreadPct(new BigDecimal("8.00"));
            p.setAccrualMinutes(10);
            p.setMaxLoan(new BigDecimal("100000.00"));
            p.setUpdatedAt(Instant.now());
            return policyRepository.save(p);
        });
    }

    public BigDecimal depositRate(CreditPolicy p) {
        return p.getKeyRatePct().subtract(p.getDepositSpreadPct()).max(BigDecimal.ZERO);
    }

    public BigDecimal loanRate(CreditPolicy p) {
        return p.getKeyRatePct().add(p.getLoanSpreadPct()).max(BigDecimal.ZERO);
    }

    @Transactional
    public CreditOverviewResponse overview(Long actorId) {
        CreditPolicy p = getOrCreatePolicy();
        BigDecimal cash = balanceRepository.findByAccountId(actorId)
                .map(b -> b.getCashlessAmount() == null ? BigDecimal.ZERO : b.getCashlessAmount())
                .orElse(BigDecimal.ZERO);

        var deposits = depositRepository.findByAccountIdAndStatusOrderByOpenedAtDesc(actorId, ACTIVE);
        var loans = loanRepository.findByAccountIdAndStatusOrderByOpenedAtDesc(actorId, ACTIVE);

        BigDecimal totalDep = deposits.stream().map(Deposit::getCurrentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebt = loans.stream().map(Loan::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CreditOverviewResponse.builder()
                .keyRatePct(p.getKeyRatePct())
                .depositRatePct(depositRate(p))
                .loanRatePct(loanRate(p))
                .accrualMinutes(p.getAccrualMinutes())
                .maxLoan(p.getMaxLoan())
                .cashlessBalance(scale(cash))
                .totalDeposited(scale(totalDep))
                .totalDebt(scale(totalDebt))
                .deposits(deposits.stream().map(d -> CreditOverviewResponse.DepositView.builder()
                        .id(d.getId()).principal(d.getPrincipal()).currentAmount(d.getCurrentAmount())
                        .openedAt(d.getOpenedAt()).status(d.getStatus()).build()).toList())
                .loans(loans.stream().map(l -> CreditOverviewResponse.LoanView.builder()
                        .id(l.getId()).principal(l.getPrincipal()).debt(l.getDebt())
                        .openedAt(l.getOpenedAt()).status(l.getStatus()).build()).toList())
                .build();
    }

    @Transactional
    public CreditOverviewResponse setPolicy(Long actorId, String actorRole, SetCreditPolicyRequest req) {
        requireAdminOrBanker(actorRole);
        CreditPolicy p = getOrCreatePolicy();
        if (req.keyRatePct() != null) p.setKeyRatePct(req.keyRatePct().setScale(2, RoundingMode.HALF_UP));
        if (req.depositSpreadPct() != null) p.setDepositSpreadPct(req.depositSpreadPct().setScale(2, RoundingMode.HALF_UP));
        if (req.loanSpreadPct() != null) p.setLoanSpreadPct(req.loanSpreadPct().setScale(2, RoundingMode.HALF_UP));
        if (req.accrualMinutes() != null) {
            if (req.accrualMinutes() < 1) throw new BadRequestException("accrualMinutes must be >= 1");
            p.setAccrualMinutes(req.accrualMinutes());
        }
        if (req.maxLoan() != null) p.setMaxLoan(req.maxLoan().setScale(2, RoundingMode.HALF_UP));
        p.setUpdatedAt(Instant.now());
        log.info("Credit policy set by {}: key={}% depSpread={} loanSpread={} period={}m maxLoan={}",
                actorRole, p.getKeyRatePct(), p.getDepositSpreadPct(), p.getLoanSpreadPct(),
                p.getAccrualMinutes(), p.getMaxLoan());
        return overview(actorId);
    }

    /** Compound interest on every active deposit and loan for each elapsed full period. */
    @Scheduled(fixedDelayString = "${app.credit.accrual-check-ms:60000}", initialDelay = 30000)
    @Transactional
    public void accrue() {
        try {
            CreditPolicy p = getOrCreatePolicy();
            long periodSeconds = Math.max(60L, p.getAccrualMinutes() * 60L);
            Instant now = Instant.now();
            BigDecimal depFactor = BigDecimal.ONE.add(depositRate(p).movePointLeft(2));
            BigDecimal loanFactor = BigDecimal.ONE.add(loanRate(p).movePointLeft(2));

            for (Deposit d : depositRepository.findByStatus(ACTIVE)) {
                int periods = elapsedPeriods(d.getLastAccruedAt(), now, periodSeconds);
                if (periods <= 0) continue;
                d.setCurrentAmount(scale(d.getCurrentAmount().multiply(depFactor.pow(periods))));
                d.setLastAccruedAt(d.getLastAccruedAt().plusSeconds((long) periods * periodSeconds));
                depositRepository.save(d);
            }
            for (Loan l : loanRepository.findByStatus(ACTIVE)) {
                int periods = elapsedPeriods(l.getLastAccruedAt(), now, periodSeconds);
                if (periods <= 0) continue;
                l.setDebt(scale(l.getDebt().multiply(loanFactor.pow(periods))));
                l.setLastAccruedAt(l.getLastAccruedAt().plusSeconds((long) periods * periodSeconds));
                loanRepository.save(l);
            }
        } catch (Exception ex) {
            log.error("Credit accrual failed: {}", ex.getMessage());
        }
    }

    private int elapsedPeriods(Instant last, Instant now, long periodSeconds) {
        long elapsed = now.getEpochSecond() - last.getEpochSecond();
        if (elapsed < periodSeconds) return 0;
        return (int) Math.min(elapsed / periodSeconds, 1000); // cap to avoid runaway after long downtime
    }

    private BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private void requireAdminOrBanker(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_BANKER.equals(role)) {
            throw new BadRequestException("Insufficient privileges");
        }
    }
}
