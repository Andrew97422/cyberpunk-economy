package ru.andrew.mainserver.gateway.cards;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;

import java.util.Map;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardGatewayService cardGateway;
    private final CurrentUserService currentUserService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode issue(@RequestBody JsonNode body) {
        return cardGateway.issueCard(currentUserService.getCurrentUser(), body);
    }

    @PostMapping("/{cardId}/block")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode block(@PathVariable Long cardId, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return cardGateway.blockCard(currentUserService.getCurrentUser(), cardId, reason);
    }

    @PostMapping("/{cardId}/lost")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode lost(@PathVariable Long cardId, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return cardGateway.markLost(currentUserService.getCurrentUser(), cardId, reason);
    }

    @PostMapping("/{cardId}/replace")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode replace(@PathVariable Long cardId, @RequestBody JsonNode body) {
        return cardGateway.replaceCard(currentUserService.getCurrentUser(), cardId, body);
    }

    @GetMapping("/by-account/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode listByAccount(@PathVariable Long accountId) {
        return cardGateway.listByAccount(currentUserService.getCurrentUser(), accountId);
    }

    @GetMapping("/{cardId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode getOne(@PathVariable Long cardId) {
        return cardGateway.getCard(currentUserService.getCurrentUser(), cardId);
    }

    @GetMapping("/by-uid")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public JsonNode lookup(@RequestParam String uid) {
        return cardGateway.lookupByUid(currentUserService.getCurrentUser(), uid);
    }

    /**
     * Terminal-facing scan: looks up cardUid → AccountInfo and publishes a
     * card.scanned event. Still goes through JWT (operator of the terminal),
     * the terminal identity comes from the X-Terminal-Name header.
     */
    @PostMapping("/scan")
    public JsonNode scan(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String uid = body == null ? null : body.get("cardUid");
        String terminalName = request.getHeader("X-Terminal-Name");
        return cardGateway.scanCard(currentUserService.getCurrentUser(), uid, terminalName);
    }
}
