export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export const endpoints = {
  adminLogin: '/auth/admin/login',
  playerLogin: '/auth/player/login',
  myBalance: '/banking/balance/me',
  balanceByAccountId: (accountId: string | number) => `/banking/balance/${accountId}`,
  myTransactions: '/banking/transactions/me',
  transactionsByAccountId: (accountId: string | number) => `/banking/transactions/${accountId}`,
  deposit: '/banking/deposit',
  withdraw: '/banking/withdraw',
  adjust: '/banking/adjust',
  transfer: '/banking/transfer',
  reverse: '/banking/reverse',
};
