package ru.andrew.mainserver.banking.entity;

public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER_IN,
    TRANSFER_OUT,
    ADJUSTMENT,
    REVERSAL_IN,
    REVERSAL_OUT
}