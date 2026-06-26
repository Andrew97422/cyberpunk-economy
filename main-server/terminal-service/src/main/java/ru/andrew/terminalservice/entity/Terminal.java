package ru.andrew.terminalservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "terminals")
@Getter
@Setter
public class Terminal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "terminal_type", length = 50)
    private String terminalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TerminalStatus status;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = TerminalStatus.ACTIVE;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
