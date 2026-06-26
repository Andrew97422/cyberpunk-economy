package ru.andrew.analyticsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One row per marketplace order event (created/paid/cancelled). */
@Entity
@Table(name = "order_fact", indexes = {
        @Index(name = "idx_of_occurred", columnList = "occurredAt"),
        @Index(name = "idx_of_status", columnList = "status")
})
@Getter
@Setter
public class OrderFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String eventId;

    private Instant occurredAt;
    private String eventType;
    private Long orderId;
    private Long productId;
    private String productName;
    private Long buyerAccountId;
    private String buyerPublicName;
    private Integer quantity;
    private BigDecimal totalPrice;
    private String currencyType;
    private String status;
}
