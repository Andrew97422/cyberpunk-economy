package ru.andrew.mainserver.terminal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.mainserver.terminal.entity.Terminal;

import java.util.Optional;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {
    Optional<Terminal> findByName(String name);
}