package ru.andrew.mainserver.gateway.news;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

import java.util.Map;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsGatewayService gateway;
    private final CurrentUserService currentUserService;

    // ---- Read (any authenticated user; players see only live posts) ----

    @GetMapping
    public JsonNode list(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String search,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return gateway.list(currentUserService.getCurrentUser(), status, category, search, page, size);
    }

    @GetMapping("/{newsId}")
    public JsonNode get(@PathVariable Long newsId) {
        return gateway.get(currentUserService.getCurrentUser(), newsId);
    }

    // ---- Management (ADMIN/BANKER) ----

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode create(@RequestBody JsonNode body) {
        return gateway.create(currentUserService.getCurrentUser(), body);
    }

    @PatchMapping("/{newsId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode update(@PathVariable Long newsId, @RequestBody JsonNode body) {
        return gateway.update(currentUserService.getCurrentUser(), newsId, body);
    }

    @PostMapping("/{newsId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode changeStatus(@PathVariable Long newsId, @RequestBody Map<String, String> body) {
        String status = body == null ? null : body.get("status");
        return gateway.changeStatus(currentUserService.getCurrentUser(), newsId, status);
    }

    @PostMapping("/{newsId}/pin")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode setPinned(@PathVariable Long newsId, @RequestBody Map<String, Boolean> body) {
        boolean pinned = body != null && Boolean.TRUE.equals(body.get("pinned"));
        return gateway.setPinned(currentUserService.getCurrentUser(), newsId, pinned);
    }
}
