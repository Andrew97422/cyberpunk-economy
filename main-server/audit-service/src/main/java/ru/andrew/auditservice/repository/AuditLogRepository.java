package ru.andrew.auditservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.andrew.auditservice.entity.AuditLog;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    boolean existsByEventId(String eventId);

    Page<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
