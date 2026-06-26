package ru.andrew.accessservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.accessservice.dto.PinResponse;
import ru.andrew.accessservice.entity.AccountSnapshot;
import ru.andrew.accessservice.entity.PinCode;
import ru.andrew.accessservice.entity.PinCodeStatus;
import ru.andrew.accessservice.exception.BadRequestException;
import ru.andrew.accessservice.exception.NotFoundException;
import ru.andrew.accessservice.repository.AccountSnapshotRepository;
import ru.andrew.accessservice.repository.PinCodeRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PinCodeService {

    private final PinCodeRepository pinCodeRepository;
    private final AccountSnapshotRepository accountSnapshotRepository;
    private final AccountSnapshotService accountSnapshotService;
    private final PasswordEncoder passwordEncoder;
    private final PinHasher pinHasher;

    @Transactional(readOnly = true)
    public PinCode getById(Long id) {
        return pinCodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pin code not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PinResponse> getAllByAccount(Long accountId) {
        return pinCodeRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PinResponse create(String targetPublicName, String rawPin, Integer durationMinutes,
                              Long createdByAccountId, String comment) {
        if (rawPin == null || rawPin.isBlank()) {
            throw new BadRequestException("rawPin is required");
        }
        if (durationMinutes == null || durationMinutes < 1 || durationMinutes > 1440) {
            throw new BadRequestException("durationMinutes must be between 1 and 1440");
        }

        AccountSnapshot target = accountSnapshotService.getActiveByPublicName(targetPublicName);

        // Single CREATED PIN per account: expire any stale ones, reject if there's a live one.
        pinCodeRepository.findFirstByAccountIdAndStatusOrderByCreatedAtDesc(target.getId(), PinCodeStatus.CREATED)
                .ifPresent(existing -> {
                    if (!isPinExpired(existing)) {
                        throw new BadRequestException("Account already has active PIN");
                    }
                    existing.setStatus(PinCodeStatus.EXPIRED);
                    pinCodeRepository.save(existing);
                });

        String lookupHash = pinHasher.lookupHash(rawPin);

        // Global uniqueness among active PINs — required for PIN-only login.
        if (pinCodeRepository.findByPinLookupHashAndStatus(lookupHash, PinCodeStatus.CREATED).isPresent()) {
            throw new BadRequestException("PIN is already in use, choose a different one");
        }

        PinCode pinCode = new PinCode();
        pinCode.setAccountId(target.getId());
        pinCode.setPinHash(passwordEncoder.encode(rawPin));
        pinCode.setPinLookupHash(lookupHash);
        pinCode.setDurationMinutes(durationMinutes);
        pinCode.setStatus(PinCodeStatus.CREATED);
        pinCode.setCreatedByAccountId(createdByAccountId);
        pinCode.setComment(comment);

        PinCode saved = pinCodeRepository.save(pinCode);
        return toResponse(saved, target);
    }

    @Transactional
    public PinResponse revoke(Long pinId) {
        PinCode pinCode = getById(pinId);
        if (pinCode.getStatus() == PinCodeStatus.USED) {
            throw new BadRequestException("Used PIN cannot be revoked");
        }
        pinCode.setStatus(PinCodeStatus.REVOKED);
        return toResponse(pinCodeRepository.save(pinCode));
    }

    @Transactional
    public PinCode markUsed(PinCode pinCode) {
        pinCode.setStatus(PinCodeStatus.USED);
        pinCode.setUsedAt(Instant.now());
        return pinCodeRepository.save(pinCode);
    }

    @Transactional
    public PinCode markExpired(PinCode pinCode) {
        pinCode.setStatus(PinCodeStatus.EXPIRED);
        return pinCodeRepository.save(pinCode);
    }

    /**
     * Finds the unique CREATED PIN matching the raw value, verifies BCrypt and
     * its validity window. Returns the PIN if all checks pass, otherwise throws.
     */
    @Transactional
    public PinCode resolveAndConsume(String rawPin) {
        if (rawPin == null || rawPin.isBlank()) {
            throw new BadRequestException("PIN is required");
        }
        String lookupHash = pinHasher.lookupHash(rawPin);
        Optional<PinCode> match = pinCodeRepository.findByPinLookupHashAndStatus(lookupHash, PinCodeStatus.CREATED);
        if (match.isEmpty()) {
            throw new BadRequestException("Invalid PIN");
        }
        PinCode pinCode = match.get();

        if (!passwordEncoder.matches(rawPin, pinCode.getPinHash())) {
            throw new BadRequestException("Invalid PIN");
        }

        if (isPinExpired(pinCode)) {
            markExpired(pinCode);
            throw new BadRequestException("PIN expired");
        }

        return pinCode;
    }

    @Transactional
    public PinCode resolveForAccount(Long accountId, String rawPin) {
        PinCode pinCode = resolveAndConsume(rawPin);
        if (!pinCode.getAccountId().equals(accountId)) {
            throw new BadRequestException("PIN does not belong to this account");
        }
        return pinCode;
    }

    public boolean isPinExpired(PinCode pinCode) {
        Instant expiresAt = pinCode.getCreatedAt().plusSeconds(pinCode.getDurationMinutes() * 60L);
        return Instant.now().isAfter(expiresAt);
    }

    public PinResponse toResponse(PinCode pinCode) {
        AccountSnapshot snapshot = accountSnapshotRepository.findById(pinCode.getAccountId()).orElse(null);
        return toResponse(pinCode, snapshot);
    }

    private PinResponse toResponse(PinCode pinCode, AccountSnapshot snapshot) {
        return PinResponse.builder()
                .id(pinCode.getId())
                .accountId(pinCode.getAccountId())
                .publicName(snapshot != null ? snapshot.getPublicName() : null)
                .status(pinCode.getStatus().name())
                .durationMinutes(pinCode.getDurationMinutes())
                .createdAt(pinCode.getCreatedAt())
                .usedAt(pinCode.getUsedAt())
                .comment(pinCode.getComment())
                .build();
    }
}
