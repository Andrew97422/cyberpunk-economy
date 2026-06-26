package ru.andrew.accessservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.accessservice.entity.GameSession;

import java.time.Instant;
import java.util.List;

/**
 * Periodically moves ACTIVE sessions whose expires_at has passed into EXPIRED,
 * and publishes session.expired events. The JWT filter on main-server already
 * treats expires_at as authoritative, but emitting the event keeps every
 * consumer's snapshot tidy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionExpirationScheduler {

    private final SessionService sessionService;
    private final SessionEventPublisher sessionEventPublisher;

    @Scheduled(fixedDelay = 30_000L)
    @Transactional
    public void sweep() {
        List<GameSession> expired = sessionService.expireOverdue(Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        for (GameSession session : expired) {
            sessionEventPublisher.publishClosed(session, "session.expired");
        }
        log.info("Expired {} session(s) by sweep", expired.size());
    }
}
