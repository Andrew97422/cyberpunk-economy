package ru.andrew.accessservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.accessservice.dto.LoginResult;
import ru.andrew.accessservice.dto.PinResponse;
import ru.andrew.accessservice.dto.SessionResponse;
import ru.andrew.accessservice.dto.TerminateSessionResponse;
import ru.andrew.accessservice.entity.AccountSnapshot;
import ru.andrew.accessservice.entity.GameSession;
import ru.andrew.accessservice.entity.PinCode;
import ru.andrew.accessservice.exception.BadRequestException;

import java.util.List;
import java.util.Set;

/**
 * Top-level orchestrator for inbound commands.
 * Combines PIN + session lifecycle into the gestures the gateway exposes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessService {

    private static final Set<String> PLAYER_ROLES = Set.of("PLAYER", "DEVELOPER");
    private static final Set<String> SERVICE_ROLES = Set.of("ADMIN", "BANKER", "DEVELOPER");
    private static final Set<String> PIN_ISSUER_ROLES = Set.of("ADMIN", "BANKER");

    private final PinCodeService pinCodeService;
    private final SessionService sessionService;
    private final AccountSnapshotService accountSnapshotService;
    private final SessionEventPublisher sessionEventPublisher;

    @Transactional
    public PinResponse createPin(String actorRole, Long actorId, String targetPublicName,
                                 String rawPin, Integer durationMinutes, String comment) {
        if (!PIN_ISSUER_ROLES.contains(actorRole)) {
            throw new BadRequestException("Insufficient privileges to issue PIN");
        }
        return pinCodeService.create(targetPublicName, rawPin, durationMinutes, actorId, comment);
    }

    @Transactional
    public PinResponse revokePin(String actorRole, Long pinId) {
        if (!PIN_ISSUER_ROLES.contains(actorRole)) {
            throw new BadRequestException("Insufficient privileges to revoke PIN");
        }
        return pinCodeService.revoke(pinId);
    }

    @Transactional(readOnly = true)
    public List<PinResponse> listPinsByAccount(String actorRole, Long accountId) {
        if (!PIN_ISSUER_ROLES.contains(actorRole)) {
            throw new BadRequestException("Insufficient privileges to view PINs");
        }
        return pinCodeService.getAllByAccount(accountId);
    }

    /**
     * Player flow: PIN only, no publicName. Finds the unique active PIN matching the
     * raw value, verifies, marks it USED, opens a session whose lifetime equals the
     * PIN's durationMinutes (absolute from start).
     */
    @Transactional
    public LoginResult loginByPin(String rawPin, Long terminalId, String terminalName) {
        PinCode pinCode = pinCodeService.resolveAndConsume(rawPin);
        AccountSnapshot account = accountSnapshotService.getActiveById(pinCode.getAccountId());

        if (!PLAYER_ROLES.contains(account.getRole())) {
            throw new BadRequestException("Account role is not allowed for PIN login");
        }

        pinCodeService.markUsed(pinCode);
        GameSession session = sessionService.openPlayerSession(account, pinCode, terminalId, terminalName);
        sessionEventPublisher.publishOpened(session);

        return buildLoginResult(account, session);
    }

    /**
     * Admin / banker flow: publicName + PIN, plus an explicit duration governed by
     * the gateway (so the operator can dial admin session length independently
     * from the PIN's own validity window).
     */
    @Transactional
    public LoginResult openServiceSession(String publicName, String rawPin,
                                          int durationMinutes,
                                          Long terminalId, String terminalName) {
        AccountSnapshot account = accountSnapshotService.getActiveByPublicName(publicName);
        if (!SERVICE_ROLES.contains(account.getRole())) {
            throw new BadRequestException("Account role is not allowed for service login");
        }

        PinCode pinCode = pinCodeService.resolveForAccount(account.getId(), rawPin);
        pinCodeService.markUsed(pinCode);

        GameSession session = sessionService.openServiceSession(account, durationMinutes, terminalId, terminalName);
        sessionEventPublisher.publishOpened(session);

        return buildLoginResult(account, session);
    }

    /**
     * Gateway-trusted path: the caller (main-server) has already verified the
     * account out-of-band (e.g. via account-service VERIFY_CREDENTIALS). Used
     * for admin logins that don't carry a PIN.
     */
    @Transactional
    public LoginResult openVerifiedSession(Long accountId, int durationMinutes,
                                           Long terminalId, String terminalName) {
        AccountSnapshot account = accountSnapshotService.getActiveById(accountId);
        if (!SERVICE_ROLES.contains(account.getRole())) {
            throw new BadRequestException("Account role is not allowed for service session");
        }
        GameSession session = sessionService.openServiceSession(account, durationMinutes, terminalId, terminalName);
        sessionEventPublisher.publishOpened(session);
        return buildLoginResult(account, session);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(Long sessionId) {
        return sessionService.toResponse(sessionService.getById(sessionId));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SessionResponse> listActiveSessions(
            String actorRole, org.springframework.data.domain.Pageable pageable) {
        if (!PIN_ISSUER_ROLES.contains(actorRole)) {
            throw new BadRequestException("Insufficient privileges");
        }
        return sessionService.getActiveSessions(pageable);
    }

    @Transactional
    public TerminateSessionResponse terminateSession(String actorRole, Long sessionId) {
        if (!PIN_ISSUER_ROLES.contains(actorRole)) {
            throw new BadRequestException("Insufficient privileges");
        }
        GameSession session = sessionService.terminate(sessionId);
        sessionEventPublisher.publishClosed(session, "session.terminated");
        return TerminateSessionResponse.builder()
                .success(true)
                .sessionId(session.getId())
                .status(session.getStatus().name())
                .message("Session terminated")
                .build();
    }

    @Transactional
    public TerminateSessionResponse logoutSession(Long sessionId) {
        GameSession session = sessionService.logout(sessionId);
        sessionEventPublisher.publishClosed(session, "session.logged_out");
        return TerminateSessionResponse.builder()
                .success(true)
                .sessionId(session.getId())
                .status(session.getStatus().name())
                .message("Logged out")
                .build();
    }

    private LoginResult buildLoginResult(AccountSnapshot account, GameSession session) {
        return LoginResult.builder()
                .session(sessionService.toResponse(session))
                .accountId(account.getId())
                .publicName(account.getPublicName())
                .role(account.getRole())
                .build();
    }
}
