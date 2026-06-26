package ru.andrew.auditservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_source", length = 100)
    private String eventSource;

    @Column(name = "aggregate_type", length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 100)
    private String aggregateId;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "actor_public_name", length = 255)
    private String actorPublicName;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "target_entity_type", length = 100)
    private String targetEntityType;

    @Column(name = "target_entity_id", length = 100)
    private String targetEntityId;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "terminal_name", length = 100)
    private String terminalName;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
    }
}
