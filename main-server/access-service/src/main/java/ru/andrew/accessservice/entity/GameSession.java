package ru.andrew.accessservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.accessservice.common.entity.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "game_sessions")
@Getter
@Setter
public class GameSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_role", nullable = false, length = 50)
    private String accountRole;

    @Column(name = "public_name", nullable = false, length = 255)
    private String publicName;

    @Column(name = "pin_code_id")
    private Long pinCodeId;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "terminal_name", length = 100)
    private String terminalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 50)
    private SessionType sessionType;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SessionStatus status;
}
