package ru.andrew.marketplaceservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.marketplaceservice.dto.PriceStep;
import ru.andrew.marketplaceservice.dto.ScheduleResponse;
import ru.andrew.marketplaceservice.entity.PendingStep;
import ru.andrew.marketplaceservice.entity.Scenario;
import ru.andrew.marketplaceservice.entity.ScenarioSchedule;
import ru.andrew.marketplaceservice.exception.BadRequestException;
import ru.andrew.marketplaceservice.exception.NotFoundException;
import ru.andrew.marketplaceservice.exception.UnauthorizedException;
import ru.andrew.marketplaceservice.repository.PendingStepRepository;
import ru.andrew.marketplaceservice.repository.ScenarioRepository;
import ru.andrew.marketplaceservice.repository.ScenarioScheduleRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages time schedules for price scenarios and turns scenario steps into queued {@link PendingStep}s.
 * Role-checked entry points (create/list/cancel/triggerNow) mirror {@link PriceScenarioService}'s
 * ADMIN/BANKER guard; the {@code enqueueScenarioSteps} helper is internal (no role check), used by
 * the poller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioScheduleService {

    private static final Set<String> ADMIN_BANKER = Set.of("ADMIN", "BANKER");
    private static final String MODE_ONCE = "ONCE";
    private static final String MODE_RECURRING = "RECURRING";

    private final ScenarioRepository scenarioRepository;
    private final ScenarioScheduleRepository scheduleRepository;
    private final PendingStepRepository pendingStepRepository;
    private final PriceScenarioService priceScenarioService;
    private final ObjectMapper objectMapper;

    // ===================== Role-checked entry points =====================

    @Transactional
    public ScheduleResponse createSchedule(String actorRole, Long scenarioId, String mode,
                                           Instant startAt, Long intervalSeconds, Instant endAt) {
        requireAdminOrBanker(actorRole);
        Scenario scenario = getScenario(scenarioId);
        String normalizedMode = normalizeMode(mode);
        if (MODE_RECURRING.equals(normalizedMode) && (intervalSeconds == null || intervalSeconds <= 0)) {
            throw new BadRequestException("intervalSeconds must be > 0 for RECURRING schedules");
        }
        Instant effectiveStart = startAt != null ? startAt : Instant.now();

        ScenarioSchedule schedule = new ScenarioSchedule();
        schedule.setScenarioId(scenario.getId());
        schedule.setMode(normalizedMode);
        schedule.setStartAt(effectiveStart);
        schedule.setIntervalSeconds(MODE_RECURRING.equals(normalizedMode) ? intervalSeconds : null);
        schedule.setEndAt(endAt);
        schedule.setActive(true);
        schedule.setNextFireAt(effectiveStart);
        schedule.setFireCount(0);

        ScenarioSchedule saved = scheduleRepository.save(schedule);
        return toResponse(saved, scenario.getName());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedules(String actorRole) {
        requireAdminOrBanker(actorRole);
        List<ScenarioSchedule> schedules = scheduleRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> names = scenarioRepository.findAll().stream()
                .collect(Collectors.toMap(Scenario::getId, Scenario::getName));
        return schedules.stream()
                .map(s -> toResponse(s, names.get(s.getScenarioId())))
                .toList();
    }

    @Transactional
    public void cancelSchedule(String actorRole, Long scheduleId) {
        requireAdminOrBanker(actorRole);
        if (scheduleId == null) throw new BadRequestException("scheduleId is required");
        ScenarioSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException("Schedule not found: " + scheduleId));
        schedule.setActive(false);
        scheduleRepository.save(schedule);
        // Drop any still-pending steps that this schedule had queued.
        List<PendingStep> pending = pendingStepRepository.findByScheduleId(scheduleId).stream()
                .filter(p -> "PENDING".equals(p.getStatus()))
                .toList();
        if (!pending.isEmpty()) {
            pendingStepRepository.deleteAll(pending);
        }
    }

    /** Enqueue the scenario's steps starting now (honoring per-step delays). Returns step count. */
    @Transactional
    public int triggerNow(String actorRole, Long scenarioId) {
        requireAdminOrBanker(actorRole);
        Scenario scenario = getScenario(scenarioId);
        return enqueueScenarioSteps(scenario, null, Instant.now());
    }

    // ===================== Internal helpers (no role check) =====================

    /**
     * Reads the scenario's steps and queues one {@link PendingStep} per step. Each step's fireAt is
     * {@code baseTime + cumulative sum of delaySeconds} (null delay treated as 0). Steps whose fireAt
     * is already in the past are simply picked up on the next poll tick. Returns the number enqueued.
     */
    @Transactional
    public int enqueueScenarioSteps(Scenario scenario, Long scheduleId, Instant baseTime) {
        List<PriceStep> steps = readSteps(scenario.getStepsJson());
        long cumulativeSeconds = 0L;
        int enqueued = 0;
        for (PriceStep step : steps) {
            long delay = step.delaySeconds() == null ? 0L : step.delaySeconds();
            cumulativeSeconds += delay;
            Instant fireAt = baseTime.plusSeconds(cumulativeSeconds);

            PendingStep pending = new PendingStep();
            pending.setScenarioId(scenario.getId());
            pending.setScheduleId(scheduleId);
            pending.setStepJson(writeStep(step));
            pending.setFireAt(fireAt);
            pending.setStatus("PENDING");
            pendingStepRepository.save(pending);
            enqueued++;
        }
        return enqueued;
    }

    // ===================== Helpers =====================

    private Scenario getScenario(Long id) {
        if (id == null) throw new BadRequestException("scenarioId is required");
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scenario not found: " + id));
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw new BadRequestException("mode is required");
        }
        String upper = mode.trim().toUpperCase();
        if (!MODE_ONCE.equals(upper) && !MODE_RECURRING.equals(upper)) {
            throw new BadRequestException("Invalid schedule mode: " + mode + " (expected ONCE or RECURRING)");
        }
        return upper;
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

    private String writeStep(PriceStep step) {
        try {
            return objectMapper.writeValueAsString(step);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize price step", ex);
        }
    }

    private ScheduleResponse toResponse(ScenarioSchedule s, String scenarioName) {
        return ScheduleResponse.builder()
                .id(s.getId())
                .scenarioId(s.getScenarioId())
                .scenarioName(scenarioName)
                .mode(s.getMode())
                .startAt(s.getStartAt())
                .intervalSeconds(s.getIntervalSeconds())
                .endAt(s.getEndAt())
                .active(s.isActive())
                .nextFireAt(s.getNextFireAt())
                .lastFiredAt(s.getLastFiredAt())
                .fireCount(s.getFireCount())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private void requireAdminOrBanker(String role) {
        if (!ADMIN_BANKER.contains(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }
}
