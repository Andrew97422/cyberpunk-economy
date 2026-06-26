package ru.andrew.analyticsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One row per account event (created / role_changed / status_changed). */
@Entity
@Table(name = "account_fact", indexes = {
        @Index(name = "idx_af_account", columnList = "accountId"),
        @Index(name = "idx_af_occurred", columnList = "occurredAt")
})
@Getter
@Setter
public class AccountFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String eventId;

    private Instant occurredAt;
    private String eventType;
    private Long accountId;
    private String publicName;
    private String role;
    private String status;
}
