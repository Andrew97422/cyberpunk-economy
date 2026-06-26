package ru.andrew.cardservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.cardservice.entity.CardBinding;
import ru.andrew.cardservice.entity.CardStatus;

import java.util.List;
import java.util.Optional;

public interface CardBindingRepository extends JpaRepository<CardBinding, Long> {

    Optional<CardBinding> findByCardUid(String cardUid);

    Optional<CardBinding> findFirstByAccountIdAndStatus(Long accountId, CardStatus status);

    List<CardBinding> findAllByAccountIdOrderByIssuedAtDesc(Long accountId);
}
