package ru.andrew.mainserver.gateway.banking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.auth.service.CurrentUserService;
import ru.andrew.mainserver.auth.token.AuthenticatedUser;
import ru.andrew.mainserver.gateway.news.NewsGatewayService;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/banking")
@RequiredArgsConstructor
public class BankingController {

    private final BankingGatewayService gateway;
    private final CurrentUserService currentUserService;
    private final NewsGatewayService newsGateway;
    private final ObjectMapper objectMapper;

    @GetMapping("/balance/me")
    public JsonNode getMyBalance() {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.GET_MY_BALANCE, Map.of(), JsonNode.class);
    }

    @GetMapping("/balance/{accountId}")
    public JsonNode getBalance(@PathVariable Long accountId) {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.GET_BALANCE_BY_ACCOUNT,
                Map.of("accountId", accountId), JsonNode.class);
    }

    @PostMapping("/deposit")
    public JsonNode deposit(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                            @RequestBody JsonNode body) {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.DEPOSIT, idempotencyKey, body, JsonNode.class);
    }

    @PostMapping("/withdraw")
    public JsonNode withdraw(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                             @RequestBody JsonNode body) {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.WITHDRAW, idempotencyKey, body, JsonNode.class);
    }

    @PostMapping("/transfer")
    public JsonNode transfer(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                             @RequestBody JsonNode body) {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.TRANSFER, idempotencyKey, body, JsonNode.class);
    }

    @PostMapping("/reverse")
    public JsonNode reverse(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                            @RequestBody JsonNode body) {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.REVERSE, idempotencyKey, body, JsonNode.class);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    @PostMapping("/mass-operation")
    public JsonNode massOperation(@RequestBody JsonNode body) {
        AuthenticatedUser actor = currentUserService.getCurrentUser();
        JsonNode result = gateway.send(actor, BankingCommandType.MASS_OPERATION, body, JsonNode.class);
        if (result != null && result.path("success").asBoolean(false)) {
            autoDraftAnnouncement(actor, body, result);
        }
        return result;
    }

    /**
     * After a successful economic event, drop a DRAFT announcement into the news feed
     * so an editor can polish and publish the in-game story behind it. Best-effort:
     * never let a news hiccup fail the economic event itself.
     */
    private void autoDraftAnnouncement(AuthenticatedUser actor, JsonNode body, JsonNode result) {
        try {
            String mode = result.path("mode").asText("");
            String currency = result.path("currencyType").asText("");
            int affected = result.path("affected").asInt(0);
            BigDecimal delta = new BigDecimal(result.path("totalDelta").asText("0"));
            String comment = body.path("comment").asText("");
            String value = body.path("value").asText("");
            String curLabel = "CRYPTO".equals(currency) ? "крипты" : "безналичных";

            String action = switch (mode) {
                case "CREDIT" -> "Всем начислено по " + value + " " + curLabel;
                case "DEBIT" -> "У всех списано по " + value + " " + curLabel;
                case "MULTIPLY" -> "Балансы (" + curLabel + ") умножены на ×" + value;
                default -> "Изменение балансов";
            };
            String title = comment.isBlank() ? "Экономическое событие в Найт-Сити" : comment;

            ObjectNode draft = objectMapper.createObjectNode();
            draft.put("title", title);
            draft.put("summary", action + ". Затронуто счетов: " + affected + ".");
            draft.put("body", "## " + title + "\n\n" + action + ".\n\n"
                    + "- Затронуто счетов: **" + affected + "**\n"
                    + "- Суммарное изменение: **" + delta + " " + currency + "**\n\n"
                    + "_Черновик создан автоматически. Отредактируйте и опубликуйте, чтобы игроки узнали новость._");
            draft.put("category", "Экономика");
            draft.put("status", "DRAFT");
            newsGateway.create(actor, draft);
        } catch (Exception ex) {
            log.warn("Auto-draft for economic event failed (ignored): {}", ex.getMessage());
        }
    }

    /**
     * Player-initiated payment from the caller's OWN account to another player.
     * Recipient resolved by typed public name or by a tapped card UID. Any authenticated user.
     */
    @PostMapping("/pay")
    public JsonNode pay(@RequestBody Map<String, Object> body) {
        AuthenticatedUser actor = currentUserService.getCurrentUser();
        String toPublicName = body.get("toPublicName") == null ? null : body.get("toPublicName").toString();
        String toCardUid = body.get("toCardUid") == null ? null : body.get("toCardUid").toString();
        if ((toPublicName == null || toPublicName.isBlank()) && toCardUid != null && !toCardUid.isBlank()) {
            JsonNode card = gateway.resolveCard(actor, toCardUid.trim());
            String cardStatus = card == null ? "" : card.path("cardStatus").asText("");
            if ("BLOCKED".equals(cardStatus) || "LOST".equals(cardStatus) || "REPLACED".equals(cardStatus)) {
                throw new IllegalStateException("Карта получателя недоступна (" + cardStatus + ")");
            }
            toPublicName = card == null ? null : card.path("publicName").asText(null);
        }
        if (toPublicName == null || toPublicName.isBlank()) {
            throw new IllegalArgumentException("Не указан получатель");
        }
        String currencyType = body.get("currencyType") == null ? null : body.get("currencyType").toString();
        BigDecimal amount = body.get("amount") == null ? null : new BigDecimal(body.get("amount").toString());
        String comment = body.get("comment") == null ? null : body.get("comment").toString();
        return gateway.pay(actor, toPublicName, currencyType, amount, comment);
    }

    /** Resolve a tapped card UID → owner, so the pay screen can show the payee. Any authenticated user. */
    @GetMapping("/pay/lookup")
    public JsonNode payLookup(@RequestParam String cardUid) {
        return gateway.resolveCard(currentUserService.getCurrentUser(), cardUid);
    }

    // ---- Crypto exchange ----

    @GetMapping("/crypto/rate")
    public JsonNode cryptoRate() {
        return gateway.cryptoRate(currentUserService.getCurrentUser());
    }

    @PostMapping("/crypto/buy")
    public JsonNode cryptoBuy(@RequestBody JsonNode body) {
        return gateway.cryptoBuy(currentUserService.getCurrentUser(), body);
    }

    @PostMapping("/crypto/sell")
    public JsonNode cryptoSell(@RequestBody JsonNode body) {
        return gateway.cryptoSell(currentUserService.getCurrentUser(), body);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    @PostMapping("/crypto/market")
    public JsonNode cryptoSetMarket(@RequestBody JsonNode body) {
        return gateway.cryptoSetMarket(currentUserService.getCurrentUser(), body);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    @PostMapping("/crypto/shock")
    public JsonNode cryptoShock(@RequestBody JsonNode body) {
        return gateway.cryptoShock(currentUserService.getCurrentUser(), body);
    }

    // ---- Credit (deposits & loans) ----

    @GetMapping("/credit/overview")
    public JsonNode creditOverview() {
        return gateway.creditOverview(currentUserService.getCurrentUser());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    @PostMapping("/credit/policy")
    public JsonNode creditPolicy(@RequestBody JsonNode body) {
        return gateway.creditSetPolicy(currentUserService.getCurrentUser(), body);
    }

    @PostMapping("/credit/deposit")
    public JsonNode openDeposit(@RequestBody JsonNode body) {
        return gateway.openDeposit(currentUserService.getCurrentUser(), body);
    }

    @PostMapping("/credit/deposit/close")
    public JsonNode closeDeposit(@RequestBody JsonNode body) {
        return gateway.closeDeposit(currentUserService.getCurrentUser(), body);
    }

    @PostMapping("/credit/loan")
    public JsonNode takeLoan(@RequestBody JsonNode body) {
        return gateway.takeLoan(currentUserService.getCurrentUser(), body);
    }

    @PostMapping("/credit/loan/repay")
    public JsonNode repayLoan(@RequestBody JsonNode body) {
        return gateway.repayLoan(currentUserService.getCurrentUser(), body);
    }

    @GetMapping("/transactions/me")
    public JsonNode getMyTransactions(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.GET_MY_TRANSACTIONS,
                Map.of("page", page, "size", size), JsonNode.class);
    }

    @GetMapping("/transactions/{accountId}")
    public JsonNode getTransactionsByAccountId(@PathVariable Long accountId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return gateway.send(currentUserService.getCurrentUser(),
                BankingCommandType.GET_TRANSACTIONS_BY_ACCOUNT,
                Map.of("accountId", accountId, "page", page, "size", size), JsonNode.class);
    }
}
