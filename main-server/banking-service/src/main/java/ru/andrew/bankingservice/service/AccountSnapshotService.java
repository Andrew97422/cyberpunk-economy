package ru.andrew.bankingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.dto.AccountEvent;
import ru.andrew.bankingservice.entity.AccountSnapshot;
import ru.andrew.bankingservice.repository.AccountSnapshotRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSnapshotService {

    private final AccountSnapshotRepository repository;
    private final BalanceInitializationService balanceInitializationService;

    @Transactional
    public void upsert(AccountEvent event) {
        if (event == null || event.id() == null) {
            log.warn("Skip account snapshot upsert: empty event");
            return;
        }

        AccountSnapshot snapshot = repository.findById(event.id())
                .orElseGet(() -> {
                    AccountSnapshot fresh = new AccountSnapshot();
                    fresh.setId(event.id());
                    return fresh;
                });

        snapshot.setPublicName(event.publicName());
        snapshot.setRole(event.role());
        snapshot.setStatus(event.status());

        repository.save(snapshot);
        balanceInitializationService.createBalanceIfAbsent(event.id());

        log.debug("Account snapshot upserted id={}, status={}", event.id(), event.status());
    }
}
