package ru.andrew.mainserver.card.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.common.entity.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "card_bindings")
@Getter
@Setter
public class CardBinding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(name = "card_uid", nullable = false, unique = true, length = 255)
    private String cardUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CardStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "issued_by_account_id")
    private Long issuedByAccountId;
}