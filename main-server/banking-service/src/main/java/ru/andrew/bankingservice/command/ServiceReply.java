package ru.andrew.bankingservice.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceReply(
        boolean success,
        int status,
        String errorMessage,
        Object result
) {
    public static ServiceReply ok(Object result) {
        return new ServiceReply(true, 200, null, result);
    }

    public static ServiceReply error(int status, String message) {
        return new ServiceReply(false, status, message, null);
    }
}
