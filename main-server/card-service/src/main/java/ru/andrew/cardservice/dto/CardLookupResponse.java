package ru.andrew.cardservice.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * What a terminal / banking service sees when a card is scanned: just enough
 * to identify the owner and decide what to do next (charge, open zone, etc.).
 */
@Getter
@Builder
public class CardLookupResponse {
    private Long cardId;
    private String cardUid;
    private String cardStatus;
    private Long accountId;
    private String publicName;
    private String accountRole;
    private String accountStatus;
}
