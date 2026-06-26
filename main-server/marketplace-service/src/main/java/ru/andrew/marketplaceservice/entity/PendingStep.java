package ru.andrew.marketplaceservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A single scenario step queued for execution at {@link #fireAt}. The poller picks up PENDING rows
 * whose {@link #fireAt} has passed, applies the step, and marks them DONE or FAILED.
 */
@Entity
@Table(name = "pending_steps")
@Getter
@Setter
public class PendingStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    /** A single PriceStep serialized as JSON. */
    @Column(name = "step_json", columnDefinition = "text", nullable = false)
    private String stepJson;

    @Column(name = "fire_at", nullable = false)
    private Instant fireAt;

    /** PENDING, DONE or FAILED. */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "PENDING";
    }
}
