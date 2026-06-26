package ru.andrew.mainserver.banking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.mainserver.banking.entity.IdempotencyRecord;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}