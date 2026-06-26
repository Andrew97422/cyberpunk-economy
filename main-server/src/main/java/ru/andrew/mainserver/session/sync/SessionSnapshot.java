package ru.andrew.mainserver.session.sync;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "session_snapshot")
@Getter
@Setter
public class SessionSnapshot {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "public_name", nullable = false, length = 255)
    private String publicName;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "session_type", nullable = false, length = 50)
    private String sessionType;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = Instant.now();
    }
}
