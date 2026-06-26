package ru.andrew.cardservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.cardservice.entity.AccountSnapshot;

import java.util.Optional;

public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, Long> {
    Optional<AccountSnapshot> findByPublicNameIgnoreCase(String publicName);
}
