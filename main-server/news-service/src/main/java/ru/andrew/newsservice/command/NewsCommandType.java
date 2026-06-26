package ru.andrew.newsservice.command;

public enum NewsCommandType {
    // Management (ADMIN/BANKER)
    CREATE_POST,
    UPDATE_POST,
    CHANGE_STATUS,
    SET_PINNED,
    // Read
    LIST_POSTS,
    GET_POST
}
