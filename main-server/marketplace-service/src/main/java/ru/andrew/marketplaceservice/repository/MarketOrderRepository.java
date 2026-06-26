package ru.andrew.marketplaceservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.andrew.marketplaceservice.entity.MarketOrder;
import ru.andrew.marketplaceservice.entity.OrderStatus;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, Long> {

    Page<MarketOrder> findByBuyerAccountIdOrderByCreatedAtDesc(Long buyerAccountId, Pageable pageable);

    Page<MarketOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<MarketOrder> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
}
