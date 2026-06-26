package ru.andrew.mainserver.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.mainserver.card.entity.CardBinding;

import java.util.Optional;

public interface CardBindingRepository extends JpaRepository<CardBinding, Long> {
    Optional<CardBinding> findByCardUid(String cardUid);
    Optional<CardBinding> findByAccountId(Long accountId);
}