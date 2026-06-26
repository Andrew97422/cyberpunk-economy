package ru.andrew.accessservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.accessservice.entity.PinCode;
import ru.andrew.accessservice.entity.PinCodeStatus;

import java.util.List;
import java.util.Optional;

public interface PinCodeRepository extends JpaRepository<PinCode, Long> {

    Optional<PinCode> findByPinLookupHashAndStatus(String pinLookupHash, PinCodeStatus status);

    Optional<PinCode> findFirstByAccountIdAndStatusOrderByCreatedAtDesc(Long accountId, PinCodeStatus status);

    List<PinCode> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);
}
