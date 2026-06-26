package ru.andrew.marketplaceservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.andrew.marketplaceservice.entity.Product;
import ru.andrew.marketplaceservice.entity.ProductStatus;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findWithLockById(@Param("id") Long id);

    /**
     * Empty-string sentinels mean "no filter". Params are always non-null strings so a
     * null never reaches {@code lower(...)} (PostgreSQL would otherwise bind it as bytea
     * and fail with "function lower(bytea) does not exist").
     */
    @Query("""
            select p from Product p
            where (:status is null or p.status = :status)
              and (:category = '' or lower(p.category) = lower(:category))
              and (:search = ''
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(p.sku) like lower(concat('%', :search, '%')))
            """)
    Page<Product> search(@Param("status") ProductStatus status,
                         @Param("category") String category,
                         @Param("search") String search,
                         Pageable pageable);

    /**
     * Products targeted by a mass price operation. Empty-string sentinel for category means
     * "all categories" (same null-safe pattern as {@link #search}: never let null reach lower()).
     */
    @Query("""
            select p from Product p
            where (:status is null or p.status = :status)
              and (:category = '' or lower(p.category) = lower(:category))
            """)
    List<Product> findForMassOp(@Param("status") ProductStatus status,
                                @Param("category") String category);
}
