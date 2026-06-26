package ru.andrew.marketplaceservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.marketplaceservice.dto.CreateProductRequest;
import ru.andrew.marketplaceservice.dto.ProductResponse;
import ru.andrew.marketplaceservice.dto.UpdateProductRequest;
import ru.andrew.marketplaceservice.entity.CurrencyType;
import ru.andrew.marketplaceservice.entity.Product;
import ru.andrew.marketplaceservice.entity.ProductStatus;
import ru.andrew.marketplaceservice.exception.BadRequestException;
import ru.andrew.marketplaceservice.exception.NotFoundException;
import ru.andrew.marketplaceservice.exception.UnauthorizedException;
import ru.andrew.marketplaceservice.repository.ProductRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> ADMIN_BANKER = Set.of("ADMIN", "BANKER");

    private final ProductRepository productRepository;
    private final MarketplaceEventPublisher eventPublisher;

    @Transactional
    public ProductResponse create(String actorRole, CreateProductRequest request) {
        requireAdminOrBanker(actorRole);
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required");
        }
        // SKU is auto-assigned when the admin leaves it empty (it's a technical field).
        String sku;
        if (request.getSku() == null || request.getSku().isBlank()) {
            sku = generateSku();
        } else {
            sku = request.getSku().trim();
            productRepository.findBySkuIgnoreCase(sku).ifPresent(existing -> {
                throw new BadRequestException("Product with this sku already exists");
            });
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        BigDecimal price = normalizePrice(request.getPrice());
        product.setPrice(price);
        product.setBasePrice(price); // capture the base for "reset to base" operations
        product.setCurrencyType(parseCurrency(request.getCurrencyType()));
        product.setStockQuantity(normalizeStock(request.getStockQuantity()));
        product.setCategory(blankToNull(request.getCategory()));
        product.setImageUrl(blankToNull(request.getImageUrl()));
        product.setStatus(ProductStatus.ACTIVE);

        Product saved = productRepository.save(product);
        eventPublisher.publishProductCreated(saved);
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse update(String actorRole, Long productId, UpdateProductRequest request) {
        requireAdminOrBanker(actorRole);
        Product product = getById(productId);
        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(normalizePrice(request.getPrice()));
        }
        if (request.getCategory() != null) {
            product.setCategory(blankToNull(request.getCategory()));
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(blankToNull(request.getImageUrl()));
        }
        Product saved = productRepository.save(product);
        eventPublisher.publishProductUpdated(saved);
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse changeStatus(String actorRole, Long productId, ProductStatus newStatus) {
        requireAdminOrBanker(actorRole);
        if (newStatus == null) {
            throw new BadRequestException("status is required");
        }
        Product product = getById(productId);
        product.setStatus(newStatus);
        Product saved = productRepository.save(product);
        eventPublisher.publishProductStatusChanged(saved);
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse adjustStock(String actorRole, Long productId, Integer delta) {
        requireAdminOrBanker(actorRole);
        if (delta == null) {
            throw new BadRequestException("delta is required");
        }
        Product product = productRepository.findWithLockById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
        if (product.getStockQuantity() == null) {
            throw new BadRequestException("Product has unlimited stock; cannot adjust");
        }
        int updated = product.getStockQuantity() + delta;
        if (updated < 0) {
            throw new BadRequestException("Stock cannot become negative");
        }
        product.setStockQuantity(updated);
        Product saved = productRepository.save(product);
        eventPublisher.publishProductUpdated(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(String actorRole, Long productId) {
        Product product = getById(productId);
        if (!isOperator(actorRole) && product.getStatus() != ProductStatus.ACTIVE) {
            throw new NotFoundException("Product not found: " + productId);
        }
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String actorRole, String search, String category,
                                      String statusFilter, Pageable pageable) {
        ProductStatus status;
        if (isOperator(actorRole)) {
            status = parseStatusOrNull(statusFilter);
        } else {
            // players only see the active catalog
            status = ProductStatus.ACTIVE;
        }
        // Empty string = "no filter" (see ProductRepository.search — avoids null → lower(bytea)).
        String categoryFilter = category == null ? "" : category.trim();
        String searchFilter = search == null ? "" : search.trim();
        return productRepository.search(status, categoryFilter, searchFilter, pageable)
                .map(this::toResponse);
    }

    /** Generate a unique, human-neutral SKU so non-technical admins never have to. */
    private String generateSku() {
        for (int i = 0; i < 6; i++) {
            String candidate = "SKU-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                    + "-" + Integer.toString((int) (Math.random() * 1296), 36).toUpperCase();
            if (productRepository.findBySkuIgnoreCase(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "SKU-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Product getById(Long id) {
        if (id == null) throw new BadRequestException("productId is required");
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .basePrice(p.getBasePrice())
                .currencyType(p.getCurrencyType().name())
                .stockQuantity(p.getStockQuantity())
                .status(p.getStatus().name())
                .category(p.getCategory())
                .imageUrl(p.getImageUrl())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private boolean isOperator(String role) {
        return "ADMIN".equals(role) || "BANKER".equals(role) || "DEVELOPER".equals(role);
    }

    private void requireAdminOrBanker(String role) {
        if (!ADMIN_BANKER.contains(role)) {
            throw new UnauthorizedException("Admin or banker role required");
        }
    }

    private CurrencyType parseCurrency(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("currencyType is required");
        }
        try {
            return CurrencyType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid currency type: " + value);
        }
    }

    private ProductStatus parseStatusOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ProductStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price == null) {
            throw new BadRequestException("price is required");
        }
        BigDecimal normalized = price.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("price must be positive");
        }
        return normalized;
    }

    private Integer normalizeStock(Integer stock) {
        if (stock == null) return null; // unlimited
        if (stock < 0) {
            throw new BadRequestException("stockQuantity cannot be negative");
        }
        return stock;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
