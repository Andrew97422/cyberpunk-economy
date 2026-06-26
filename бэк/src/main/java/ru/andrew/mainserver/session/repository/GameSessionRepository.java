package ru.andrew.mainserver.session.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.mainserver.session.entity.GameSession;
import ru.andrew.mainserver.session.entity.SessionStatus;

import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findFirstByAccountIdAndStatusOrderByStartedAtDesc(Long accountId, SessionStatus status);

    List<GameSession> findAllByStatus(SessionStatus status);

    Page<GameSession> findAllByStatus(SessionStatus status, Pageable pageable);
}