package ru.andrew.mainserver.banking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.andrew.mainserver.banking.dto.*;
import ru.andrew.mainserver.banking.service.BankingService;

@RestController
@RequestMapping("/banking")
@RequiredArgsConstructor
public class BankingController {

    private final BankingService bankingService;

    @GetMapping("/balance/me")
    public BalanceResponse getMyBalance() {
        return bankingService.getMyBalance();
    }

    @GetMapping("/balance/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public BalanceResponse getBalance(@PathVariable Long accountId) {
        return bankingService.getBalanceByAccountId(accountId);
    }

    @PostMapping("/deposit")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public OperationResultResponse deposit(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DepositRequest request
    ) {
        return bankingService.deposit(idempotencyKey, request);
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public OperationResultResponse withdraw(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WithdrawRequest request
    ) {
        return bankingService.withdraw(idempotencyKey, request);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public OperationResultResponse adjust(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AdjustmentRequest request
    ) {
        return bankingService.adjust(idempotencyKey, request);
    }

    @PostMapping("/transfer")
    public OperationResultResponse transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        return bankingService.transfer(idempotencyKey, request);
    }

    @PostMapping("/reverse")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public OperationResultResponse reverse(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReversalRequest request
    ) {
        return bankingService.reverse(idempotencyKey, request);
    }

    @GetMapping("/transactions/me")
    public Page<TransactionResponse> getMyTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return bankingService.getMyTransactions(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping("/transactions/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('BANKER')")
    public Page<TransactionResponse> getTransactionsByAccountId(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return bankingService.getTransactionsByAccountId(
                accountId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }
}
