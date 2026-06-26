package ru.andrew.marketplaceservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.marketplaceservice.dto.PriceStep;
import ru.andrew.marketplaceservice.entity.PendingStep;
import ru.andrew.marketplaceservice.entity.Scenario;
import ru.andrew.marketplaceservice.entity.ScenarioSchedule;
import ru.andrew.marketplaceservice.repository.PendingStepRepository;
import ru.andrew.marketplaceservice.repository.ScenarioRepository;
import ru.andrew.marketplaceservice.repository.ScenarioScheduleRepository;

import java.time.Instant;
import java.util.List;

/**
 * Background poller. Every 15s it (1) materializes due schedules into pending steps and (2) executes
 * pending steps whose fire time has passed. All exceptions are caught and logged so a single bad
 * row never breaks the poll loop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioSchedulerPoller {

    private static final String MODE_RECURRING = "RECURRING";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";

    private final ScenarioRepository scenarioRepository;
    private final ScenarioScheduleRepository scheduleRepository;
    private final PendingStepRepository pendingStepRepository;
    private final ScenarioScheduleService scheduleService;
    private final PriceScenarioService priceScenarioService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 15000)
    public void pollSchedules() {
        try {
            Instant now = Instant.now();
            List<ScenarioSchedule> due = scheduleRepository.findByActiveTrueAndNextFireAtLessThanEqual(now);
            for (ScenarioSchedule schedule : due) {
                try {
                    fireSchedule(schedule, now);
                } catch (Exception ex) {
                    log.error("Failed to fire schedule id={}", schedule.getId(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("pollSchedules failed", ex);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void pollPendingSteps() {
        try {
            Instant now = Instant.now();
            List<PendingStep> due = pendingStepRepository.findByStatusAndFireAtLessThanEqual(STATUS_PENDING, now);
            for (PendingStep pending : due) {
                executePendingStep(pending);
            }
        } catch (Exception ex) {
            log.error("pollPendingSteps failed", ex);
        }
    }

    @Transactional
    public void fireSchedule(ScenarioSchedule schedule, Instant now) {
        Scenario scenario = scenarioRepository.findById(schedule.getScenarioId()).orElse(null);
        if (scenario == null) {
            log.warn("Schedule id={} references missing scenario id={}; deactivating",
                    schedule.getId(), schedule.getScenarioId());
            schedule.setActive(false);
            scheduleRepository.save(schedule);
            return;
        }
        scheduleService.enqueueScenarioSteps(scenario, schedule.getId(), now);
        schedule.setFireCount(schedule.getFireCount() + 1);
        schedule.setLastFiredAt(now);

        if (MODE_RECURRING.equals(schedule.getMode())) {
            long interval = schedule.getIntervalSeconds() == null ? 0L : schedule.getIntervalSeconds();
            Instant base = schedule.getNextFireAt() != null ? schedule.getNextFireAt() : now;
            Instant next = base.plusSeconds(interval);
            schedule.setNextFireAt(next);
            if (interval <= 0 || (schedule.getEndAt() != null && next.isAfter(schedule.getEndAt()))) {
                schedule.setActive(false);
            }
        } else {
            // ONCE
            schedule.setActive(false);
        }
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void executePendingStep(PendingStep pending) {
        try {
            PriceStep step = objectMapper.readValue(pending.getStepJson(), PriceStep.class);
            priceScenarioService.applySingleStep(step);
            pending.setStatus(STATUS_DONE);
        } catch (Exception ex) {
            log.error("Failed to execute pending step id={}", pending.getId(), ex);
            pending.setStatus(STATUS_FAILED);
        }
        pending.setExecutedAt(Instant.now());
        pendingStepRepository.save(pending);
    }
}
