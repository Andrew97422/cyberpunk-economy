package ru.andrew.mainserver.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.audit.entity.AuditEventType;
import ru.andrew.mainserver.audit.entity.AuditLog;
import ru.andrew.mainserver.audit.repository.AuditLogRepository;
import ru.andrew.mainserver.terminal.entity.Terminal;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog log(AuditEventType eventType,
                        Long actorAccountId,
                        String targetEntityType,
                        Long targetEntityId,
                        Terminal terminal,
                        String message,
                        String metadataJson) {

        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setActorAccountId(actorAccountId);
        log.setTargetEntityType(targetEntityType);
        log.setTargetEntityId(targetEntityId);
        log.setTerminal(terminal);
        log.setMessage(message);
        log.setMetadataJson(metadataJson);

        return auditLogRepository.save(log);
    }
}