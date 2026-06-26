package ru.andrew.mainserver.session.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSnapshotRepository extends JpaRepository<SessionSnapshot, Long> {
}
