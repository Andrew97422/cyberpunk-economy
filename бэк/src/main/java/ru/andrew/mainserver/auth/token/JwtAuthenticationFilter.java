package ru.andrew.mainserver.auth.token;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.AccountStatus;
import ru.andrew.mainserver.account.service.AccountService;
import ru.andrew.mainserver.session.entity.GameSession;
import ru.andrew.mainserver.session.entity.SessionStatus;
import ru.andrew.mainserver.session.service.SessionService;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountService accountService;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            if (!jwtService.isValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            Long accountId = jwtService.extractAccountId(token);
            Long sessionId = jwtService.extractSessionId(token);
            String username = jwtService.extractUsername(token);
            String role = jwtService.extractRole(token);

            if (accountId == null || sessionId == null || username == null || role == null) {
                filterChain.doFilter(request, response);
                return;
            }

            Account account = accountService.getById(accountId);

            if (account.getStatus() != AccountStatus.ACTIVE) {
                filterChain.doFilter(request, response);
                return;
            }

            if (!account.getPublicName().equals(username)) {
                filterChain.doFilter(request, response);
                return;
            }

            GameSession session = sessionService.findById(sessionId).orElse(null);

            if (session == null) {
                filterChain.doFilter(request, response);
                return;
            }

            if (session.getStatus() != SessionStatus.ACTIVE) {
                filterChain.doFilter(request, response);
                return;
            }

            if (!session.getAccount().getId().equals(account.getId())) {
                filterChain.doFilter(request, response);
                return;
            }

            if (sessionService.isExpired(session)) {
                sessionService.expire(session);
                filterChain.doFilter(request, response);
                return;
            }

            sessionService.touch(session);

            Collection<SimpleGrantedAuthority> authorities = buildAuthorities(account);

            AuthenticatedUser principal = AuthenticatedUser.builder()
                    .accountId(account.getId())
                    .publicName(account.getPublicName())
                    .role(account.getRole().name())
                    .sessionId(session.getId())
                    .authorities(
                            authorities.stream()
                                    .map(SimpleGrantedAuthority::getAuthority)
                                    .collect(java.util.stream.Collectors.toSet())
                    )
                    .build();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Collection<SimpleGrantedAuthority> buildAuthorities(Account account) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
    }
}