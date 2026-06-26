package ru.andrew.auditservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AuditLogResponse {
    private Long id;
    private String eventId;
    private String eventType;
    private String eventSource;
    private String aggregateType;
    private String aggregateId;
    private Long actorAccountId;
    private String actorPublicName;
    private String actorRole;
    private String targetEntityType;
    private String targetEntityId;
    private Long terminalId;
    private String terminalName;
    private String message;
    private String payloadJson; // populated only for "hacker" reads
    private Instant occurredAt;
    private Instant createdAt;
}
