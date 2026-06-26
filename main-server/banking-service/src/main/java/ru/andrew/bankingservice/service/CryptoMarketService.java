package ru.andrew.bankingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.dto.CryptoMarketUpdateRequest;
import ru.andrew.bankingservice.dto.CryptoRateResponse;
import ru.andrew.bankingservice.dto.CryptoShockRequest;
import ru.andrew.bankingservice.entity.CryptoMarketState;
import ru.andrew.bankingservice.entity.CryptoTick;
import ru.andrew.bankingservice.exception.BadRequestException;
import ru.andrew.bankingservice.repository.CryptoMarketStateRepository;
import ru.andrew.bankingservice.repository.CryptoTickRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The crypto market. The rate follows a discrete stochastic process (geometric diffusion):
 *   logReturn = drift + κ·ln(baseline/rate) + volatility·Z,   Z ~ N(0,1)
 *   rateNext  = clamp(rate · e^logReturn, [min, max])
 * — i.e. a "physical law" (mean-reverting random walk) plus randomness, where {@code drift}
 * is the admin's directional pressure ("political" pump/dump) and a shock multiplies instantly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoMarketService {

    private static final long STATE_ID = 1L;
    private static final double MEAN_REVERSION = 0.03; // κ — how strongly the rate is pulled to baseline
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BANKER = "BANKER";

    private final CryptoMarketStateRepository stateRepository;
    private final CryptoTickRepository tickRepository;

    @Transactional
    public CryptoMarketState getOrCreateState() {
        return stateRepository.findById(STATE_ID).orElseGet(() -> {
            CryptoMarketState s = new CryptoMarketState();
            s.setId(STATE_ID);
            s.setRate(new BigDecimal("100.00"));
            s.setBaseline(new BigDecimal("100.00"));
            s.setDrift(0.0);
            s.setVolatility(0.05);
            s.setMinRate(new BigDecimal("10.00"));
            s.setMaxRate(new BigDecimal("1000.00"));
            s.setUpdatedAt(Instant.now());
            CryptoMarketState saved = stateRepository.save(s);
            appendTick(saved.getRate());
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentRate() {
        return stateRepository.findById(STATE_ID)
                .map(CryptoMarketState::getRate)
                .orElse(new BigDecimal("100.00"));
    }

    @Transactional
    public CryptoRateResponse getRate() {
        CryptoMarketState s = getOrCreateState();
        List<CryptoTick> recent = tickRepository.findTop120ByOrderByCreatedAtDesc();
        List<CryptoRateResponse.TickPoint> history = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) { // oldest → newest for charting
            CryptoTick t = recent.get(i);
            history.add(CryptoRateResponse.TickPoint.builder().rate(t.getRate()).at(t.getCreatedAt()).build());
        }
        return CryptoRateResponse.builder()
                .rate(s.getRate()).baseline(s.getBaseline())
                .drift(s.getDrift()).volatility(s.getVolatility())
                .minRate(s.getMinRate()).maxRate(s.getMaxRate())
                .updatedAt(s.getUpdatedAt()).history(history)
                .build();
    }

    /** Advance the market one step. Runs on a timer. */
    @Scheduled(fixedDelayString = "${app.crypto.tick-ms:45000}", initialDelay = 20000)
    @Transactional
    public void tick() {
        try {
            CryptoMarketState s = getOrCreateState();
            double rate = s.getRate().doubleValue();
            double baseline = s.getBaseline().doubleValue();
            double z = ThreadLocalRandom.current().nextGaussian();
            double meanRev = MEAN_REVERSION * Math.log(baseline / Math.max(rate, 0.0001));
            double logReturn = s.getDrift() + meanRev + s.getVolatility() * z;
            double next = rate * Math.exp(logReturn);
            BigDecimal clamped = clamp(next, s);
            s.setRate(clamped);
            s.setUpdatedAt(Instant.now());
            stateRepository.save(s);
            appendTick(clamped);
        } catch (Exception ex) {
            log.error("Crypto tick failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public CryptoRateResponse setMarket(String actorRole, CryptoMarketUpdateRequest req) {
        requireAdminOrBanker(actorRole);
        CryptoMarketState s = getOrCreateState();
        if (req.drift() != null) s.setDrift(req.drift());
        if (req.volatility() != null) {
            if (req.volatility() < 0) throw new BadRequestException("volatility must be >= 0");
            s.setVolatility(req.volatility());
        }
        if (req.baseline() != null) s.setBaseline(req.baseline().setScale(2, RoundingMode.HALF_UP));
        if (req.minRate() != null) s.setMinRate(req.minRate().setScale(2, RoundingMode.HALF_UP));
        if (req.maxRate() != null) s.setMaxRate(req.maxRate().setScale(2, RoundingMode.HALF_UP));
        if (s.getMinRate().compareTo(s.getMaxRate()) > 0) {
            throw new BadRequestException("minRate must be <= maxRate");
        }
        s.setUpdatedAt(Instant.now());
        log.info("Crypto market updated by {}: drift={} vol={} baseline={} band=[{},{}]",
                actorRole, s.getDrift(), s.getVolatility(), s.getBaseline(), s.getMinRate(), s.getMaxRate());
        return getRate();
    }

    /** Instant political shock: multiply the rate by (1 + pct/100). */
    @Transactional
    public CryptoRateResponse shock(String actorRole, CryptoShockRequest req) {
        requireAdminOrBanker(actorRole);
        if (req.pct() == null) throw new BadRequestException("pct is required");
        CryptoMarketState s = getOrCreateState();
        double factor = 1.0 + req.pct().doubleValue() / 100.0;
        if (factor <= 0) throw new BadRequestException("pct too low");
        BigDecimal next = clamp(s.getRate().doubleValue() * factor, s);
        s.setRate(next);
        s.setUpdatedAt(Instant.now());
        stateRepository.save(s);
        appendTick(next);
        log.info("Crypto shock by {}: {}% -> rate {} (reason: {})", actorRole, req.pct(), next, req.reason());
        return getRate();
    }

    private void appendTick(BigDecimal rate) {
        CryptoTick t = new CryptoTick();
        t.setRate(rate);
        t.setCreatedAt(Instant.now());
        tickRepository.save(t);
    }

    private BigDecimal clamp(double value, CryptoMarketState s) {
        double min = s.getMinRate().doubleValue();
        double max = s.getMaxRate().doubleValue();
        double v = Math.min(max, Math.max(min, value));
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private void requireAdminOrBanker(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_BANKER.equals(role)) {
            throw new BadRequestException("Insufficient privileges");
        }
    }
}
