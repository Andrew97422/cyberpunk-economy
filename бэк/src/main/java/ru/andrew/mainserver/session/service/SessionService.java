package ru.andrew.mainserver.session.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.common.exception.NotFoundException;
import ru.andrew.mainserver.pin.entity.PinCode;
import ru.andrew.mainserver.session.entity.GameSession;
import ru.andrew.mainserver.session.entity.SessionStatus;
import ru.andrew.mainserver.session.repository.GameSessionRepository;
import ru.andrew.mainserver.terminal.entity.Terminal;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final GameSessionRepository gameSessionRepository;

    @Transactional
    public GameSession openPlayerSession(Account account, PinCode pinCode, Terminal terminal) {
        GameSession session = new GameSession();
        session.setAccount(account);
        session.setPinCode(pinCode);
        session.setTerminal(terminal);
        session.setStartedAt(Instant.now());
        session.setLastSeenAt(Instant.now());
        session.setDurationMinutes(pinCode.getDurationMinutes());
        session.setStatus(SessionStatus.ACTIVE);
        return gameSessionRepository.save(session);
    }

    @Transactional
    public GameSession openServiceSession(Account account, int durationMinutes, Terminal terminal) {
        GameSession session = new GameSession();
        session.setAccount(account);
        session.setPinCode(null);
        session.setTerminal(terminal);
        session.setStartedAt(Instant.now());
        session.setLastSeenAt(Instant.now());
        session.setDurationMinutes(durationMinutes);
        session.setStatus(SessionStatus.ACTIVE);
        return gameSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<GameSession> findById(Long sessionId) {
        return gameSessionRepository.findById(sessionId);
    }

    @Transactional(readOnly = true)
    public GameSession getById(Long sessionId) {
        return gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    @Transactional(readOnly = true)
    public Optional<GameSession> findActiveSession(Long accountId) {
        return gameSessionRepository.findFirstByAccountIdAndStatusOrderByStartedAtDesc(accountId, SessionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Page<GameSession> getActiveSessions(Pageable pageable) {
        return gameSessionRepository.findAllByStatus(SessionStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public boolean isExpired(GameSession session) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            return true;
        }

        Instant baseTime = session.getLastSeenAt() != null
                ? session.getLastSeenAt()
                : session.getStartedAt();

        Instant expiresAt = baseTime.plusSeconds(session.getDurationMinutes() * 60L);
        return Instant.now().isAfter(expiresAt);
    }

    @Transactional
    public void touch(GameSession session) {
        Instant now = Instant.now();

        if (session.getLastSeenAt() == null || session.getLastSeenAt().isBefore(now.minusSeconds(30))) {
            session.setLastSeenAt(now);
            gameSessionRepository.save(session);
        }
    }

    @Transactional
    public void logout(GameSession session) {
        session.setStatus(SessionStatus.LOGGED_OUT);
        session.setEndedAt(Instant.now());
        gameSessionRepository.save(session);
    }

    @Transactional
    public void expire(GameSession session) {
        session.setStatus(SessionStatus.EXPIRED);
        session.setEndedAt(Instant.now());
        gameSessionRepository.save(session);
    }

    @Transactional
    public void terminate(GameSession session) {
        session.setStatus(SessionStatus.TERMINATED);
        session.setEndedAt(Instant.now());
        gameSessionRepository.save(session);
    }
}