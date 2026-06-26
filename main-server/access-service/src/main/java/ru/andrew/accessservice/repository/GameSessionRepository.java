package ru.andrew.accessservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.accessservice.entity.GameSession;
import ru.andrew.accessservice.entity.SessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findFirstByAccountIdAndStatusOrderByStartedAtDesc(Long accountId, SessionStatus status);

    Page<GameSession> findAllByStatus(SessionStatus status, Pageable pageable);

    List<GameSession> findAllByStatusAndExpiresAtBefore(SessionStatus status, Instant cutoff);
}
