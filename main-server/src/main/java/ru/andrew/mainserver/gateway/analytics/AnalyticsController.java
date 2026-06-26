package ru.andrew.mainserver.gateway.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.andrew.mainserver.auth.service.CurrentUserService;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsGatewayService analyticsGateway;
    private final CurrentUserService currentUserService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode overview() {
        return analyticsGateway.overview(currentUserService.getCurrentUser());
    }
}
