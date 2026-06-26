package ru.andrew.mainserver.pin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.common.exception.BadRequestException;
import ru.andrew.mainserver.common.exception.NotFoundException;
import ru.andrew.mainserver.pin.entity.PinCode;
import ru.andrew.mainserver.pin.entity.PinCodeStatus;
import ru.andrew.mainserver.pin.repository.PinCodeRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PinCodeService {

    private final PinCodeRepository pinCodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PinCode getById(Long id) {
        return pinCodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pin code not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PinCode> getAllByAccount(Long accountId) {
        return pinCodeRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public PinCode findLatestActivePin(Account account) {
        return pinCodeRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
                        account.getId(),
                        PinCodeStatus.CREATED
                )
                .orElseThrow(() -> new BadRequestException("No active PIN found"));
    }

    @Transactional(readOnly = true)
    public boolean matches(String rawPin, String hash) {
        return passwordEncoder.matches(rawPin, hash);
    }

    @Transactional(readOnly = true)
    public boolean isExpired(PinCode pinCode) {
        Instant expiresAt = pinCode.getCreatedAt().plusSeconds(pinCode.getDurationMinutes() * 60L);
        return Instant.now().isAfter(expiresAt);
    }

    @Transactional
    public void markUsed(PinCode pinCode) {
        pinCode.setStatus(PinCodeStatus.USED);
        pinCode.setUsedAt(Instant.now());
        pinCodeRepository.save(pinCode);
    }

    @Transactional
    public void markExpired(PinCode pinCode) {
        pinCode.setStatus(PinCodeStatus.EXPIRED);
        pinCodeRepository.save(pinCode);
    }

    @Transactional
    public void revoke(PinCode pinCode) {
        if (pinCode.getStatus() == PinCodeStatus.USED) {
            throw new BadRequestException("Used PIN cannot be revoked");
        }
        pinCode.setStatus(PinCodeStatus.REVOKED);
        pinCodeRepository.save(pinCode);
    }

    @Transactional
    public PinCode create(Account account, String rawPin, Integer durationMinutes, Long createdByAccountId, String comment) {
        pinCodeRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(account.getId(), PinCodeStatus.CREATED)
                .ifPresent(existing -> {
                    if (!isExpired(existing)) {
                        throw new BadRequestException("Account already has active PIN");
                    }
                    markExpired(existing);
                });

        PinCode pinCode = new PinCode();
        pinCode.setAccount(account);
        pinCode.setPinHash(passwordEncoder.encode(rawPin));
        pinCode.setDurationMinutes(durationMinutes);
        pinCode.setStatus(PinCodeStatus.CREATED);
        pinCode.setCreatedByAccountId(createdByAccountId);
        pinCode.setComment(comment);

        return pinCodeRepository.save(pinCode);
    }
}