package ru.andrew.mainserver.session.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.common.entity.BaseEntity;
import ru.andrew.mainserver.pin.entity.PinCode;
import ru.andrew.mainserver.terminal.entity.Terminal;

import java.time.Instant;

@Entity
@Table(name = "game_sessions")
@Getter
@Setter
public class GameSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pin_code_id", unique = true)
    private PinCode pinCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terminal_id")
    private Terminal terminal;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SessionStatus status;
}