package ru.andrew.marketplaceservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.marketplaceservice.dto.ApplyScenarioResult;
import ru.andrew.marketplaceservice.dto.MassPriceResult;
import ru.andrew.marketplaceservice.dto.PriceStep;
import ru.andrew.marketplaceservice.dto.ScenarioResponse;
import ru.andrew.marketplaceservice.entity.PriceOpMode;
import ru.andrew.marketplaceservice.entity.Product;
import ru.andrew.marketplaceservice.entity.ProductStatus;
import ru.andrew.marketplaceservice.entity.Scenario;
import ru.andrew.marketplaceservice.exception.BadRequestException;
import ru.andrew.marketplaceservice.exception.NotFoundException;
import ru.andrew.marketplaceservice.exception.UnauthorizedException;
import ru.andrew.marketplaceservice.repository.ProductRepository;
import ru.andrew.marketplaceservice.repository.ScenarioRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceScenarioService {

    private static final Set<String> ADMIN_BANKER = Set.of("ADMIN", "BANKER");
    /** Prices never drop to or below this floor. */
    private static final BigDecimal PRICE_FLOOR = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductRepository productRepository;
    private final ScenarioRepository scenarioRepository;
    private final MarketplaceEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ===================== Mass price operation =====================

    @Transactional
    public MassPriceResult applyMassPriceOp(String actorRole, PriceStep step) {
        requireAdminOrBanker(actorRole);
        int affected = applyStepInternal(step);
        eventPublisher.publishMassPriceApplied(
                step.mode(), step.value(), affected, step.category(), step.status());
        return new MassPriceResult(affected, normalizeMode(step.mode()).name(), step.value());
    }

    /**
     * Runs a single step against the matching products and returns how many were changed.
     * No role check — intended for the internal scheduler/poller. The role-checked public paths
     * ({@link #applyMassPriceOp} / {@link #applyScenario}) share the same underlying logic.
     */
    @Transactional
    public int applySingleStep(PriceStep step) {
        return applyStepInternal(step);
    }

    /** Runs a single step against the matching products and returns how many were changed. */
    private int applyStepInternal(PriceStep step) {
        PriceOpMode mode = normalizeMode(step.mode());
        ProductStatus statusFilter = resolveStatusFilter(step.status());
        String categoryFilter = step.category() == null ? "" : step.category().trim();

        List<Product> products = productRepository.findForMassOp(statusFilter, categoryFilter);
        int affected = 0;
        for (Product product : products) {
            BigDecimal current = product.getPrice();
            BigDecimal next = computeNextPrice(mode, current, product.getBasePrice(), step.value());
            if (next == null || next.compareTo(current) == 0) {
                continue; // skip (e.g. RESET with no base price) or no change
            }
            product.setPrice(next);
            productRepository.save(product);
            affected++;
        }
        return affected;
    }

    /** Returns the new price, or null if this product should be skipped. */
    private BigDecimal computeNextPrice(PriceOpMode mode, BigDecimal current,
                                        BigDecimal basePrice, BigDecimal value) {
        BigDecimal raw;
        switch (mode) {
            case MULTIPLY -> {
                BigDecimal factor = requireValue(value);
                raw = current.multiply(factor);
            }
            case INCREASE_PCT -> {
                BigDecimal pct = requireValue(value);
                raw = current.multiply(BigDecimal.ONE.add(pct.divide(HUNDRED)));
            }
            case DECREASE_PCT -> {
                BigDecimal pct = requireValue(value);
                raw = current.multiply(BigDecimal.ONE.subtract(pct.divide(HUNDRED)));
            }
            case RESET -> {
                if (basePrice == null) return null; // nothing to reset to -> skip
                raw = basePrice;
            }
            default -> throw new BadRequestException("Unsupported price-op mode");
        }
        BigDecimal rounded = raw.setScale(2, RoundingMode.HALF_UP);
        // Never allow a non-positive price; clamp to the floor.
        return rounded.compareTo(PRICE_FLOOR) < 0 ? PRICE_FLOOR : rounded;
    }

    // ===================== Scenario CRUD =====================

    @Transactional(readOnly = true)
    public List<ScenarioResponse> listScenarios(String actorRole) {
        requireAdminOrBanker(actorRole);
        return scenarioRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScenarioResponse createScenario(String actorRole, String name, String description,
                                           List<PriceStep> steps) {
        requireAdminOrBanker(actorRole);
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        validateSteps(steps);
        Scenario scenario = new Scenario();
        scenario.setName(name.trim());
        scenario.setDescription(blankToNull(description));
        scenario.setStepsJson(writeSteps(steps));
        return toResponse(scenarioRepository.save(scenario));
    }

    @Transactional
    public ScenarioResponse updateScenario(String actorRole, Long id, String name,
                                           String description, List<PriceStep> steps) {
        requireAdminOrBanker(actorRole);
        Scenario scenario = getScenario(id);
        if (name != null && !name.isBlank()) {
            scenario.setName(name.trim());
        }
        if (description != null) {
            scenario.setDescription(blankToNull(description));
        }
        if (steps != null) {
            validateSteps(steps);
            scenario.setStepsJson(writeSteps(steps));
        }
        return toResponse(scenarioRepository.save(scenario));
    }

    @Transactional
    public void deleteScenario(String actorRole, Long id) {
        requireAdminOrBanker(actorRole);
        Scenario scenario = getScenario(id);
        scenarioRepository.delete(scenario);
    }

    @Transactional
    public ApplyScenarioResult applyScenario(String actorRole, Long id) {
        requireAdminOrBanker(actorRole);
        Scenario scenario = getScenario(id);
        List<PriceStep> steps = readSteps(scenario.getStepsJson());
        int totalAffected = 0;
        for (PriceStep step : steps) {
            totalAffected += applyStepInternal(step);
        }
        eventPublisher.publishScenarioApplied(scenario.getId(), scenario.getName(),
                steps.size(), totalAffected);
        return new ApplyScenarioResult(scenario.getId(), scenario.getName(), steps.size(), totalAffected);
    }

    // ===================== Helpers =====================

    private Scenario getScenario(Long id) {
        if (id == null) throw new BadRequestException("scenarioId is required");
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scenario not found: " + id));
    }

    private void validateSteps(List<PriceStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new BadRequestException("scenario must have at least one step");
        }
        for (PriceStep step : steps) {
            PriceOpMode mode = normalizeMode(step.mode());
            if (mode != PriceOpMode.RESET && step.value() == null) {
                throw new BadRequestException("value is required for mode " + mode);
            }
        }
    }

    private PriceOpMode normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw new BadRequestException("mode is required");
        }
        try {
            return PriceOpMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid mode: " + mode);
        }
    }

    /** Status filter: explicit value when given, otherwise default to ACTIVE products only. */
    private ProductStatus resolveStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return ProductStatus.ACTIVE;
        }
        try {
            return ProductStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    private BigDecimal requireValue(BigDecimal value) {
        if (value == null) {
            throw new BadRequestException("value is required for this mode");
        }
        return value;
    }

    private String writeSteps(List<PriceStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize scenario steps", ex);
        }
    }

    private List<PriceStep> readSteps(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<PriceStep>>() {});
        } catch (Exception ex) {
            log.error("Failed to parse scenario steps json", ex);
            throw new BadRequestException("Scenario steps are corrupted");
        }
    }

    private ScenarioResponse toResponse(Scenario s) {
        return ScenarioResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .steps(readSteps(s.getStepsJson()))
                .createdAt(s.getCreatedAt())
                .build();
    }

    private void requireAdminOrBanker(String role) {
        if (!ADMIN_BANKER.contains(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
