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
import ru.andrew.mainserver.session.sync.SessionSnapshot;
import ru.andrew.mainserver.session.sync.SessionSnapshotService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessionSnapshotService sessionSnapshotService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token == null || !jwtService.isValid(token)) {
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

            Optional<SessionSnapshot> snapshotOpt = sessionSnapshotService.findById(sessionId);
            if (snapshotOpt.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }
            SessionSnapshot snapshot = snapshotOpt.get();
            if (!"ACTIVE".equals(snapshot.getStatus())) {
                filterChain.doFilter(request, response);
                return;
            }
            if (snapshot.getExpiresAt() != null && Instant.now().isAfter(snapshot.getExpiresAt())) {
                // session is past its absolute deadline; access-service will publish session.expired
                // shortly. Reject the request locally either way.
                filterChain.doFilter(request, response);
                return;
            }
            if (!snapshot.getAccountId().equals(accountId)) {
                filterChain.doFilter(request, response);
                return;
            }

            AuthenticatedUser principal = AuthenticatedUser.builder()
                    .accountId(accountId)
                    .publicName(username)
                    .role(role)
                    .sessionId(sessionId)
                    .authorities(Set.of("ROLE_" + role))
                    .build();

            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
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
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
