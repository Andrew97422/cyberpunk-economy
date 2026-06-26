package ru.andrew.bankingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.andrew.bankingservice.dto.*;
import ru.andrew.bankingservice.entity.AccountSnapshot;
import ru.andrew.bankingservice.entity.Balance;
import ru.andrew.bankingservice.entity.BankTransaction;
import ru.andrew.bankingservice.entity.Deposit;
import ru.andrew.bankingservice.entity.IdempotencyRecord;
import ru.andrew.bankingservice.entity.Loan;
import ru.andrew.bankingservice.entity.enums.CurrencyType;
import ru.andrew.bankingservice.entity.enums.TransactionStatus;
import ru.andrew.bankingservice.entity.enums.TransactionType;
import ru.andrew.bankingservice.exception.BadRequestException;
import ru.andrew.bankingservice.exception.NotFoundException;
import ru.andrew.bankingservice.repository.AccountSnapshotRepository;
import ru.andrew.bankingservice.repository.BalanceRepository;
import ru.andrew.bankingservice.repository.BankTransactionRepository;
import ru.andrew.bankingservice.repository.DepositRepository;
import ru.andrew.bankingservice.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankingServiceImpl implements BankingService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BANKER = "BANKER";

    private final BalanceRepository balanceRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final AccountSnapshotRepository accountSnapshotRepository;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final BalanceInitializationService balanceInitializationService;
    private final CryptoMarketService cryptoMarketService;
    private final CreditService creditService;
    private final DepositRepository depositRepository;
    private final LoanRepository loanRepository;

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getMyBalance(Long actorId) {
        return toBalanceResponse(actorId, getBalance(actorId));
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalanceByAccountId(Long actorId, String actorRole, Long accountId) {
        requireAdminOrBanker(actorRole);
        AccountSnapshot target = getAccountOrThrow(accountId);
        return toBalanceResponse(target.getId(), getBalance(target.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getMyTransactions(Long actorId, Pageable pageable) {
        return bankTransactionRepository.findByAccountIdOrderByCreatedAtDesc(actorId, pageable)
                .map(this::toTransactionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionsByAccountId(Long actorId, String actorRole, Long accountId, Pageable pageable) {
        requireAdminOrBanker(actorRole);
        return bankTransactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toTransactionResponse);
    }

    @Override
    @Transactional
    public OperationResultResponse deposit(Long actorId, String actorRole, String idempotencyKey, DepositRequest request) {
        requireAdminOrBanker(actorRole);
        String fingerprint = idempotencyService.fingerprint("DEPOSIT", request, actorId);
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actorId, "DEPOSIT");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doDeposit(actorId, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Override
    @Transactional
    public OperationResultResponse withdraw(Long actorId, String actorRole, String idempotencyKey, WithdrawRequest request) {
        requireAdminOrBanker(actorRole);
        String fingerprint = idempotencyService.fingerprint("WITHDRAW", request, actorId);
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actorId, "WITHDRAW");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doWithdraw(actorId, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Override
    @Transactional
    public OperationResultResponse transfer(Long actorId, String actorRole, String idempotencyKey, TransferRequest request) {
        String fingerprint = idempotencyService.fingerprint("TRANSFER", request, actorId);
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actorId, "TRANSFER");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doTransfer(actorId, actorRole, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Override
    @Transactional
    public OperationResultResponse reverse(Long actorId, String actorRole, String idempotencyKey, ReversalRequest request) {
        requireAdminOrBanker(actorRole);
        String fingerprint = idempotencyService.fingerprint("REVERSE", request, actorId);
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actorId, "REVERSE");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doReverse(actorId, request.getTransactionId(), request.getComment());
        idempotencyService.storeResponse(record, response);
        return response;
    }

    @Override
    @Transactional
    public OperationResultResponse purchaseCharge(Long actorId, String actorRole, String idempotencyKey, PurchaseChargeRequest request) {
        String fingerprint = idempotencyService.fingerprint("PURCHASE_CHARGE", request, actorId);
        var record = idempotencyService.reserve(idempotencyKey, fingerprint, actorId, "PURCHASE_CHARGE");

        if (record != null && hasStoredResponse(record)) {
            return idempotencyService.parseResponse(record, OperationResultResponse.class);
        }

        OperationResultResponse response = doPurchaseCharge(actorId, request);
        idempotencyService.storeResponse(record, response);
        return response;
    }

    // ---- Internal operations ----

    private OperationResultResponse doPurchaseCharge(Long actorId, PurchaseChargeRequest request) {
        if (actorId == null) {
            throw new BadRequestException("Authentication required");
        }
        // The buyer always charges their own account.
        AccountSnapshot target = getActiveAccountById(actorId);
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

        String comment = (request.getComment() == null || request.getComment().isBlank())
                ? "Marketplace purchase" : request.getComment();
        BankTransaction tx = newTransaction(target.getId(), null, TransactionType.WITHDRAW, currencyType,
                amount, before, after, comment, actorId);
        bankTransactionRepository.save(tx);

        publishOutboxEvent(tx, target, "balance.withdrawn", "PURCHASE", null, null);

        return OperationResultResponse.builder()
                .success(true)
                .operation("PURCHASE")
                .accountId(target.getId())
                .publicName(target.getPublicName())
                .currencyType(currencyType.name())
                .amount(amount)
                .balanceAfter(after)
                .transactionId(tx.getId())
                .message("Purchase charge completed")
                .build();
    }

    private OperationResultResponse doDeposit(Long actorId, DepositRequest request) {
        AccountSnapshot target = getActiveAccountByPublicName(request.getPublicName());
        CurrencyType currencyType = parseCurrencyType(request.getCurrencyType());
        BigDecimal amount = normalizeAmount(request.getAmount());

        Balance balance = getBalanceForUpdate(target.getId());
        BigDecimal before = getAmount(balance, currencyType);
        BigDecimal after = before.add(amount);
        setAmount(balance, currencyType, after);
        balanceRepository.save(balance);

        BankTransaction tx = newTransaction(target.getId(), null, TransactionType.DEPOSIT, currencyType,
                amount, before, after, request.getComment(), actorId);
        bankTransactionRepository.save(tx);

        publishOutboxEvent(tx, target, "balance.deposited", "DEPOSIT", null, null);

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

    private OperationResultResponse doWithdraw(Long actorId, WithdrawRequest request) {
        AccountSnapshot target = getActiveAccountByPublicName(request.getPublicName());
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

        BankTransaction tx = newTransaction(target.getId(), null, TransactionType.WITHDRAW, currencyType,
                amount, before, after, request.getComment(), actorId);
        bankTransactionRepository.save(tx);

        publishOutboxEvent(tx, target, "balance.withdrawn", "WITHDRAW", null, null);

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

    private OperationResultResponse doTransfer(Long actorId, String actorRole, TransferRequest request) {
        AccountSnapshot from = getActiveAccountByPublicName(request.getFromPublicName());
        AccountSnapshot to = getActiveAccountByPublicName(request.getToPublicName());

        if (from.getId().equals(to.getId())) {
            throw new BadRequestException("Cannot transfer to the same account");
        }

        boolean actorAllowed = ROLE_ADMIN.equals(actorRole) || ROLE_BANKER.equals(actorRole) || actorId.equals(from.getId());
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
            BankTransaction rejected = newTransaction(from.getId(), to.getId(), TransactionType.TRANSFER_OUT,
                    currencyType, amount, fromBefore, fromBefore,
                    "Insufficient funds. " + nullableComment(request.getComment()), actorId);
            rejected.setStatus(TransactionStatus.REJECTED);
            bankTransactionRepository.save(rejected);
            throw new BadRequestException("Insufficient funds");
        }

        BigDecimal fromAfter = fromBefore.subtract(amount);
        BigDecimal toAfter = toBefore.add(amount);

        setAmount(fromBalance, currencyType, fromAfter);
        setAmount(toBalance, currencyType, toAfter);
        balanceRepository.save(fromBalance);
        balanceRepository.save(toBalance);

        BankTransaction outTx = newTransaction(from.getId(), to.getId(), TransactionType.TRANSFER_OUT,
                currencyType, amount, fromBefore, fromAfter, request.getComment(), actorId);
        bankTransactionRepository.save(outTx);

        BankTransaction inTx = newTransaction(to.getId(), from.getId(), TransactionType.TRANSFER_IN,
                currencyType, amount, toBefore, toAfter, request.getComment(), actorId);
        bankTransactionRepository.save(inTx);

        publishOutboxEvent(outTx, from, "balance.transferred", "TRANSFER", to.getId(), to.getPublicName());
        // Emit the recipient side too, so audit shows both parties and analytics can
        // reconstruct every account's current balance from its latest balanceAfter.
        publishOutboxEvent(inTx, to, "balance.transferred", "TRANSFER_IN", from.getId(), from.getPublicName());

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

    private OperationResultResponse doReverse(Long actorId, Long transactionId, String comment) {
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
        Long accountId = original.getAccountId();

        AccountSnapshot accountSnapshot = getAccountOrThrow(accountId);

        Balance balance = getBalanceForUpdate(accountId);
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

        BankTransaction reversal = newTransaction(accountId, original.getRelatedAccountId(), reversalType,
                currencyType, amount, before, after,
                "REVERSAL of tx#" + original.getId() + ". " + nullableComment(comment), actorId);
        reversal.setReversedTransaction(original);
        bankTransactionRepository.save(reversal);

        original.setReversalApplied(true);
        bankTransactionRepository.save(original);

        publishOutboxEvent(reversal, accountSnapshot, "transaction.reversed", "REVERSAL",
                original.getRelatedAccountId(), null);

        return OperationResultResponse.builder()
                .success(true)
                .operation("REVERSAL")
                .accountId(accountId)
                .publicName(accountSnapshot.getPublicName())
                .relatedAccountId(original.getRelatedAccountId())
                .currencyType(currencyType.name())
                .amount(amount)
                .balanceAfter(after)
                .transactionId(reversal.getId())
                .relatedTransactionId(original.getId())
                .message("Reversal completed")
                .build();
    }

    @Override
    @Transactional
    public MassOperationResult massOperation(Long actorId, String actorRole, MassOperationRequest request) {
        requireAdminOrBanker(actorRole);
        CurrencyType currencyType = parseCurrencyType(request.getCurrencyType());
        String mode = request.getMode() == null ? "" : request.getMode().trim().toUpperCase();
        BigDecimal value = request.getValue();
        if (value == null) {
            throw new BadRequestException("value is required");
        }
        value = value.setScale(2, RoundingMode.HALF_UP);
        if (!mode.equals("CREDIT") && !mode.equals("DEBIT") && !mode.equals("MULTIPLY")) {
            throw new BadRequestException("Invalid mode: " + request.getMode());
        }
        if ((mode.equals("CREDIT") || mode.equals("DEBIT")) && value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("value must be positive");
        }
        if (mode.equals("MULTIPLY") && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("multiplier must be >= 0");
        }

        String roleFilter = (request.getTargetRole() == null || request.getTargetRole().isBlank())
                ? null : request.getTargetRole().trim().toUpperCase();
        String comment = (request.getComment() == null || request.getComment().isBlank())
                ? "Экономическое событие" : request.getComment().trim();

        int affected = 0;
        BigDecimal totalDelta = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (AccountSnapshot snap : accountSnapshotRepository.findAll()) {
            if (!STATUS_ACTIVE.equals(snap.getStatus())) {
                continue;
            }
            if (roleFilter != null && !roleFilter.equalsIgnoreCase(snap.getRole())) {
                continue;
            }
            Balance balance = getBalanceForUpdate(snap.getId());
            BigDecimal before = getAmount(balance, currencyType);
            BigDecimal after = switch (mode) {
                case "CREDIT" -> before.add(value);
                case "DEBIT" -> before.subtract(value).max(BigDecimal.ZERO);
                default -> before.multiply(value); // MULTIPLY
            };
            after = after.setScale(2, RoundingMode.HALF_UP);
            if (after.compareTo(before) == 0) {
                continue;
            }
            setAmount(balance, currencyType, after);
            balanceRepository.save(balance);
            BankTransaction tx = newTransaction(snap.getId(), null, TransactionType.ADJUSTMENT, currencyType,
                    after.subtract(before).abs(), before, after, comment, actorId);
            bankTransactionRepository.save(tx);
            publishOutboxEvent(tx, snap, "balance.adjusted", "EVENT", null, null);
            affected++;
            totalDelta = totalDelta.add(after.subtract(before));
        }

        log.info("Mass economic event by actor {}: mode={} currency={} value={} role={} -> affected={} totalDelta={}",
                actorId, mode, currencyType, value, roleFilter, affected, totalDelta);
        return MassOperationResult.builder()
                .success(true)
                .mode(mode)
                .currencyType(currencyType.name())
                .affected(affected)
                .totalDelta(totalDelta)
                .message("Событие применено к " + affected + " счетам")
                .build();
    }

    @Override
    @Transactional
    public CryptoTradeResult buyCrypto(Long actorId, CryptoTradeRequest request) {
        AccountSnapshot account = getActiveAccountById(actorId);
        BigDecimal spend = normalizeAmount(request.amount());
        BigDecimal rate = cryptoMarketService.getCurrentRate();
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Курс недоступен");
        }
        Balance balance = getBalanceForUpdate(account.getId());
        BigDecimal cashBefore = getAmount(balance, CurrencyType.CASHLESS);
        if (cashBefore.compareTo(spend) < 0) {
            throw new BadRequestException("Недостаточно безналичных средств");
        }
        BigDecimal qty = spend.divide(rate, 2, RoundingMode.DOWN);
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма слишком мала для покупки по текущему курсу");
        }
        BigDecimal cryptoBefore = getAmount(balance, CurrencyType.CRYPTO);
        BigDecimal cashAfter = cashBefore.subtract(spend);
        BigDecimal cryptoAfter = cryptoBefore.add(qty);
        setAmount(balance, CurrencyType.CASHLESS, cashAfter);
        setAmount(balance, CurrencyType.CRYPTO, cryptoAfter);
        balanceRepository.save(balance);

        String comment = "Покупка крипты по курсу " + rate;
        BankTransaction cashLeg = newTransaction(account.getId(), null, TransactionType.EXCHANGE,
                CurrencyType.CASHLESS, spend, cashBefore, cashAfter, comment, actorId);
        bankTransactionRepository.save(cashLeg);
        BankTransaction cryptoLeg = newTransaction(account.getId(), null, TransactionType.EXCHANGE,
                CurrencyType.CRYPTO, qty, cryptoBefore, cryptoAfter, comment, actorId);
        bankTransactionRepository.save(cryptoLeg);
        publishOutboxEvent(cashLeg, account, "balance.exchanged", "EXCHANGE_BUY", null, null);
        publishOutboxEvent(cryptoLeg, account, "balance.exchanged", "EXCHANGE_BUY", null, null);

        return CryptoTradeResult.builder()
                .success(true).side("BUY").rate(rate).spent(spend).received(qty)
                .cashlessAfter(cashAfter).cryptoAfter(cryptoAfter)
                .message("Куплено " + qty + " крипты по курсу " + rate)
                .build();
    }

    @Override
    @Transactional
    public CryptoTradeResult sellCrypto(Long actorId, CryptoTradeRequest request) {
        AccountSnapshot account = getActiveAccountById(actorId);
        BigDecimal qty = normalizeAmount(request.amount());
        BigDecimal rate = cryptoMarketService.getCurrentRate();
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Курс недоступен");
        }
        Balance balance = getBalanceForUpdate(account.getId());
        BigDecimal cryptoBefore = getAmount(balance, CurrencyType.CRYPTO);
        if (cryptoBefore.compareTo(qty) < 0) {
            throw new BadRequestException("Недостаточно крипты");
        }
        BigDecimal proceeds = qty.multiply(rate).setScale(2, RoundingMode.DOWN);
        if (proceeds.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Сумма слишком мала для продажи по текущему курсу");
        }
        BigDecimal cashBefore = getAmount(balance, CurrencyType.CASHLESS);
        BigDecimal cryptoAfter = cryptoBefore.subtract(qty);
        BigDecimal cashAfter = cashBefore.add(proceeds);
        setAmount(balance, CurrencyType.CRYPTO, cryptoAfter);
        setAmount(balance, CurrencyType.CASHLESS, cashAfter);
        balanceRepository.save(balance);

        String comment = "Продажа крипты по курсу " + rate;
        BankTransaction cryptoLeg = newTransaction(account.getId(), null, TransactionType.EXCHANGE,
                CurrencyType.CRYPTO, qty, cryptoBefore, cryptoAfter, comment, actorId);
        bankTransactionRepository.save(cryptoLeg);
        BankTransaction cashLeg = newTransaction(account.getId(), null, TransactionType.EXCHANGE,
                CurrencyType.CASHLESS, proceeds, cashBefore, cashAfter, comment, actorId);
        bankTransactionRepository.save(cashLeg);
        publishOutboxEvent(cryptoLeg, account, "balance.exchanged", "EXCHANGE_SELL", null, null);
        publishOutboxEvent(cashLeg, account, "balance.exchanged", "EXCHANGE_SELL", null, null);

        return CryptoTradeResult.builder()
                .success(true).side("SELL").rate(rate).spent(qty).received(proceeds)
                .cashlessAfter(cashAfter).cryptoAfter(cryptoAfter)
                .message("Продано " + qty + " крипты за " + proceeds)
                .build();
    }

    @Override
    @Transactional
    public CreditOverviewResponse openDeposit(Long actorId, CreditActionRequest request) {
        AccountSnapshot acc = getActiveAccountById(actorId);
        BigDecimal amt = normalizeAmount(request.amount());
        Balance bal = getBalanceForUpdate(acc.getId());
        BigDecimal before = getAmount(bal, CurrencyType.CASHLESS);
        if (before.compareTo(amt) < 0) {
            throw new BadRequestException("Недостаточно средств для вклада");
        }
        BigDecimal after = before.subtract(amt);
        setAmount(bal, CurrencyType.CASHLESS, after);
        balanceRepository.save(bal);
        BankTransaction tx = newTransaction(acc.getId(), null, TransactionType.WITHDRAW, CurrencyType.CASHLESS,
                amt, before, after, "Открытие вклада", actorId);
        bankTransactionRepository.save(tx);
        publishOutboxEvent(tx, acc, "balance.withdrawn", "DEPOSIT_OPEN", null, null);

        Deposit d = new Deposit();
        d.setAccountId(acc.getId());
        d.setPublicName(acc.getPublicName());
        d.setPrincipal(amt);
        d.setCurrentAmount(amt);
        Instant now = Instant.now();
        d.setOpenedAt(now);
        d.setLastAccruedAt(now);
        d.setStatus(CreditService.ACTIVE);
        depositRepository.save(d);
        return creditService.overview(actorId);
    }

    @Override
    @Transactional
    public CreditOverviewResponse closeDeposit(Long actorId, CreditActionRequest request) {
        Long depositId = request.depositId() == null ? -1L : request.depositId();
        Deposit d = depositRepository.findById(depositId)
                .orElseThrow(() -> new NotFoundException("Вклад не найден"));
        if (!d.getAccountId().equals(actorId)) {
            throw new BadRequestException("Это не ваш вклад");
        }
        if (!CreditService.ACTIVE.equals(d.getStatus())) {
            throw new BadRequestException("Вклад уже закрыт");
        }
        AccountSnapshot acc = getActiveAccountById(actorId);
        BigDecimal value = d.getCurrentAmount();
        Balance bal = getBalanceForUpdate(acc.getId());
        BigDecimal before = getAmount(bal, CurrencyType.CASHLESS);
        BigDecimal after = before.add(value);
        setAmount(bal, CurrencyType.CASHLESS, after);
        balanceRepository.save(bal);
        BankTransaction tx = newTransaction(acc.getId(), null, TransactionType.DEPOSIT, CurrencyType.CASHLESS,
                value, before, after, "Закрытие вклада (с процентами)", actorId);
        bankTransactionRepository.save(tx);
        publishOutboxEvent(tx, acc, "balance.deposited", "DEPOSIT_CLOSE", null, null);

        d.setStatus(CreditService.CLOSED);
        d.setClosedAt(Instant.now());
        depositRepository.save(d);
        return creditService.overview(actorId);
    }

    @Override
    @Transactional
    public CreditOverviewResponse takeLoan(Long actorId, CreditActionRequest request) {
        AccountSnapshot acc = getActiveAccountById(actorId);
        BigDecimal amt = normalizeAmount(request.amount());
        BigDecimal maxLoan = creditService.getOrCreatePolicy().getMaxLoan();
        if (amt.compareTo(maxLoan) > 0) {
            throw new BadRequestException("Превышен лимит кредита: " + maxLoan);
        }
        Balance bal = getBalanceForUpdate(acc.getId());
        BigDecimal before = getAmount(bal, CurrencyType.CASHLESS);
        BigDecimal after = before.add(amt);
        setAmount(bal, CurrencyType.CASHLESS, after);
        balanceRepository.save(bal);
        BankTransaction tx = newTransaction(acc.getId(), null, TransactionType.DEPOSIT, CurrencyType.CASHLESS,
                amt, before, after, "Получение кредита", actorId);
        bankTransactionRepository.save(tx);
        publishOutboxEvent(tx, acc, "balance.deposited", "LOAN_TAKE", null, null);

        Loan l = new Loan();
        l.setAccountId(acc.getId());
        l.setPublicName(acc.getPublicName());
        l.setPrincipal(amt);
        l.setDebt(amt);
        Instant now = Instant.now();
        l.setOpenedAt(now);
        l.setLastAccruedAt(now);
        l.setStatus(CreditService.ACTIVE);
        loanRepository.save(l);
        return creditService.overview(actorId);
    }

    @Override
    @Transactional
    public CreditOverviewResponse repayLoan(Long actorId, CreditActionRequest request) {
        Long loanId = request.loanId() == null ? -1L : request.loanId();
        Loan l = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Кредит не найден"));
        if (!l.getAccountId().equals(actorId)) {
            throw new BadRequestException("Это не ваш кредит");
        }
        if (!CreditService.ACTIVE.equals(l.getStatus())) {
            throw new BadRequestException("Кредит уже погашен");
        }
        BigDecimal requested = normalizeAmount(request.amount());
        BigDecimal pay = requested.min(l.getDebt());
        AccountSnapshot acc = getActiveAccountById(actorId);
        Balance bal = getBalanceForUpdate(acc.getId());
        BigDecimal before = getAmount(bal, CurrencyType.CASHLESS);
        if (before.compareTo(pay) < 0) {
            throw new BadRequestException("Недостаточно средств для погашения");
        }
        BigDecimal after = before.subtract(pay);
        setAmount(bal, CurrencyType.CASHLESS, after);
        balanceRepository.save(bal);
        BankTransaction tx = newTransaction(acc.getId(), null, TransactionType.WITHDRAW, CurrencyType.CASHLESS,
                pay, before, after, "Погашение кредита", actorId);
        bankTransactionRepository.save(tx);
        publishOutboxEvent(tx, acc, "balance.withdrawn", "LOAN_REPAY", null, null);

        BigDecimal newDebt = l.getDebt().subtract(pay);
        l.setDebt(newDebt);
        if (newDebt.compareTo(BigDecimal.ZERO) <= 0) {
            l.setStatus(CreditService.REPAID);
            l.setClosedAt(Instant.now());
        }
        loanRepository.save(l);
        return creditService.overview(actorId);
    }

    // ---- Helpers ----

    private BankTransaction newTransaction(Long accountId, Long relatedAccountId, TransactionType type,
                                           CurrencyType currencyType, BigDecimal amount,
                                           BigDecimal before, BigDecimal after,
                                           String comment, Long createdBy) {
        BankTransaction tx = new BankTransaction();
        tx.setAccountId(accountId);
        tx.setRelatedAccountId(relatedAccountId);
        tx.setType(type);
        tx.setCurrencyType(currencyType);
        tx.setStatus(TransactionStatus.SUCCESS);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setComment(comment);
        tx.setCreatedByAccountId(createdBy);
        return tx;
    }

    private Balance getBalance(Long accountId) {
        return balanceRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    balanceInitializationService.createBalanceIfAbsent(accountId);
                    return balanceRepository.findByAccountId(accountId)
                            .orElseThrow(() -> new NotFoundException("Balance not found for account: " + accountId));
                });
    }

    private Balance getBalanceForUpdate(Long accountId) {
        balanceRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    balanceInitializationService.createBalanceIfAbsent(accountId);
                    return balanceRepository.findByAccountId(accountId).orElse(null);
                });
        return balanceRepository.findWithLockByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("Balance not found for account: " + accountId));
    }

    private AccountSnapshot getAccountOrThrow(Long accountId) {
        return accountSnapshotRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
    }

    private AccountSnapshot getActiveAccountById(Long accountId) {
        AccountSnapshot snapshot = getAccountOrThrow(accountId);
        if (!STATUS_ACTIVE.equals(snapshot.getStatus())) {
            throw new BadRequestException("Account is not active");
        }
        return snapshot;
    }

    private AccountSnapshot getActiveAccountByPublicName(String publicName) {
        AccountSnapshot snapshot = accountSnapshotRepository.findByPublicNameIgnoreCase(publicName)
                .orElseThrow(() -> new NotFoundException("Account not found: " + publicName));
        if (!STATUS_ACTIVE.equals(snapshot.getStatus())) {
            throw new BadRequestException("Account is not active");
        }
        return snapshot;
    }

    private void requireAdminOrBanker(String role) {
        if (!ROLE_ADMIN.equals(role) && !ROLE_BANKER.equals(role)) {
            throw new BadRequestException("Insufficient privileges");
        }
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
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasStoredResponse(IdempotencyRecord record) {
        return record.getResponsePayload() != null && !record.getResponsePayload().isBlank();
    }

    private String nullableComment(String comment) {
        return comment == null ? "" : comment;
    }

    private BalanceResponse toBalanceResponse(Long accountId, Balance balance) {
        String publicName = accountSnapshotRepository.findById(accountId)
                .map(AccountSnapshot::getPublicName)
                .orElse(null);
        return BalanceResponse.builder()
                .accountId(accountId)
                .publicName(publicName)
                .cashlessAmount(safe(balance.getCashlessAmount()))
                .cryptoAmount(safe(balance.getCryptoAmount()))
                .build();
    }

    private TransactionResponse toTransactionResponse(BankTransaction tx) {
        String publicName = accountSnapshotRepository.findById(tx.getAccountId())
                .map(AccountSnapshot::getPublicName)
                .orElse(null);
        String relatedPublicName = null;
        if (tx.getRelatedAccountId() != null) {
            relatedPublicName = accountSnapshotRepository.findById(tx.getRelatedAccountId())
                    .map(AccountSnapshot::getPublicName)
                    .orElse(null);
        }
        return TransactionResponse.builder()
                .id(tx.getId())
                .accountId(tx.getAccountId())
                .publicName(publicName)
                .relatedAccountId(tx.getRelatedAccountId())
                .relatedPublicName(relatedPublicName)
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

    private void publishOutboxEvent(BankTransaction tx, AccountSnapshot account, String eventType, String operation,
                                    Long relatedAccountId, String relatedPublicName) {
        try {
            outboxService.addEvent(
                    "BALANCE",
                    account.getId().toString(),
                    eventType,
                    BankingEventPayload.builder()
                            .transactionId(tx.getId())
                            .accountId(account.getId())
                            .publicName(account.getPublicName())
                            .relatedAccountId(relatedAccountId)
                            .relatedPublicName(relatedPublicName)
                            .currencyType(tx.getCurrencyType().name())
                            .operation(operation)
                            .amount(tx.getAmount())
                            .balanceAfter(tx.getBalanceAfter())
                            .actorId(tx.getCreatedByAccountId())
                            .occurredAt(LocalDateTime.ofInstant(tx.getCreatedAt(), ZoneId.systemDefault()))
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to publish outbox event for tx {}: {}", tx.getId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
