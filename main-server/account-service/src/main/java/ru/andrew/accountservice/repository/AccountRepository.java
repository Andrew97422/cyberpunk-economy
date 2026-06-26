package ru.andrew.accountservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.andrew.accountservice.entity.Account;
import ru.andrew.accountservice.entity.AccountStatus;
import ru.andrew.accountservice.entity.Role;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByPublicName(String publicName);

    boolean existsByPublicNameIgnoreCase(String publicName);

    Page<Account> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Search by public name OR character name, with optional role/status filters.
     * Empty-string search = no text filter; null role/status = no filter.
     */
    @Query("""
            select a from Account a
            where (:search = ''
                   or lower(a.publicName) like lower(concat('%', :search, '%'))
                   or lower(a.characterName) like lower(concat('%', :search, '%')))
              and (:role is null or a.role = :role)
              and (:status is null or a.status = :status)
            order by a.createdAt desc
            """)
    Page<Account> search(@Param("search") String search,
                         @Param("role") Role role,
                         @Param("status") AccountStatus status,
                         Pageable pageable);
}
