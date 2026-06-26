package ru.andrew.bankingservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.andrew.bankingservice.dto.*;

public interface BankingService {

    BalanceResponse getMyBalance(Long actorId);

    BalanceResponse getBalanceByAccountId(Long actorId, String actorRole, Long accountId);

    Page<TransactionResponse> getMyTransactions(Long actorId, Pageable pageable);

    Page<TransactionResponse> getTransactionsByAccountId(Long actorId, String actorRole, Long accountId, Pageable pageable);

    OperationResultResponse deposit(Long actorId, String actorRole, String idempotencyKey, DepositRequest request);

    OperationResultResponse withdraw(Long actorId, String actorRole, String idempotencyKey, WithdrawRequest request);

    OperationResultResponse transfer(Long actorId, String actorRole, String idempotencyKey, TransferRequest request);

    OperationResultResponse reverse(Long actorId, String actorRole, String idempotencyKey, ReversalRequest request);

    OperationResultResponse purchaseCharge(Long actorId, String actorRole, String idempotencyKey, PurchaseChargeRequest request);

    MassOperationResult massOperation(Long actorId, String actorRole, MassOperationRequest request);

    CryptoTradeResult buyCrypto(Long actorId, CryptoTradeRequest request);

    CryptoTradeResult sellCrypto(Long actorId, CryptoTradeRequest request);

    CreditOverviewResponse openDeposit(Long actorId, CreditActionRequest request);

    CreditOverviewResponse closeDeposit(Long actorId, CreditActionRequest request);

    CreditOverviewResponse takeLoan(Long actorId, CreditActionRequest request);

    CreditOverviewResponse repayLoan(Long actorId, CreditActionRequest request);
}