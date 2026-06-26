package ru.andrew.mainserver.audit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.mainserver.common.entity.BaseEntity;
import ru.andrew.mainserver.terminal.entity.Terminal;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private AuditEventType eventType;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "target_entity_type", length = 100)
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private Long targetEntityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terminal_id")
    private Terminal terminal;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;
}