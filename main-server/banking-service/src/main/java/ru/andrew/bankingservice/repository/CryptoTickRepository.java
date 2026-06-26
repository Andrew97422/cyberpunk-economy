package ru.andrew.bankingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.bankingservice.entity.CryptoTick;

import java.util.List;

public interface CryptoTickRepository extends JpaRepository<CryptoTick, Long> {
    List<CryptoTick> findTop120ByOrderByCreatedAtDesc();
}
