package ru.andrew.bankingservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Debit of the buyer's own account in a marketplace-purchase context.
 * The account charged is always the command actor (the buyer), so no target
 * publicName is carried — only the currency, amount and an optional comment.
 */
@Getter
@Setter
public class PurchaseChargeRequest {
    @NotBlank
    private String currencyType;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    private String comment;
}
