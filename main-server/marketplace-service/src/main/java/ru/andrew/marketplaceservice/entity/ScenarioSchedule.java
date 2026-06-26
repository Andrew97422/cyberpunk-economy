package ru.andrew.marketplaceservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A schedule that fires a {@link Scenario} either once or repeatedly. The poller scans for active
 * schedules whose {@link #nextFireAt} has passed and enqueues the scenario's steps as pending steps.
 */
@Entity
@Table(name = "scenario_schedules")
@Getter
@Setter
public class ScenarioSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    /** ONCE or RECURRING. */
    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "interval_seconds")
    private Long intervalSeconds;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "next_fire_at")
    private Instant nextFireAt;

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    @Column(name = "fire_count", nullable = false)
    private int fireCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        // primitive boolean defaults to false; default a freshly-created schedule to active.
        // (Callers that explicitly cancel set active=false before persisting an existing row.)
        active = true;
        if (fireCount < 0) fireCount = 0;
    }
}
