package ru.andrew.mainserver.gateway.access;

public enum AccessCommandType {
    CREATE_PIN,
    REVOKE_PIN,
    LIST_PINS_BY_ACCOUNT,
    LOGIN_BY_PIN,
    OPEN_SERVICE_SESSION,
    OPEN_VERIFIED_SESSION,
    GET_SESSION,
    LIST_ACTIVE_SESSIONS,
    TERMINATE_SESSION,
    LOGOUT_SESSION
}
