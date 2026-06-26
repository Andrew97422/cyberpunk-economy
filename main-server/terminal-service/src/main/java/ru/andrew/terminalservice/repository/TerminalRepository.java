package ru.andrew.terminalservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.terminalservice.entity.Terminal;
import ru.andrew.terminalservice.entity.TerminalStatus;

import java.util.Optional;

public interface TerminalRepository extends JpaRepository<Terminal, Long> {

    Optional<Terminal> findByName(String name);

    Page<Terminal> findAllByOrderByNameAsc(Pageable pageable);

    Page<Terminal> findAllByStatusOrderByNameAsc(TerminalStatus status, Pageable pageable);
}
