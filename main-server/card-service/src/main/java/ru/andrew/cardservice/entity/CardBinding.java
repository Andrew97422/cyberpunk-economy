package ru.andrew.cardservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "card_bindings")
@Getter
@Setter
public class CardBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "card_uid", nullable = false, unique = true, length = 255)
    private String cardUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CardStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "issued_by_account_id")
    private Long issuedByAccountId;

    @Column(name = "blocked_at")
    private Instant blockedAt;

    @Column(name = "blocked_by_account_id")
    private Long blockedByAccountId;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    @Column(name = "replaced_by_card_id")
    private Long replacedByCardId;

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
        if (issuedAt == null) issuedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
