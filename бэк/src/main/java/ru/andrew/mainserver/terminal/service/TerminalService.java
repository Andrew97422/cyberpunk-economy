package ru.andrew.mainserver.terminal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.terminal.entity.Terminal;
import ru.andrew.mainserver.terminal.repository.TerminalRepository;

@Service
@RequiredArgsConstructor
public class TerminalService {

    private final TerminalRepository terminalRepository;

    @Transactional(readOnly = true)
    public Terminal findByNameOrNull(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return terminalRepository.findByName(name).orElse(null);
    }
}