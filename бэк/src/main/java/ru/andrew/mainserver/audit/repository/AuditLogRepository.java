package ru.andrew.mainserver.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.mainserver.audit.entity.AuditEventType;
import ru.andrew.mainserver.audit.entity.AuditLog;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByEventTypeOrderByCreatedAtDesc(AuditEventType eventType);
    List<AuditLog> findAllByActorAccountIdOrderByCreatedAtDesc(Long actorAccountId);
}