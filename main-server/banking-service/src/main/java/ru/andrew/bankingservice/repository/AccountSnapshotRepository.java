package ru.andrew.bankingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.bankingservice.entity.AccountSnapshot;

import java.util.Optional;

public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, Long> {

    Optional<AccountSnapshot> findByPublicNameIgnoreCase(String publicName);
}