package ru.andrew.bankingservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A game-wide economic event applied to every (optionally role-filtered) account balance.
 * mode: CREDIT (+value), DEBIT (−value, floored at 0), MULTIPLY (×value, e.g. 0.9 = −10%).
 */
@Getter
@Setter
public class MassOperationRequest {
    private String mode;
    private String currencyType;
    private BigDecimal value;
    private String targetRole; // null/blank = all roles
    private String comment;
}
