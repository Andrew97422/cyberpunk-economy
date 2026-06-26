package ru.andrew.cardservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CardResponse {
    private Long id;
    private Long accountId;
    private String publicName;
    private String accountRole;
    private String accountStatus;
    private String cardUid;
    private String status;
    private Instant issuedAt;
    private Long issuedByAccountId;
    private Instant blockedAt;
    private Long blockedByAccountId;
    private String blockedReason;
    private Long replacedByCardId;
    private String notes;
}
