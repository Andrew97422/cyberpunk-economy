package ru.andrew.mainserver.account.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.mainserver.common.entity.BaseEntity;

@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}