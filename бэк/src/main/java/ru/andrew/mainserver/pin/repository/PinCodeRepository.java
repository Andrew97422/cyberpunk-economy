package ru.andrew.mainserver.pin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.mainserver.pin.entity.PinCode;
import ru.andrew.mainserver.pin.entity.PinCodeStatus;

import java.util.List;
import java.util.Optional;

public interface PinCodeRepository extends JpaRepository<PinCode, Long> {

    List<PinCode> findAllByStatus(PinCodeStatus status);

    List<PinCode> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<PinCode> findFirstByAccountIdAndStatusOrderByCreatedAtDesc(Long accountId, PinCodeStatus status);
}