package ru.andrew.auditservice.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.auditservice.dto.AuditLogFilter;
import ru.andrew.auditservice.dto.AuditLogResponse;
import ru.andrew.auditservice.entity.AuditLog;
import ru.andrew.auditservice.exception.NotFoundException;
import ru.andrew.auditservice.exception.UnauthorizedException;
import ru.andrew.auditservice.repository.AuditLogRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository repository;

    @Value("${app.hack.token:}")
    private String hackToken;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditLogFilter filter, int page, int size, boolean revealPayload) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Specification<AuditLog> spec = buildSpec(filter);
        return repository.findAll(spec, pageRequest).map(log -> toResponse(log, revealPayload));
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getById(Long id, boolean revealPayload) {
        AuditLog log = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Audit log not found: " + id));
        return toResponse(log, revealPayload);
    }

    /**
     * "Флешка Бога" path: any caller who knows the hack token can read the raw
     * payloads. The token is configured via env, so finding it on the playfield
     * (USB / NFC tag / etc.) gives a hacker full read access.
     */
    public boolean isHackerToken(String providedToken) {
        if (hackToken == null || hackToken.isBlank()) return false;
        return providedToken != null && hackToken.equals(providedToken);
    }

    public void requireHackerToken(String providedToken) {
        if (!isHackerToken(providedToken)) {
            throw new UnauthorizedException("Invalid hack token");
        }
    }

    private Specification<AuditLog> buildSpec(AuditLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null) return cb.conjunction();

            if (filter.eventType() != null && !filter.eventType().isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), filter.eventType()));
            }
            if (filter.eventSource() != null && !filter.eventSource().isBlank()) {
                predicates.add(cb.equal(root.get("eventSource"), filter.eventSource()));
            }
            if (filter.actorAccountId() != null) {
                predicates.add(cb.equal(root.get("actorAccountId"), filter.actorAccountId()));
            }
            if (filter.aggregateType() != null && !filter.aggregateType().isBlank()) {
                predicates.add(cb.equal(root.get("aggregateType"), filter.aggregateType()));
            }
            if (filter.aggregateId() != null && !filter.aggregateId().isBlank()) {
                predicates.add(cb.equal(root.get("aggregateId"), filter.aggregateId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), filter.to()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String like = "%" + filter.search().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("message")), like),
                        cb.like(cb.lower(root.get("actorPublicName")), like),
                        cb.like(cb.lower(root.get("eventType")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogResponse toResponse(AuditLog log, boolean revealPayload) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .eventId(log.getEventId())
                .eventType(log.getEventType())
                .eventSource(log.getEventSource())
                .aggregateType(log.getAggregateType())
                .aggregateId(log.getAggregateId())
                .actorAccountId(log.getActorAccountId())
                .actorPublicName(log.getActorPublicName())
                .actorRole(log.getActorRole())
                .targetEntityType(log.getTargetEntityType())
                .targetEntityId(log.getTargetEntityId())
                .terminalId(log.getTerminalId())
                .terminalName(log.getTerminalName())
                .message(log.getMessage())
                .payloadJson(revealPayload ? log.getPayloadJson() : null)
                .occurredAt(log.getOccurredAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
