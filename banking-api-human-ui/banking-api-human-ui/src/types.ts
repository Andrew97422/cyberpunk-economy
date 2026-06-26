export type LoginMode = 'admin' | 'player';
export type PageKey = 'home' | 'balance' | 'history' | 'deposit' | 'withdraw' | 'transfer' | 'adjust' | 'reverse';

export interface AuthResponse {
  token?: string;
  accountId?: number;
  publicName?: string;
  role?: string;
  sessionId?: number;
}

export interface BalanceResponse {
  accountId?: number;
  publicName?: string;
  cashlessAmount?: number;
  cryptoAmount?: number;
}

export interface TransactionResponse {
  id?: number;
  accountId?: number;
  publicName?: string;
  relatedAccountId?: number;
  relatedPublicName?: string;
  type?: string;
  currencyType?: string;
  status?: string;
  amount?: number;
  balanceBefore?: number;
  balanceAfter?: number;
  comment?: string;
  createdByAccountId?: number;
  createdAt?: string;
}

export interface TransactionsPageResponse {
  content?: TransactionResponse[];
}

export interface OperationResultResponse {
  success?: boolean;
  operation?: string;
  accountId?: number;
  publicName?: string;
  relatedAccountId?: number;
  relatedPublicName?: string;
  currencyType?: string;
  amount?: number;
  balanceAfter?: number;
  transactionId?: number;
  relatedTransactionId?: number;
  message?: string;
}
