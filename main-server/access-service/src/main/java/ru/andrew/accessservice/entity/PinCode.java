package ru.andrew.accessservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.andrew.accessservice.common.entity.BaseEntity;

import java.time.Instant;

@Entity
@Table(name = "pin_codes")
@Getter
@Setter
public class PinCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "pin_hash", nullable = false, length = 255)
    private String pinHash;

    @Column(name = "pin_lookup_hash", nullable = false, length = 64)
    private String pinLookupHash;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PinCodeStatus status;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_by_account_id")
    private Long createdByAccountId;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;
}
