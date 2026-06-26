package ru.andrew.mainserver.banking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.account.entity.Role;
import ru.andrew.mainserver.account.service.AccountService;
import ru.andrew.mainserver.audit.entity.AuditEventType;
import ru.andrew.mainserver.audit.service.AuditService;
import ru.andrew.mainserver.auth.service.CurrentUserService;
import ru.andrew.mainserver.banking.dto.*;
import ru.andrew.mainserver.banking.entity.*;
import ru.andrew.mainserver.banking.repository.BalanceRepository;
import ru.andrew.mainserver.banking.repository.BankTransactionRepository;
import ru.andrew.mainserver.common.exception.BadRequestException;
import ru.andrew.mainserver.common.exception.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class BankingService {

    private final BalanceRepository balanceRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final AccountService accountService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public BalanceResponse getMyBalance() {
        Long accountId = currentUserService.getCurrentAccountId();
        Account account = accountService.getById(accountId);
        return toBalanceResponse(account, getBalance(accountId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalanceByAccountId(Long accountId) {
        Account actor = getActor();
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        Account target = accountService.getById(accountId);
        return toBalanceResponse(target, getBalance(accountId));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getMyTransactions(Pageable pageable) {
        Long accountId = currentUserService.getCurrentAccountId();
        return bankTransactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByAccountId(Long accountId, Pageable pageable) {
        Account actor = getActor();
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        return bankTransactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toTransactionResponse);
    }

    @Transactional
    public OperationResultResponse deposit(String idempotencyKey, DepositRequest request) {
        Account actor = getActor();
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        String fingerprint = idempotencyService.fingerprint("DEPOSIT", request, actor.getId());
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actor.getId(), "DEPOSIT");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doDeposit(actor, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Transactional
    public OperationResultResponse withdraw(String idempotencyKey, WithdrawRequest request) {
        Account actor = getActor();
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        String fingerprint = idempotencyService.fingerprint("WITHDRAW", request, actor.getId());
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actor.getId(), "WITHDRAW");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doWithdraw(actor, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Transactional
    public OperationResultResponse adjust(String idempotencyKey, AdjustmentRequest request) {
        Account actor = getActor();
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        String fingerprint = idempotencyService.fingerprint("ADJUST", request, actor.getId());
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actor.getId(), "ADJUST");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doAdjust(actor, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Transactional
    public OperationResultResponse transfer(String idempotencyKey, TransferRequest request) {
        Account actor = getActor();

        String fingerprint = idempotencyService.fingerprint("TRANSFER", request, actor.getId());
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actor.getId(), "TRANSFER");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doTransfer(actor, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Transactional
    public OperationResultResponse reverse(String idempotencyKey, ReversalRequest request) {
        Account actor = getActor();
        accountService.requireAnyRole(actor, Role.ADMIN, Role.BANKER);

        String fingerprint = idempotencyService.fingerprint("REVERSE", request, actor.getId());
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actor.getId(), "REVERSE");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doReverse(actor, request.getTransactionId(), request.getComment());
        idempotencyService.storeResponse(record, response);
        return response;
    }

    private OperationResultResponse doDeposit(Account actor, DepositRequest request) {
        Account target = accountService.getActiveByPublicName(request.getPublicName());
        CurrencyType currencyType = parseCurrencyType(request.getCurrencyType());
        BigDecimal amount = normalizeAmount(request.getAmount());

        Balance balance = getBalanceForUpdate(target.getId());
        BigDecimal before = getAmount(balance, currencyType);
        BigDecimal after = before.add(amount);

        setAmount(balance, currencyType, after);
        balanceRepository.save(balance);

        BankTransaction tx = new BankTransaction();
        tx.setAccount(target);
        tx.setRelatedAccount(null);
        tx.setType(TransactionType.DEPOSIT);
        tx.setCurrencyType(currencyType);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setComment(request.getComment());
        tx.setCreatedByAccountId(actor.getId());
        bankTransactionRepository.save(tx);

        auditService.log(
                AuditEventType.BALANCE_DEPOSITED,
                actor.getId(),
                "BankTransaction",
                tx.getId(),
                null,
                "Deposit to " + target.getPublicName() + ", amount=" + amount,
                null
        );

        return OperationResultResponse.builder()
                .success(true)
                .operation("DEPOSIT")
                .accountId(target.getId())
                .publicName(target.getPublicName())
                .currencyType(currencyType.name())
                .amount(amount)
                .balanceAfter(after)
                .transactionId(tx.getId())
                .message("Deposit completed")
                .build();
    }

    private OperationResultResponse doWithdraw(Account actor, WithdrawRequest request) {
        Account target = accountService.getActiveByPublicName(request.getPublicName());
        CurrencyType currencyType = parseCurrencyType(request.getCurrencyType());
        BigDecimal amount = normalizeAmount(request.getAmount());

        Balance balance = getBalanceForUpdate(target.getId());
        BigDecimal before = getAmount(balance, currencyType);

        if (before.compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient funds");
        }

        BigDecimal after = before.subtract(amount);
        setAmount(balance, currencyType, after);
        balanceRepository.save(balance);

        BankTransaction tx = new BankTransaction();
        tx.setAccount(target);
        tx.setRelatedAccount(null);
        tx.setType(TransactionType.WITHDRAW);
        tx.setCurrencyType(currencyType);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setComment(request.getComment());
        tx.setCreatedByAccountId(actor.getId());
        bankTransactionRepository.save(tx);

        auditService.log(
                AuditEventType.BALANCE_WITHDRAWN,
                actor.getId(),
                "BankTransaction",
                tx.getId(),
                null,
                "Withdraw from " + target.getPublicName() + ", amount=" + amount,
                null
        );

        return OperationResultResponse.builder()
                .success(true)
                .operation("WITHDRAW")
                .accountId(target.getId())
                .publicName(target.getPublicName())
                .currencyType(currencyType.name())
                .amount(amount)
                .balanceAfter(after)
                .transactionId(tx.getId())
                .message("Withdraw completed")
                .build();
    }

    private OperationResultResponse doAdjust(Account actor, AdjustmentRequest request) {
        Account target = accountService.getActiveByPublicName(request.getPublicName());
        CurrencyType currencyType = parseCurrencyType(request.getCurrencyType());
        BigDecimal amount = normalizeAmount(request.getAmount());
        boolean positive = isPositiveAdjustment(request.getDirection());

        Balance balance = getBalanceForUpdate(target.getId());
        BigDecimal before = getAmount(balance, currencyType);
        BigDecimal after = positive ? before.add(amount) : before.subtract(amount);

        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Adjustment leads to negative balance");
        }

        setAmount(balance, currencyType, after);
        balanceRepository.save(balance);

        BankTransaction tx = new BankTransaction();
        tx.setAccount(target);
        tx.setRelatedAccount(null);
        tx.setType(TransactionType.ADJUSTMENT);
        tx.setCurrencyType(currencyType);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setComment((positive ? "[PLUS] " : "[MINUS] ") + nullableComment(request.getComment()));
        tx.setCreatedByAccountId(actor.getId());
        bankTransactionRepository.save(tx);

        auditService.log(
                AuditEventType.BALANCE_ADJUSTED,
                actor.getId(),
                "BankTransaction",
                tx.getId(),
                null,
                "Balance adjusted for " + target.getPublicName(),
                null
        );

        return OperationResultResponse.builder()
                .success(true)
                .operation("ADJUSTMENT")
                .accountId(target.getId())
                .publicName(target.getPublicName())
                .currencyType(currencyType.name())
                .amount(amount)
                .balanceAfter(after)
                .transactionId(tx.getId())
                .message("Adjustment completed")
                .build();
    }

    private OperationResultResponse doTransfer(Account actor, TransferRequest request) {
        Account from = accountService.getActiveByPublicName(request.getFromPublicName());
        Account to = accountService.getActiveByPublicName(request.getToPublicName());

        if (from.getId().equals(to.getId())) {
            throw new BadRequestException("Cannot transfer to the same account");
        }

        boolean actorAllowed = actor.getRole() == Role.ADMIN
                || actor.getRole() == Role.BANKER
                || actor.getId().equals(from.getId());

        if (!actorAllowed) {
            throw new BadRequestException("Transfer is not allowed");
        }

        CurrencyType currencyType = parseCurrencyType(request.getCurrencyType());
        BigDecimal amount = normalizeAmount(request.getAmount());

        Long firstId = Math.min(from.getId(), to.getId());
        Long secondId = Math.max(from.getId(), to.getId());

        Balance firstLocked = getBalanceForUpdate(firstId);
        Balance secondLocked = getBalanceForUpdate(secondId);

        Balance fromBalance = from.getId().equals(firstId) ? firstLocked : secondLocked;
        Balance toBalance = to.getId().equals(firstId) ? firstLocked : secondLocked;

        BigDecimal fromBefore = getAmount(fromBalance, currencyType);
        BigDecimal toBefore = getAmount(toBalance, currencyType);

        if (fromBefore.compareTo(amount) < 0) {
            BankTransaction rejected = new BankTransaction();
            rejected.setAccount(from);
            rejected.setRelatedAccount(to);
            rejected.setType(TransactionType.TRANSFER_OUT);
            rejected.setCurrencyType(currencyType);
            rejected.setStatus(TransactionStatus.REJECTED);
            rejected.setAmount(amount);
            rejected.setBalanceBefore(fromBefore);
            rejected.setBalanceAfter(fromBefore);
            rejected.setComment("Insufficient funds. " + nullableComment(request.getComment()));
            rejected.setCreatedByAccountId(actor.getId());
            bankTransactionRepository.save(rejected);

            throw new BadRequestException("Insufficient funds");
        }

        BigDecimal fromAfter = fromBefore.subtract(amount);
        BigDecimal toAfter = toBefore.add(amount);

        setAmount(fromBalance, currencyType, fromAfter);
        setAmount(toBalance, currencyType, toAfter);

        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);

        BankTransaction outTx = new BankTransaction();
        outTx.setAccount(from);
        outTx.setRelatedAccount(to);
        outTx.setType(TransactionType.TRANSFER_OUT);
        outTx.setCurrencyType(currencyType);
        outTx.setStatus(TransactionStatus.SUCCESS);
        outTx.setAmount(amount);
        outTx.setBalanceBefore(fromBefore);
        outTx.setBalanceAfter(fromAfter);
        outTx.setComment(request.getComment());
        outTx.setCreatedByAccountId(actor.getId());
        bankTransactionRepository.save(outTx);

        BankTransaction inTx = new BankTransaction();
        inTx.setAccount(to);
        inTx.setRelatedAccount(from);
        inTx.setType(TransactionType.TRANSFER_IN);
        inTx.setCurrencyType(currencyType);
        inTx.setStatus(TransactionStatus.SUCCESS);
        inTx.setAmount(amount);
        inTx.setBalanceBefore(toBefore);
        inTx.setBalanceAfter(toAfter);
        inTx.setComment(request.getComment());
        inTx.setCreatedByAccountId(actor.getId());
        bankTransactionRepository.save(inTx);

        auditService.log(
                AuditEventType.BALANCE_TRANSFERRED,
                actor.getId(),
                "BankTransaction",
                outTx.getId(),
                null,
                "Transfer " + amount + " from " + from.getPublicName() + " to " + to.getPublicName(),
                null
        );

        return OperationResultResponse.builder()
                .success(true)
                .operation("TRANSFER")
                .accountId(from.getId())
                .publicName(from.getPublicName())
                .relatedAccountId(to.getId())
                .relatedPublicName(to.getPublicName())
                .currencyType(currencyType.name())
                .amount(amount)
                .balanceAfter(fromAfter)
                .transactionId(outTx.getId())
                .relatedTransactionId(inTx.getId())
                .message("Transfer completed")
                .build();
    }

    private OperationResultResponse doReverse(Account actor, Long transactionId, String comment) {
        BankTransaction original = bankTransactionRepository.findWithLockById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));

        if (original.getStatus() != TransactionStatus.SUCCESS) {
            throw new BadRequestException("Only successful transaction can be reversed");
        }

        if (original.isReversalApplied()) {
            throw new BadRequestException("Transaction already reversed");
        }

        if (original.getType() == TransactionType.REVERSAL_IN || original.getType() == TransactionType.REVERSAL_OUT) {
            throw new BadRequestException("Reversal transaction cannot be reversed");
        }

        CurrencyType currencyType = original.getCurrencyType();
        BigDecimal amount = original.getAmount();
        Account account = original.getAccount();

        Balance balance = getBalanceForUpdate(account.getId());
        BigDecimal before = getAmount(balance, currencyType);

        BigDecimal after;
        TransactionType reversalType;

        switch (original.getType()) {
            case DEPOSIT, TRANSFER_IN -> {
                if (before.compareTo(amount) < 0) {
                    throw new BadRequestException("Insufficient funds to reverse incoming transaction");
                }
                after = before.subtract(amount);
                reversalType = TransactionType.REVERSAL_OUT;
            }
            case WITHDRAW, TRANSFER_OUT -> {
                after = before.add(amount);
                reversalType = TransactionType.REVERSAL_IN;
            }
            case ADJUSTMENT -> {
                boolean plus = original.getBalanceAfter().compareTo(original.getBalanceBefore()) > 0;
                if (plus) {
                    if (before.compareTo(amount) < 0) {
                        throw new BadRequestException("Insufficient funds to reverse positive adjustment");
                    }
                    after = before.subtract(amount);
                    reversalType = TransactionType.REVERSAL_OUT;
                } else {
                    after = before.add(amount);
                    reversalType = TransactionType.REVERSAL_IN;
                }
            }
            default -> throw new BadRequestException("Unsupported transaction type for reversal");
        }

        setAmount(balance, currencyType, after);
        balanceRepository.save(balance);

        BankTransaction reversal = new BankTransaction();
        reversal.setAccount(account);
        reversal.setRelatedAccount(original.getRelatedAccount());
        reversal.setType(reversalType);
        reversal.setCurrencyType(currencyType);
        reversal.setStatus(TransactionStatus.SUCCESS);
        reversal.setAmount(amount);
        reversal.setBalanceBefore(before);
        reversal.setBalanceAfter(after);
        reversal.setComment("REVERSAL of tx#" + original.getId() + ". " + nullableComment(comment));
        reversal.setCreatedByAccountId(actor.getId());
        reversal.setReversedTransaction(original);
        bankTransactionRepository.save(reversal);

        original.setReversalApplied(true);
        bankTransactionRepository.save(original);

        auditService.log(
                AuditEventType.BALANCE_REVERSED,
                actor.getId(),
                "BankTransaction",
                reversal.getId(),
                null,
                "Reversal created for tx#" + original.getId(),
                null
        );

        return OperationResultResponse.builder()
                .success(true)
                .operation("REVERSAL")
                .accountId(account.getId())
                .publicName(account.getPublicName())
                .relatedAccountId(original.getRelatedAccount() != null ? original.getRelatedAccount().getId() : null)
                .relatedPublicName(original.getRelatedAccount() != null ? original.getRelatedAccount().getPublicName() : null)
                .currencyType(currencyType.name())
                .amount(amount)
                .balanceAfter(after)
                .transactionId(reversal.getId())
                .relatedTransactionId(original.getId())
                .message("Reversal completed")
                .build();
    }

    private Balance getBalance(Long accountId) {
        return balanceRepository.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("Balance not found for account: " + accountId));
    }

    private Balance getBalanceForUpdate(Long accountId) {
        return balanceRepository.findWithLockByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("Balance not found for account: " + accountId));
    }

    private Account getActor() {
        return accountService.getById(currentUserService.getCurrentAccountId());
    }

    private CurrencyType parseCurrencyType(String value) {
        try {
            return CurrencyType.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid currency type: " + value);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("Amount is required");
        }

        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);

        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be positive");
        }

        return normalized;
    }

    private BigDecimal getAmount(Balance balance, CurrencyType currencyType) {
        return switch (currencyType) {
            case CASHLESS -> safe(balance.getCashlessAmount());
            case CRYPTO -> safe(balance.getCryptoAmount());
        };
    }

    private void setAmount(Balance balance, CurrencyType currencyType, BigDecimal value) {
        switch (currencyType) {
            case CASHLESS -> balance.setCashlessAmount(value);
            case CRYPTO -> balance.setCryptoAmount(value);
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isPositiveAdjustment(String direction) {
        if (direction == null) {
            throw new BadRequestException("Adjustment direction is required");
        }

        String value = direction.trim().toUpperCase();
        if ("PLUS".equals(value) || "IN".equals(value) || "CREDIT".equals(value)) {
            return true;
        }
        if ("MINUS".equals(value) || "OUT".equals(value) || "DEBIT".equals(value)) {
            return false;
        }

        throw new BadRequestException("Invalid adjustment direction: " + direction);
    }

    private boolean hasStoredResponse(IdempotencyRecord record) {
        return record.getResponsePayload() != null && !record.getResponsePayload().isBlank();
    }

    private String nullableComment(String comment) {
        return comment == null ? "" : comment;
    }

    private BalanceResponse toBalanceResponse(Account account, Balance balance) {
        return BalanceResponse.builder()
                .accountId(account.getId())
                .publicName(account.getPublicName())
                .cashlessAmount(safe(balance.getCashlessAmount()))
                .cryptoAmount(safe(balance.getCryptoAmount()))
                .build();
    }

    private TransactionResponse toTransactionResponse(BankTransaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .accountId(tx.getAccount().getId())
                .publicName(tx.getAccount().getPublicName())
                .relatedAccountId(tx.getRelatedAccount() != null ? tx.getRelatedAccount().getId() : null)
                .relatedPublicName(tx.getRelatedAccount() != null ? tx.getRelatedAccount().getPublicName() : null)
                .type(tx.getType().name())
                .currencyType(tx.getCurrencyType().name())
                .status(tx.getStatus().name())
                .amount(tx.getAmount())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .comment(tx.getComment())
                .createdByAccountId(tx.getCreatedByAccountId())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}