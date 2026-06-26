package ru.andrew.bankingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.bankingservice.entity.CryptoMarketState;

public interface CryptoMarketStateRepository extends JpaRepository<CryptoMarketState, Long> {
}
