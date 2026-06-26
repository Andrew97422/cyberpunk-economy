package ru.andrew.mainserver.session.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class SessionSnapshotService {

    private final SessionSnapshotRepository repository;

    /**
     * Self-reference (through the Spring proxy) so {@link #doUpsert} runs in its own
     * {@code REQUIRES_NEW} transaction even when called from another method of this bean.
     */
    @Autowired
    @Lazy
    private SessionSnapshotService self;

    public SessionSnapshotService(SessionSnapshotRepository repository) {
        this.repository = repository;
    }

    /**
     * Idempotent insert-or-update keyed by session id. The same snapshot is written by
     * both the synchronous login path and the asynchronous {@code session.events} listener,
     * so two concurrent first-time writes can race on the primary key. We let one win and
     * convert the loser's duplicate-key failure into an update on a retry.
     */
    public SessionSnapshot upsert(Long id, Long accountId, String publicName, String role,
                                  String sessionType, String status,
                                  Instant startedAt, Instant expiresAt, Instant endedAt) {
        if (id == null) {
            log.warn("Skip session snapshot upsert: id is null");
            return null;
        }
        try {
            return self.doUpsert(id, accountId, publicName, role, sessionType, status, startedAt, expiresAt, endedAt);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent writer inserted this id first — retry; the row now exists so it becomes an update.
            return self.doUpsert(id, accountId, publicName, role, sessionType, status, startedAt, expiresAt, endedAt);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SessionSnapshot doUpsert(Long id, Long accountId, String publicName, String role,
                                    String sessionType, String status,
                                    Instant startedAt, Instant expiresAt, Instant endedAt) {
        SessionSnapshot snapshot = repository.findById(id).orElseGet(() -> {
            SessionSnapshot fresh = new SessionSnapshot();
            fresh.setId(id);
            return fresh;
        });
        if (accountId != null) snapshot.setAccountId(accountId);
        if (publicName != null) snapshot.setPublicName(publicName);
        if (role != null) snapshot.setRole(role);
        if (sessionType != null) snapshot.setSessionType(sessionType);
        if (status != null) snapshot.setStatus(status);
        if (startedAt != null) snapshot.setStartedAt(startedAt);
        if (expiresAt != null) snapshot.setExpiresAt(expiresAt);
        if (endedAt != null) snapshot.setEndedAt(endedAt);
        // Flush inside this REQUIRES_NEW tx so a unique-violation surfaces here (catchable) rather than at commit.
        return repository.saveAndFlush(snapshot);
    }

    public Optional<SessionSnapshot> findById(Long id) {
        return repository.findById(id);
    }
}
