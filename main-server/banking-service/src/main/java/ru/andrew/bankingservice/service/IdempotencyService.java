package ru.andrew.bankingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.entity.IdempotencyRecord;
import ru.andrew.bankingservice.exception.BadRequestException;
import ru.andrew.bankingservice.repository.IdempotencyRecordRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdempotencyKey(key.trim());
    }

    @Transactional
    public IdempotencyRecord reserve(String key, String fingerprint, Long actorId, String operation) {
        if (key == null || key.isBlank()) {
            return null;
        }

        Optional<IdempotencyRecord> existingOpt = repository.findByIdempotencyKey(key.trim());
        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new BadRequestException("Idempotency key already used for another request");
            }
            return existing;
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(key.trim());
        record.setRequestFingerprint(fingerprint);
        record.setCreatedByAccountId(actorId);
        record.setOperation(operation);
        return repository.save(record);
    }

    @Transactional
    public void storeResponse(IdempotencyRecord record, Object response) {
        if (record == null) {
            return;
        }

        try {
            record.setResponsePayload(objectMapper.writeValueAsString(response));
            repository.save(record);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Cannot serialize idempotent response");
        }
    }

    public String fingerprint(String operation, Object payload, Long actorId) {
        try {
            String raw = operation + "|" + actorId + "|" + objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BadRequestException("Cannot build idempotency fingerprint");
        }
    }

    public <T> T parseResponse(IdempotencyRecord record, Class<T> responseType) {
        if (record == null || record.getResponsePayload() == null || record.getResponsePayload().isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(record.getResponsePayload(), responseType);
        } catch (Exception e) {
            throw new BadRequestException("Cannot read idempotent response");
        }
    }
}
