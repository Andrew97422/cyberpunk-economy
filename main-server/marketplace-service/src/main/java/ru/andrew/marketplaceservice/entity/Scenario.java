package ru.andrew.marketplaceservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A saved, reusable economic price scenario. {@link #stepsJson} holds a JSON array of
 * price-op steps (each step has the same shape as the MASS_PRICE_OP payload), applied in order.
 */
@Entity
@Table(name = "scenarios")
@Getter
@Setter
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "steps_json", nullable = false, columnDefinition = "text")
    private String stepsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (stepsJson == null) stepsJson = "[]";
    }
}
