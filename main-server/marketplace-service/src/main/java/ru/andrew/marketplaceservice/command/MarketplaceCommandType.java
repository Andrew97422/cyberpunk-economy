package ru.andrew.marketplaceservice.command;

public enum MarketplaceCommandType {
    // Catalog management (ADMIN/BANKER)
    CREATE_PRODUCT,
    UPDATE_PRODUCT,
    CHANGE_PRODUCT_STATUS,
    ADJUST_STOCK,
    // Catalog browse
    LIST_PRODUCTS,
    GET_PRODUCT,
    // Purchase saga (orchestrated by the gateway)
    RESERVE_ORDER,
    CONFIRM_ORDER,
    CANCEL_ORDER,
    // Orders / inventory
    LIST_ORDERS,
    LIST_MY_ORDERS,
    GET_ORDER,
    // Price scenarios (ADMIN/BANKER)
    MASS_PRICE_OP,
    LIST_SCENARIOS,
    CREATE_SCENARIO,
    UPDATE_SCENARIO,
    DELETE_SCENARIO,
    APPLY_SCENARIO,
    // Scenario scheduling (ADMIN/BANKER)
    SCHEDULE_SCENARIO,
    LIST_SCHEDULES,
    CANCEL_SCHEDULE,
    TRIGGER_SCENARIO_NOW
}
