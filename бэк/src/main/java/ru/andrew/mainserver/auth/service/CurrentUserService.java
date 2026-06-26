package ru.andrew.mainserver.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.common.exception.UnauthorizedException;

@Service
public class CurrentUserService {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new UnauthorizedException("Unauthorized");
        }

        return principal;
    }

    public Long getCurrentAccountId() {
        return getCurrentUser().getAccountId();
    }

    public Long getCurrentSessionId() {
        return getCurrentUser().getSessionId();
    }

    public String getCurrentRole() {
        return getCurrentUser().getRole();
    }
}