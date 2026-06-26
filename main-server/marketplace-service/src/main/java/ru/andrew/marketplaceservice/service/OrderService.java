package ru.andrew.marketplaceservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.marketplaceservice.dto.OrderResponse;
import ru.andrew.marketplaceservice.entity.MarketOrder;
import ru.andrew.marketplaceservice.entity.OrderStatus;
import ru.andrew.marketplaceservice.entity.Product;
import ru.andrew.marketplaceservice.entity.ProductStatus;
import ru.andrew.marketplaceservice.exception.BadRequestException;
import ru.andrew.marketplaceservice.exception.NotFoundException;
import ru.andrew.marketplaceservice.exception.UnauthorizedException;
import ru.andrew.marketplaceservice.repository.MarketOrderRepository;
import ru.andrew.marketplaceservice.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<String> ADMIN_BANKER = Set.of("ADMIN", "BANKER");

    private final ProductRepository productRepository;
    private final MarketOrderRepository orderRepository;
    private final MarketplaceEventPublisher eventPublisher;

    /**
     * Step 1 of the purchase saga: validate availability, reserve stock and create a
     * PENDING_PAYMENT order. The gateway then charges the buyer and confirms.
     */
    @Transactional
    public OrderResponse reserve(Long actorId, String buyerPublicName, Long productId, Integer quantity) {
        if (actorId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        int qty = normalizeQuantity(quantity);

        Product product = productRepository.findWithLockById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BadRequestException("Product is not available");
        }
        if (product.getStockQuantity() != null) {
            if (product.getStockQuantity() < qty) {
                throw new BadRequestException("Insufficient stock");
            }
            product.setStockQuantity(product.getStockQuantity() - qty);
            productRepository.save(product);
        }

        BigDecimal unitPrice = product.getPrice();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(qty));

        MarketOrder order = new MarketOrder();
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setBuyerAccountId(actorId);
        order.setBuyerPublicName(buyerPublicName);
        order.setQuantity(qty);
        order.setUnitPrice(unitPrice);
        order.setTotalPrice(totalPrice);
        order.setCurrencyType(product.getCurrencyType());
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        MarketOrder saved = orderRepository.save(order);
        eventPublisher.publishOrderCreated(saved);
        return toResponse(saved);
    }

    /**
     * Step 3 of the purchase saga: mark the order paid once the buyer has been charged.
     * Idempotent — confirming an already-paid order returns it unchanged.
     */
    @Transactional
    public OrderResponse confirm(Long actorId, String actorRole, Long orderId, Long transactionId) {
        MarketOrder order = getById(orderId);
        requireBuyerOrOperator(actorId, actorRole, order);

        if (order.getStatus() == OrderStatus.PAID) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Order cannot be confirmed in status " + order.getStatus());
        }
        order.setStatus(OrderStatus.PAID);
        order.setTransactionId(transactionId);
        MarketOrder saved = orderRepository.save(order);
        eventPublisher.publishOrderPaid(saved);
        return toResponse(saved);
    }

    /**
     * Compensating step: cancel an order and return reserved stock. Idempotent.
     * Refund of a PAID order is handled by the gateway (banking reversal) before calling this.
     */
    @Transactional
    public OrderResponse cancel(Long actorId, String actorRole, Long orderId) {
        MarketOrder order = getById(orderId);
        requireBuyerOrOperator(actorId, actorRole, order);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        restock(order);
        order.setStatus(OrderStatus.CANCELLED);
        MarketOrder saved = orderRepository.save(order);
        eventPublisher.publishOrderCancelled(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listMyOrders(Long actorId, Pageable pageable) {
        return orderRepository.findByBuyerAccountIdOrderByCreatedAtDesc(actorId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(String actorRole, String statusFilter, Pageable pageable) {
        requireAdminOrBanker(actorRole);
        OrderStatus status = parseStatusOrNull(statusFilter);
        Page<MarketOrder> page = status == null
                ? orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long actorId, String actorRole, Long orderId) {
        MarketOrder order = getById(orderId);
        requireBuyerOrOperator(actorId, actorRole, order);
        return toResponse(order);
    }

    private void restock(MarketOrder order) {
        productRepository.findWithLockById(order.getProductId()).ifPresent(product -> {
            if (product.getStockQuantity() != null) {
                product.setStockQuantity(product.getStockQuantity() + order.getQuantity());
                productRepository.save(product);
            }
        });
    }

    private MarketOrder getById(Long id) {
        if (id == null) throw new BadRequestException("orderId is required");
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    private OrderResponse toResponse(MarketOrder o) {
        return OrderResponse.builder()
                .id(o.getId())
                .productId(o.getProductId())
                .productName(o.getProductName())
                .buyerAccountId(o.getBuyerAccountId())
                .buyerPublicName(o.getBuyerPublicName())
                .quantity(o.getQuantity())
                .unitPrice(o.getUnitPrice())
                .totalPrice(o.getTotalPrice())
                .currencyType(o.getCurrencyType().name())
                .status(o.getStatus().name())
                .transactionId(o.getTransactionId())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }

    private int normalizeQuantity(Integer quantity) {
        int qty = quantity == null ? 1 : quantity;
        if (qty < 1) {
            throw new BadRequestException("quantity must be at least 1");
        }
        return qty;
    }

    private void requireBuyerOrOperator(Long actorId, String actorRole, MarketOrder order) {
        boolean owner = actorId != null && actorId.equals(order.getBuyerAccountId());
        if (!owner && !ADMIN_BANKER.contains(actorRole)) {
            throw new UnauthorizedException("Not allowed to access this order");
        }
    }

    private void requireAdminOrBanker(String role) {
        if (!ADMIN_BANKER.contains(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }

    private OrderStatus parseStatusOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
