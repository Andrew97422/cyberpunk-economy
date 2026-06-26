package ru.andrew.mainserver.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.dto.*;
import ru.andrew.mainserver.auth.service.AuthService;
import ru.andrew.mainserver.auth.service.CurrentUserService;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    @PostMapping("/admin/login")
    public AuthResponse adminLogin(@Valid @RequestBody AdminLoginRequest request,
                                   HttpServletRequest httpServletRequest) {
        String terminalName = httpServletRequest.getHeader("X-Terminal-Name");
        return authService.adminLogin(request, terminalName);
    }

    @PostMapping("/player/login")
    public AuthResponse playerLogin(@Valid @RequestBody PlayerPinLoginRequest request,
                                    HttpServletRequest httpServletRequest) {
        String terminalName = httpServletRequest.getHeader("X-Terminal-Name");
        return authService.playerLoginByPin(request, terminalName);
    }

    @GetMapping("/me")
    public MeResponse me() {
        AuthenticatedUser user = currentUserService.getCurrentUser();
        return MeResponse.builder()
                .accountId(user.getAccountId())
                .publicName(user.getPublicName())
                .role(user.getRole())
                .sessionId(user.getSessionId())
                .build();
    }

    @PostMapping("/logout")
    public LogoutResponse logout() {
        return authService.logoutCurrentSession();
    }
}