package ru.andrew.mainserver.session.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.session.dto.SessionResponse;
import ru.andrew.mainserver.session.dto.TerminateSessionResponse;
import ru.andrew.mainserver.session.service.SessionManagementService;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionManagementController {

    private final SessionManagementService sessionManagementService;

    @GetMapping("/me")
    public SessionResponse getCurrentSession() {
        return sessionManagementService.getCurrentSession();
    }

    @GetMapping("/active")
    public Page<SessionResponse> getActiveSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return sessionManagementService.getActiveSessions(pageable);
    }

    @PostMapping("/{sessionId}/terminate")
    public TerminateSessionResponse terminateSession(@PathVariable Long sessionId) {
        return sessionManagementService.terminateSession(sessionId);
    }
}