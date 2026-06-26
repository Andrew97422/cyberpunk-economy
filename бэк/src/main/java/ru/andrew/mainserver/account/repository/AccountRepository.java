package ru.andrew.mainserver.account.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.mainserver.account.entity.Account;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByPublicName(String publicName);

    boolean existsByPublicNameIgnoreCase(String publicName);

    Page<Account> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Account> findByPublicNameContainingIgnoreCaseOrderByCreatedAtDesc(String publicName, Pageable pageable);
}