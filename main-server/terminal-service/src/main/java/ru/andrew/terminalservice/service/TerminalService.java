package ru.andrew.terminalservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.terminalservice.dto.TerminalResponse;
import ru.andrew.terminalservice.entity.Terminal;
import ru.andrew.terminalservice.entity.TerminalStatus;
import ru.andrew.terminalservice.exception.BadRequestException;
import ru.andrew.terminalservice.exception.NotFoundException;
import ru.andrew.terminalservice.exception.UnauthorizedException;
import ru.andrew.terminalservice.repository.TerminalRepository;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TerminalService {

    private static final Set<String> ADMIN_ONLY = Set.of("ADMIN");
    private static final Set<String> ADMIN_BANKER = Set.of("ADMIN", "BANKER");

    private final TerminalRepository terminalRepository;
    private final TerminalEventPublisher eventPublisher;

    @Transactional
    public TerminalResponse register(String actorRole, String name, String ipAddress,
                                     String location, String terminalType, String notes) {
        requireAdmin(actorRole);
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        String normalized = name.trim();
        terminalRepository.findByName(normalized).ifPresent(existing -> {
            throw new BadRequestException("Terminal with this name already exists");
        });

        Terminal terminal = new Terminal();
        terminal.setName(normalized);
        terminal.setIpAddress(ipAddress);
        terminal.setLocation(location);
        terminal.setTerminalType(terminalType);
        terminal.setStatus(TerminalStatus.ACTIVE);
        terminal.setNotes(notes);

        Terminal saved = terminalRepository.save(terminal);
        eventPublisher.publishRegistered(saved);
        return toResponse(saved);
    }

    @Transactional
    public TerminalResponse update(String actorRole, Long terminalId, String ipAddress,
                                   String location, String terminalType, String notes) {
        requireAdmin(actorRole);
        Terminal terminal = getById(terminalId);
        if (ipAddress != null) terminal.setIpAddress(ipAddress);
        if (location != null) terminal.setLocation(location);
        if (terminalType != null) terminal.setTerminalType(terminalType);
        if (notes != null) terminal.setNotes(notes);
        Terminal saved = terminalRepository.save(terminal);
        eventPublisher.publishUpdated(saved);
        return toResponse(saved);
    }

    @Transactional
    public TerminalResponse changeStatus(String actorRole, Long terminalId, TerminalStatus newStatus) {
        requireAdmin(actorRole);
        Terminal terminal = getById(terminalId);
        TerminalStatus previous = terminal.getStatus();
        terminal.setStatus(newStatus);
        Terminal saved = terminalRepository.save(terminal);

        if (previous == newStatus) {
            // no-op, but still publish update for audit
            eventPublisher.publishUpdated(saved);
        } else {
            switch (newStatus) {
                case ACTIVE -> eventPublisher.publishActivated(saved);
                case BLOCKED, MAINTENANCE -> eventPublisher.publishBlocked(saved);
                case DECOMMISSIONED -> eventPublisher.publishDecommissioned(saved);
            }
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TerminalResponse getResponseById(String actorRole, Long terminalId) {
        requireAdminOrBanker(actorRole);
        return toResponse(getById(terminalId));
    }

    @Transactional(readOnly = true)
    public TerminalResponse getByName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        Terminal terminal = terminalRepository.findByName(name.trim())
                .orElseThrow(() -> new NotFoundException("Terminal not found: " + name));
        return toResponse(terminal);
    }

    @Transactional(readOnly = true)
    public Page<TerminalResponse> list(String actorRole, TerminalStatus statusFilter, Pageable pageable) {
        requireAdminOrBanker(actorRole);
        Page<Terminal> page = statusFilter == null
                ? terminalRepository.findAllByOrderByNameAsc(pageable)
                : terminalRepository.findAllByStatusOrderByNameAsc(statusFilter, pageable);
        return page.map(this::toResponse);
    }

    private Terminal getById(Long id) {
        if (id == null) throw new BadRequestException("terminalId is required");
        return terminalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Terminal not found: " + id));
    }

    private TerminalResponse toResponse(Terminal terminal) {
        return TerminalResponse.builder()
                .id(terminal.getId())
                .name(terminal.getName())
                .ipAddress(terminal.getIpAddress())
                .location(terminal.getLocation())
                .terminalType(terminal.getTerminalType())
                .status(terminal.getStatus().name())
                .notes(terminal.getNotes())
                .createdAt(terminal.getCreatedAt())
                .updatedAt(terminal.getUpdatedAt())
                .build();
    }

    private void requireAdmin(String role) {
        if (!ADMIN_ONLY.contains(role)) {
            throw new UnauthorizedException("Admin role required");
        }
    }

    private void requireAdminOrBanker(String role) {
        if (!ADMIN_BANKER.contains(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }
}
