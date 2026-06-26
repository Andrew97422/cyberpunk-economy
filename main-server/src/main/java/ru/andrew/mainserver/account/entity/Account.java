package ru.andrew.mainserver.account.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.mainserver.common.entity.BaseEntity;

/**
 * Local read-model of an account.
 *
 * Authoritative storage lives in account-service. This row is populated by
 * {@code AccountSyncListener} consuming {@code account.events} (and an
 * eager upsert by the gateway on login / write commands).
 *
 * Other modules (sessions, pins, cards, audit) keep their JPA relations
 * pointing here so FK constraints continue to work locally.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account extends BaseEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "public_name", nullable = false, length = 255)
    private String publicName;

    @Column(name = "character_name", length = 255)
    private String characterName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AccountStatus status;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
