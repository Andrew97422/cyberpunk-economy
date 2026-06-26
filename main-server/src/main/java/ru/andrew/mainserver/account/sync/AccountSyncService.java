package ru.andrew.mainserver.account.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.AccountStatus;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.repository.AccountRepository;

/**
 * Applies account-service updates to the local read-model.
 *
 * Called by {@link AccountSyncListener} when {@code account.events} arrive,
 * and eagerly by the auth/gateway path on successful writes to prevent
 * read-after-write races.
 *
 * Because both the synchronous write path and the asynchronous event listener can write the
 * same id for the first time concurrently, the insert is made idempotent: one writer wins and
 * the loser's duplicate-key failure is retried as an update.
 */
@Slf4j
@Service
public class AccountSyncService {

    private final AccountRepository accountRepository;

    @Autowired
    @Lazy
    private AccountSyncService self;

    public AccountSyncService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account upsert(Long id, String publicName, String role, String status, String characterName, String notes) {
        if (id == null || publicName == null || role == null || status == null) {
            log.warn("Skip account sync: missing required fields id={}, publicName={}", id, publicName);
            return null;
        }
        try {
            return self.doUpsert(id, publicName, role, status, characterName, notes);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent writer inserted this id first — retry; the row now exists so it becomes an update.
            return self.doUpsert(id, publicName, role, status, characterName, notes);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account doUpsert(Long id, String publicName, String role, String status, String characterName, String notes) {
        Account account = accountRepository.findById(id).orElseGet(() -> {
            Account fresh = new Account();
            fresh.setId(id);
            return fresh;
        });

        account.setPublicName(publicName);
        account.setRole(Role.valueOf(role));
        account.setStatus(AccountStatus.valueOf(status));
        if (characterName != null) {
            account.setCharacterName(characterName);
        }
        if (notes != null) {
            account.setNotes(notes);
        }

        // Flush inside this REQUIRES_NEW tx so a unique-violation surfaces here (catchable) rather than at commit.
        Account saved = accountRepository.saveAndFlush(account);
        log.debug("Account synced id={}, publicName={}, status={}", id, publicName, status);
        return saved;
    }
}
