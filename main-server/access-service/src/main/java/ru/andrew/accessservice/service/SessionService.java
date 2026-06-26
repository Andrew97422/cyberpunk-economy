package ru.andrew.accessservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.accessservice.dto.SessionResponse;
import ru.andrew.accessservice.entity.AccountSnapshot;
import ru.andrew.accessservice.entity.GameSession;
import ru.andrew.accessservice.entity.PinCode;
import ru.andrew.accessservice.entity.SessionStatus;
import ru.andrew.accessservice.entity.SessionType;
import ru.andrew.accessservice.exception.NotFoundException;
import ru.andrew.accessservice.repository.GameSessionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final GameSessionRepository gameSessionRepository;

    @Transactional
    public GameSession openPlayerSession(AccountSnapshot account, PinCode pinCode,
                                         Long terminalId, String terminalName) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(pinCode.getDurationMinutes() * 60L);

        GameSession session = new GameSession();
        session.setAccountId(account.getId());
        session.setAccountRole(account.getRole());
        session.setPublicName(account.getPublicName());
        session.setPinCodeId(pinCode.getId());
        session.setTerminalId(terminalId);
        session.setTerminalName(terminalName);
        session.setSessionType(SessionType.PLAYER);
        session.setDurationMinutes(pinCode.getDurationMinutes());
        session.setStartedAt(now);
        session.setExpiresAt(expiresAt);
        session.setStatus(SessionStatus.ACTIVE);
        return gameSessionRepository.save(session);
    }

    @Transactional
    public GameSession openServiceSession(AccountSnapshot account, int durationMinutes,
                                          Long terminalId, String terminalName) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(durationMinutes * 60L);

        GameSession session = new GameSession();
        session.setAccountId(account.getId());
        session.setAccountRole(account.getRole());
        session.setPublicName(account.getPublicName());
        session.setPinCodeId(null);
        session.setTerminalId(terminalId);
        session.setTerminalName(terminalName);
        session.setSessionType(SessionType.SERVICE);
        session.setDurationMinutes(durationMinutes);
        session.setStartedAt(now);
        session.setExpiresAt(expiresAt);
        session.setStatus(SessionStatus.ACTIVE);
        return gameSessionRepository.save(session);
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
    public Page<SessionResponse> getActiveSessions(Pageable pageable) {
        return gameSessionRepository.findAllByStatus(SessionStatus.ACTIVE, pageable).map(this::toResponse);
    }

    @Transactional
    public GameSession logout(Long sessionId) {
        GameSession session = getById(sessionId);
        if (session.getStatus() == SessionStatus.ACTIVE) {
            session.setStatus(SessionStatus.LOGGED_OUT);
            session.setEndedAt(Instant.now());
            return gameSessionRepository.save(session);
        }
        return session;
    }

    @Transactional
    public GameSession terminate(Long sessionId) {
        GameSession session = getById(sessionId);
        if (session.getStatus() == SessionStatus.ACTIVE) {
            session.setStatus(SessionStatus.TERMINATED);
            session.setEndedAt(Instant.now());
            return gameSessionRepository.save(session);
        }
        return session;
    }

    @Transactional
    public List<GameSession> expireOverdue(Instant cutoff) {
        List<GameSession> overdue = gameSessionRepository.findAllByStatusAndExpiresAtBefore(SessionStatus.ACTIVE, cutoff);
        for (GameSession session : overdue) {
            session.setStatus(SessionStatus.EXPIRED);
            session.setEndedAt(cutoff);
            gameSessionRepository.save(session);
        }
        return overdue;
    }

    public SessionResponse toResponse(GameSession session) {
        return SessionResponse.builder()
                .id(session.getId())
                .accountId(session.getAccountId())
                .publicName(session.getPublicName())
                .role(session.getAccountRole())
                .sessionType(session.getSessionType().name())
                .pinCodeId(session.getPinCodeId())
                .terminalId(session.getTerminalId())
                .terminalName(session.getTerminalName())
                .status(session.getStatus().name())
                .durationMinutes(session.getDurationMinutes())
                .startedAt(session.getStartedAt())
                .expiresAt(session.getExpiresAt())
                .endedAt(session.getEndedAt())
                .build();
    }
}
