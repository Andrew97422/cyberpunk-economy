package ru.andrew.mainserver.gateway.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

import java.util.Map;

@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceGatewayService gateway;
    private final CurrentUserService currentUserService;

    // ---- Catalog browse (any authenticated user) ----

    @GetMapping("/products")
    public JsonNode listProducts(@RequestParam(required = false) String search,
                                 @RequestParam(required = false) String category,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return gateway.listProducts(currentUserService.getCurrentUser(), search, category, status, page, size);
    }

    @GetMapping("/products/{productId}")
    public JsonNode getProduct(@PathVariable Long productId) {
        return gateway.getProduct(currentUserService.getCurrentUser(), productId);
    }

    // ---- Catalog management (ADMIN/BANKER) ----

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode createProduct(@RequestBody JsonNode body) {
        return gateway.createProduct(currentUserService.getCurrentUser(), body);
    }

    @PatchMapping("/products/{productId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode updateProduct(@PathVariable Long productId, @RequestBody JsonNode body) {
        return gateway.updateProduct(currentUserService.getCurrentUser(), productId, body);
    }

    @PostMapping("/products/{productId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode changeProductStatus(@PathVariable Long productId, @RequestBody Map<String, String> body) {
        String status = body == null ? null : body.get("status");
        return gateway.changeProductStatus(currentUserService.getCurrentUser(), productId, status);
    }

    @PostMapping("/products/{productId}/stock")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode adjustStock(@PathVariable Long productId, @RequestBody Map<String, Integer> body) {
        Integer delta = body == null ? null : body.get("delta");
        return gateway.adjustStock(currentUserService.getCurrentUser(), productId, delta);
    }

    // ---- Price scenarios (ADMIN/BANKER) ----

    @PostMapping("/price-op")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode massPriceOp(@RequestBody JsonNode body) {
        return gateway.massPriceOp(currentUserService.getCurrentUser(), body);
    }

    @GetMapping("/scenarios")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode listScenarios() {
        return gateway.listScenarios(currentUserService.getCurrentUser());
    }

    @PostMapping("/scenarios")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode createScenario(@RequestBody JsonNode body) {
        return gateway.createScenario(currentUserService.getCurrentUser(), body);
    }

    @PatchMapping("/scenarios/{scenarioId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode updateScenario(@PathVariable Long scenarioId, @RequestBody JsonNode body) {
        return gateway.updateScenario(currentUserService.getCurrentUser(), scenarioId, body);
    }

    @DeleteMapping("/scenarios/{scenarioId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode deleteScenario(@PathVariable Long scenarioId) {
        return gateway.deleteScenario(currentUserService.getCurrentUser(), scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/apply")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode applyScenario(@PathVariable Long scenarioId) {
        return gateway.applyScenario(currentUserService.getCurrentUser(), scenarioId);
    }

    @PostMapping("/scenarios/{scenarioId}/schedule")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode scheduleScenario(@PathVariable Long scenarioId, @RequestBody JsonNode body) {
        return gateway.scheduleScenario(currentUserService.getCurrentUser(), scenarioId, body);
    }

    @PostMapping("/scenarios/{scenarioId}/trigger")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode triggerScenario(@PathVariable Long scenarioId) {
        return gateway.triggerScenarioNow(currentUserService.getCurrentUser(), scenarioId);
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode listSchedules() {
        return gateway.listSchedules(currentUserService.getCurrentUser());
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode cancelSchedule(@PathVariable Long scheduleId) {
        return gateway.cancelSchedule(currentUserService.getCurrentUser(), scheduleId);
    }

    // ---- Orders ----

    @PostMapping("/orders")
    public JsonNode purchase(@RequestBody Map<String, Object> body) {
        Long productId = body.get("productId") == null ? null : ((Number) body.get("productId")).longValue();
        Integer quantity = body.get("quantity") == null ? null : ((Number) body.get("quantity")).intValue();
        return gateway.purchase(currentUserService.getCurrentUser(), productId, quantity);
    }

    @PostMapping("/pos-purchase")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode posPurchase(@RequestBody Map<String, Object> body) {
        Long productId = body.get("productId") == null ? null : ((Number) body.get("productId")).longValue();
        Integer quantity = body.get("quantity") == null ? null : ((Number) body.get("quantity")).intValue();
        String cardUid = body.get("cardUid") == null ? null : body.get("cardUid").toString();
        return gateway.posPurchase(currentUserService.getCurrentUser(), productId, quantity, cardUid);
    }

    @GetMapping("/orders/me")
    public JsonNode myOrders(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return gateway.listMyOrders(currentUserService.getCurrentUser(), page, size);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode listOrders(@RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return gateway.listOrders(currentUserService.getCurrentUser(), status, page, size);
    }

    @GetMapping("/orders/{orderId}")
    public JsonNode getOrder(@PathVariable Long orderId) {
        return gateway.getOrder(currentUserService.getCurrentUser(), orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode cancelOrder(@PathVariable Long orderId) {
        return gateway.cancelOrder(currentUserService.getCurrentUser(), orderId);
    }
}
