package ru.andrew.mainserver.session.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.service.AccountService;
import ru.andrew.mainserver.audit.entity.AuditEventType;
import ru.andrew.mainserver.audit.service.AuditService;
import ru.andrew.mainserver.auth.service.CurrentUserService;
import ru.andrew.mainserver.session.dto.SessionResponse;
import ru.andrew.mainserver.session.dto.TerminateSessionResponse;
import ru.andrew.mainserver.session.entity.GameSession;

@Service
@RequiredArgsConstructor
public class SessionManagementService {

    private final SessionService sessionService;
    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public SessionResponse getCurrentSession() {
        Long sessionId = currentUserService.getCurrentSessionId();
        GameSession session = sessionService.getById(sessionId);
        return map(session);
    }

    @Transactional(readOnly = true)
    public Page<SessionResponse> getActiveSessions(Pageable pageable) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        return sessionService.getActiveSessions(pageable).map(this::map);
    }

    @Transactional
    public TerminateSessionResponse terminateSession(Long sessionId) {
        Account actor = accountService.getById(currentUserService.getCurrentAccountId());
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        GameSession session = sessionService.getById(sessionId);
        sessionService.terminate(session);

        auditService.log(
                AuditEventType.SESSION_TERMINATED,
                actor.getId(),
                "GameSession",
                session.getId(),
                session.getTerminal(),
                "Session terminated by admin/banker",
                null
        );

        return TerminateSessionResponse.builder()
                .success(true)
                .sessionId(session.getId())
                .status(session.getStatus().name())
                .message("Session terminated")
                .build();
    }

    private SessionResponse map(GameSession session) {
        return SessionResponse.builder()
                .id(session.getId())
                .accountId(session.getAccount().getId())
                .publicName(session.getAccount().getPublicName())
                .role(session.getAccount().getRole().name())
                .pinCodeId(session.getPinCode() != null ? session.getPinCode().getId() : null)
                .terminalName(session.getTerminal() != null ? session.getTerminal().getName() : null)
                .status(session.getStatus().name())
                .durationMinutes(session.getDurationMinutes())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .lastSeenAt(session.getLastSeenAt())
                .build();
    }
}