package ru.andrew.accessservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.accessservice.entity.AccountSnapshot;
import ru.andrew.accessservice.exception.NotFoundException;
import ru.andrew.accessservice.repository.AccountSnapshotRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSnapshotService {

    private final AccountSnapshotRepository repository;

    @Transactional
    public AccountSnapshot upsert(Long id, String publicName, String role, String status) {
        if (id == null || publicName == null || role == null || status == null) {
            log.warn("Skip account snapshot upsert: missing required fields id={}", id);
            return null;
        }
        AccountSnapshot snapshot = repository.findById(id).orElseGet(() -> {
            AccountSnapshot fresh = new AccountSnapshot();
            fresh.setId(id);
            return fresh;
        });
        snapshot.setPublicName(publicName);
        snapshot.setRole(role);
        snapshot.setStatus(status);
        return repository.save(snapshot);
    }

    public AccountSnapshot getActiveById(Long id) {
        AccountSnapshot snap = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
        if (!"ACTIVE".equals(snap.getStatus())) {
            throw new NotFoundException("Account is not active: " + id);
        }
        return snap;
    }

    public AccountSnapshot getActiveByPublicName(String publicName) {
        AccountSnapshot snap = repository.findByPublicNameIgnoreCase(publicName)
                .orElseThrow(() -> new NotFoundException("Account not found: " + publicName));
        if (!"ACTIVE".equals(snap.getStatus())) {
            throw new NotFoundException("Account is not active: " + publicName);
        }
        return snap;
    }
}
