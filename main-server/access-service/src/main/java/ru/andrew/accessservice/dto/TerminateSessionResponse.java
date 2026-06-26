package ru.andrew.accessservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TerminateSessionResponse {
    private boolean success;
    private Long sessionId;
    private String status;
    private String message;
}
