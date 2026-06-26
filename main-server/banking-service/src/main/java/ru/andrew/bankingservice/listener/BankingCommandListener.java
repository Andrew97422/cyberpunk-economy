package ru.andrew.bankingservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import ru.andrew.bankingservice.command.BankingCommandType;
import ru.andrew.bankingservice.command.ServiceCommand;
import ru.andrew.bankingservice.command.ServiceReply;
import ru.andrew.bankingservice.dto.CreditActionRequest;
import ru.andrew.bankingservice.dto.CryptoMarketUpdateRequest;
import ru.andrew.bankingservice.dto.CryptoShockRequest;
import ru.andrew.bankingservice.dto.CryptoTradeRequest;
import ru.andrew.bankingservice.dto.DepositRequest;
import ru.andrew.bankingservice.dto.SetCreditPolicyRequest;
import ru.andrew.bankingservice.dto.MassOperationRequest;
import ru.andrew.bankingservice.dto.PurchaseChargeRequest;
import ru.andrew.bankingservice.dto.ReversalRequest;
import ru.andrew.bankingservice.dto.TransferRequest;
import ru.andrew.bankingservice.dto.WithdrawRequest;
import ru.andrew.bankingservice.exception.BadRequestException;
import ru.andrew.bankingservice.exception.NotFoundException;
import ru.andrew.bankingservice.service.BankingService;
import ru.andrew.bankingservice.service.CreditService;
import ru.andrew.bankingservice.service.CryptoMarketService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankingCommandListener {

    private final BankingService bankingService;
    private final CryptoMarketService cryptoMarketService;
    private final CreditService creditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.commands.v1", groupId = "banking-commands")
    @SendTo
    public String onCommand(String rawCommand) {
        ServiceReply reply;
        try {
            ServiceCommand command = objectMapper.readValue(rawCommand, ServiceCommand.class);
            BankingCommandType type = BankingCommandType.valueOf(command.commandType());
            log.info("Received banking command {} actorId={}", type, command.actorId());
            reply = dispatch(command, type);
        } catch (BadRequestException ex) {
            reply = ServiceReply.error(400, ex.getMessage());
        } catch (NotFoundException ex) {
            reply = ServiceReply.error(404, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            reply = ServiceReply.error(400, "Unknown command: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to process banking command", ex);
            reply = ServiceReply.error(500, "Internal error: " + ex.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(reply);
        } catch (Exception ex) {
            log.error("Failed to serialize banking reply", ex);
            return "{\"success\":false,\"status\":500,\"errorMessage\":\"Cannot serialize reply\"}";
        }
    }

    private ServiceReply dispatch(ServiceCommand command, BankingCommandType type) {
        Long actorId = command.actorId();
        String actorRole = command.actorRole();
        String idempotencyKey = command.idempotencyKey();
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();

        return switch (type) {
            case GET_MY_BALANCE -> ServiceReply.ok(bankingService.getMyBalance(actorId));
            case GET_BALANCE_BY_ACCOUNT -> ServiceReply.ok(
                    bankingService.getBalanceByAccountId(actorId, actorRole, asLong(payload.get("accountId"))));
            case GET_MY_TRANSACTIONS -> ServiceReply.ok(
                    bankingService.getMyTransactions(actorId, pageable(payload)));
            case GET_TRANSACTIONS_BY_ACCOUNT -> ServiceReply.ok(
                    bankingService.getTransactionsByAccountId(actorId, actorRole,
                            asLong(payload.get("accountId")), pageable(payload)));
            case DEPOSIT -> ServiceReply.ok(
                    bankingService.deposit(actorId, actorRole, idempotencyKey,
                            objectMapper.convertValue(payload, DepositRequest.class)));
            case WITHDRAW -> ServiceReply.ok(
                    bankingService.withdraw(actorId, actorRole, idempotencyKey,
                            objectMapper.convertValue(payload, WithdrawRequest.class)));
            case TRANSFER -> ServiceReply.ok(
                    bankingService.transfer(actorId, actorRole, idempotencyKey,
                            objectMapper.convertValue(payload, TransferRequest.class)));
            case REVERSE -> ServiceReply.ok(
                    bankingService.reverse(actorId, actorRole, idempotencyKey,
                            objectMapper.convertValue(payload, ReversalRequest.class)));
            case PURCHASE_CHARGE -> ServiceReply.ok(
                    bankingService.purchaseCharge(actorId, actorRole, idempotencyKey,
                            objectMapper.convertValue(payload, PurchaseChargeRequest.class)));
            case MASS_OPERATION -> ServiceReply.ok(
                    bankingService.massOperation(actorId, actorRole,
                            objectMapper.convertValue(payload, MassOperationRequest.class)));
            case GET_CRYPTO_RATE -> ServiceReply.ok(cryptoMarketService.getRate());
            case BUY_CRYPTO -> ServiceReply.ok(
                    bankingService.buyCrypto(actorId, objectMapper.convertValue(payload, CryptoTradeRequest.class)));
            case SELL_CRYPTO -> ServiceReply.ok(
                    bankingService.sellCrypto(actorId, objectMapper.convertValue(payload, CryptoTradeRequest.class)));
            case SET_CRYPTO_MARKET -> ServiceReply.ok(
                    cryptoMarketService.setMarket(actorRole, objectMapper.convertValue(payload, CryptoMarketUpdateRequest.class)));
            case CRYPTO_SHOCK -> ServiceReply.ok(
                    cryptoMarketService.shock(actorRole, objectMapper.convertValue(payload, CryptoShockRequest.class)));
            case GET_CREDIT_OVERVIEW -> ServiceReply.ok(creditService.overview(actorId));
            case SET_CREDIT_POLICY -> ServiceReply.ok(
                    creditService.setPolicy(actorId, actorRole, objectMapper.convertValue(payload, SetCreditPolicyRequest.class)));
            case OPEN_DEPOSIT -> ServiceReply.ok(
                    bankingService.openDeposit(actorId, objectMapper.convertValue(payload, CreditActionRequest.class)));
            case CLOSE_DEPOSIT -> ServiceReply.ok(
                    bankingService.closeDeposit(actorId, objectMapper.convertValue(payload, CreditActionRequest.class)));
            case TAKE_LOAN -> ServiceReply.ok(
                    bankingService.takeLoan(actorId, objectMapper.convertValue(payload, CreditActionRequest.class)));
            case REPAY_LOAN -> ServiceReply.ok(
                    bankingService.repayLoan(actorId, objectMapper.convertValue(payload, CreditActionRequest.class)));
        };
    }

    private PageRequest pageable(Map<String, Object> payload) {
        int page = payload.containsKey("page") ? ((Number) payload.get("page")).intValue() : 0;
        int size = payload.containsKey("size") ? ((Number) payload.get("size")).intValue() : 20;
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Long asLong(Object raw) {
        if (raw == null) throw new BadRequestException("accountId is required");
        return ((Number) raw).longValue();
    }
}
