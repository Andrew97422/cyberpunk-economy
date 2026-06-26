package ru.andrew.mainserver.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.service.AccountService;
import ru.andrew.mainserver.account.sync.AccountSyncService;
import ru.andrew.mainserver.auth.dto.AdminLoginRequest;
import ru.andrew.mainserver.auth.dto.AuthResponse;
import ru.andrew.mainserver.auth.dto.LogoutResponse;
import ru.andrew.mainserver.auth.dto.PlayerPinLoginRequest;
import ru.andrew.mainserver.auth.event.AuthEventPublisher;
import ru.andrew.mainserver.auth.token.JwtService;
import ru.andrew.mainserver.common.exception.UnauthorizedException;
import ru.andrew.mainserver.config.SecurityProperties;
import ru.andrew.mainserver.gateway.access.AccessGatewayService;
import ru.andrew.mainserver.gateway.access.LoginResult;
import ru.andrew.mainserver.gateway.access.SessionInfo;
import ru.andrew.mainserver.gateway.accounts.AccountGatewayService;
import ru.andrew.mainserver.gateway.accounts.AccountInfo;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountService accountService;
    private final AccountGatewayService accountGatewayService;
    private final AccountSyncService accountSyncService;
    private final AccessGatewayService accessGatewayService;
    private final AuthEventPublisher authEventPublisher;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final SecurityProperties securityProperties;

    @Transactional
    public AuthResponse adminLogin(AdminLoginRequest request, String terminalName) {
        AccountInfo info = accountGatewayService.verifyCredentials(request.getPublicName(), request.getPassword())
                .orElseThrow(() -> {
                    authEventPublisher.publishAdminLoginFailed(
                            request.getPublicName(), terminalName, "Invalid credentials");
                    return new UnauthorizedException("Invalid credentials");
                });

        accountSyncService.upsert(info.id(), info.publicName(), info.role(), info.status(), null, null);
        Account account = accountService.getById(info.id());

        int durationMinutes = (int) Math.max(1, securityProperties.getAdminSessionExpirationSeconds() / 60);
        LoginResult login = accessGatewayService.openVerifiedSession(account.getId(), durationMinutes, terminalName);
        SessionInfo session = login.session();

        authEventPublisher.publishAdminLoginSuccess(
                account.getId(), account.getPublicName(), account.getRole().name(),
                terminalName, session.id());

        long ttlSeconds = computeTtlSeconds(session, securityProperties.getAdminSessionExpirationSeconds());
        return AuthResponse.builder()
                .token(jwtService.generate(account, session.id(), ttlSeconds))
                .accountId(account.getId())
                .publicName(account.getPublicName())
                .role(account.getRole().name())
                .sessionId(session.id())
                .build();
    }

    @Transactional
    public AuthResponse playerLoginByPin(PlayerPinLoginRequest request, String terminalName) {
        LoginResult login;
        try {
            login = accessGatewayService.loginByPin(request.getPin(), terminalName);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            authEventPublisher.publishPlayerLoginFailed(terminalName,
                    ex.getReason() == null ? "Invalid PIN" : ex.getReason());
            throw new UnauthorizedException(ex.getReason() == null ? "Invalid PIN" : ex.getReason());
        }

        SessionInfo session = login.session();
        Long accountId = login.accountId();

        accountSyncService.upsert(accountId, login.publicName(), login.role(), "ACTIVE", null, null);
        Account account = accountService.getById(accountId);

        authEventPublisher.publishPlayerLoginSuccess(
                accountId, login.publicName(), login.role(), terminalName, session.id());

        long ttlSeconds = computeTtlSeconds(session, securityProperties.getPinSessionExpirationSeconds());
        return AuthResponse.builder()
                .token(jwtService.generate(account, session.id(), ttlSeconds))
                .accountId(account.getId())
                .publicName(account.getPublicName())
                .role(account.getRole().name())
                .sessionId(session.id())
                .build();
    }

    @Transactional
    public LogoutResponse logoutCurrentSession() {
        var currentUser = currentUserService.getCurrentUser();
        Long sessionId = currentUser.getSessionId();

        accessGatewayService.logoutSession(currentUser, sessionId);
        authEventPublisher.publishLogout(currentUser.getAccountId(), currentUser.getPublicName(), sessionId);

        return LogoutResponse.builder()
                .success(true)
                .message("Logged out successfully")
                .sessionId(sessionId)
                .build();
    }

    private long computeTtlSeconds(SessionInfo session, long fallbackSeconds) {
        if (session != null && session.expiresAt() != null && session.startedAt() != null) {
            long seconds = session.expiresAt().getEpochSecond() - session.startedAt().getEpochSecond();
            if (seconds > 0) return seconds;
        }
        return fallbackSeconds;
    }
}
