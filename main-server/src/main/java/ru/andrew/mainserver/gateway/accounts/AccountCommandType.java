package ru.andrew.mainserver.gateway.accounts;

public enum AccountCommandType {
    GET_ME,
    GET_ACCOUNT,
    GET_ACCOUNT_BY_PUBLIC_NAME,
    GET_ACTIVE_BY_PUBLIC_NAME,
    LIST_ACCOUNTS,
    CREATE_ACCOUNT,
    UPDATE_STATUS,
    UPDATE_ROLE,
    VERIFY_CREDENTIALS
}
