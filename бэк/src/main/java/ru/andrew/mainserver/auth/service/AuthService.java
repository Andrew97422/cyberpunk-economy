package ru.andrew.mainserver.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.service.AccountService;
import ru.andrew.mainserver.audit.entity.AuditEventType;
import ru.andrew.mainserver.audit.service.AuditService;
import ru.andrew.mainserver.auth.dto.AdminLoginRequest;
import ru.andrew.mainserver.auth.dto.AuthResponse;
import ru.andrew.mainserver.auth.dto.LogoutResponse;
import ru.andrew.mainserver.auth.dto.PlayerPinLoginRequest;
import ru.andrew.mainserver.auth.token.JwtService;
import ru.andrew.mainserver.common.exception.UnauthorizedException;
import ru.andrew.mainserver.config.SecurityProperties;
import ru.andrew.mainserver.pin.entity.PinCode;
import ru.andrew.mainserver.pin.service.PinCodeService;
import ru.andrew.mainserver.session.entity.GameSession;
import ru.andrew.mainserver.session.entity.SessionStatus;
import ru.andrew.mainserver.session.service.SessionService;
import ru.andrew.mainserver.terminal.entity.Terminal;
import ru.andrew.mainserver.terminal.service.TerminalService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountService accountService;
    private final PinCodeService pinCodeService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final TerminalService terminalService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final SecurityProperties securityProperties;

    @Transactional
    public AuthResponse adminLogin(AdminLoginRequest request, String terminalName) {
        Account account = accountService.getActiveByPublicName(request.getPublicName());
        accountService.requireAnyRole(account, Role.ADMIN, Role.BANKER, Role.DEVELOPER);

        Terminal terminal = terminalService.findByNameOrNull(terminalName);

        if (account.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            auditService.log(
                    AuditEventType.ADMIN_LOGIN_FAILED,
                    account.getId(),
                    "Account",
                    account.getId(),
                    terminal,
                    "Admin login failed",
                    null
            );
            throw new UnauthorizedException("Invalid credentials");
        }

        GameSession session = sessionService.findActiveSession(account.getId())
                .orElseGet(() -> sessionService.openServiceSession(
                        account,
                        (int) securityProperties.getAdminSessionExpirationSeconds() / 60,
                        terminal
                ));

        auditService.log(
                AuditEventType.ADMIN_LOGIN_SUCCESS,
                account.getId(),
                "Account",
                account.getId(),
                terminal,
                "Admin login success",
                null
        );

        return AuthResponse.builder()
                .token(jwtService.generate(account, session.getId(), securityProperties.getAdminSessionExpirationSeconds()))
                .accountId(account.getId())
                .publicName(account.getPublicName())
                .role(account.getRole().name())
                .sessionId(session.getId())
                .build();
    }

    @Transactional
    public AuthResponse playerLoginByPin(PlayerPinLoginRequest request, String terminalName) {
        Account account = accountService.getActiveByPublicName(request.getPublicName());
        accountService.requireAnyRole(account, Role.PLAYER, Role.DEVELOPER);

        Terminal terminal = terminalService.findByNameOrNull(terminalName);
        PinCode pinCode = pinCodeService.findLatestActivePin(account);

        if (pinCodeService.isExpired(pinCode)) {
            pinCodeService.markExpired(pinCode);
            auditService.log(
                    AuditEventType.PIN_EXPIRED,
                    account.getId(),
                    "PinCode",
                    pinCode.getId(),
                    terminal,
                    "PIN expired",
                    null
            );
            throw new UnauthorizedException("PIN expired");
        }

        if (!pinCodeService.matches(request.getPin(), pinCode.getPinHash())) {
            auditService.log(
                    AuditEventType.PLAYER_LOGIN_FAILED,
                    account.getId(),
                    "PinCode",
                    pinCode.getId(),
                    terminal,
                    "Player PIN login failed",
                    null
            );
            throw new UnauthorizedException("Invalid PIN");
        }

        pinCodeService.markUsed(pinCode);

        GameSession session = sessionService.openPlayerSession(account, pinCode, terminal);

        auditService.log(
                AuditEventType.PIN_USED,
                account.getId(),
                "PinCode",
                pinCode.getId(),
                terminal,
                "PIN used successfully",
                null
        );

        auditService.log(
                AuditEventType.PLAYER_LOGIN_SUCCESS,
                account.getId(),
                "GameSession",
                session.getId(),
                terminal,
                "Player login success",
                null
        );

        return AuthResponse.builder()
                .token(jwtService.generate(account, session.getId(), securityProperties.getPinSessionExpirationSeconds()))
                .accountId(account.getId())
                .publicName(account.getPublicName())
                .role(account.getRole().name())
                .sessionId(session.getId())
                .build();
    }

    @Transactional
    public LogoutResponse logoutCurrentSession() {
        var currentUser = currentUserService.getCurrentUser();

        GameSession session = sessionService.findById(currentUser.getSessionId())
                .orElseThrow(() -> new UnauthorizedException("Session not found"));

        if (session.getStatus() == SessionStatus.ACTIVE) {
            sessionService.logout(session);
        }

        auditService.log(
                AuditEventType.SESSION_LOGGED_OUT,
                currentUser.getAccountId(),
                "GameSession",
                session.getId(),
                session.getTerminal(),
                "Logout success",
                null
        );

        return LogoutResponse.builder()
                .success(true)
                .message("Logged out successfully")
                .sessionId(session.getId())
                .build();
    }
}