package ru.andrew.cardservice.command;

public enum CardCommandType {
    ISSUE_CARD,
    BLOCK_CARD,
    MARK_LOST,
    REPLACE_CARD,
    LIST_CARDS_BY_ACCOUNT,
    GET_CARD,
    LOOKUP_BY_UID,
    SCAN_CARD
}
