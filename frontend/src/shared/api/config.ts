// Single source of truth for all backend endpoints (gateway, context-path /api).
export const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

export const ENDPOINTS = {
  // Auth
  adminLogin: '/auth/admin/login',
  playerLogin: '/auth/player/login',
  me: '/auth/me',
  logout: '/auth/logout',

  // Accounts
  accounts: '/accounts',
  accountMe: '/accounts/me',
  accountById: (id: number | string) => `/accounts/${id}`,
  updateStatus: (id: number | string) => `/accounts/${id}/status`,
  updateRole: (id: number | string) => `/accounts/${id}/role`,

  // Banking
  myBalance: '/banking/balance/me',
  balance: (accountId: number | string) => `/banking/balance/${accountId}`,
  myTransactions: '/banking/transactions/me',
  transactions: (accountId: number | string) => `/banking/transactions/${accountId}`,
  deposit: '/banking/deposit',
  withdraw: '/banking/withdraw',
  transfer: '/banking/transfer',
  reverse: '/banking/reverse',
  massOperation: '/banking/mass-operation',
  pay: '/banking/pay',
  payLookup: '/banking/pay/lookup',
  cryptoRate: '/banking/crypto/rate',
  cryptoBuy: '/banking/crypto/buy',
  cryptoSell: '/banking/crypto/sell',
  cryptoMarket: '/banking/crypto/market',
  cryptoShock: '/banking/crypto/shock',
  creditOverview: '/banking/credit/overview',
  creditPolicy: '/banking/credit/policy',
  openDeposit: '/banking/credit/deposit',
  closeDeposit: '/banking/credit/deposit/close',
  takeLoan: '/banking/credit/loan',
  repayLoan: '/banking/credit/loan/repay',

  // Sessions
  sessionMe: '/sessions/me',
  activeSessions: '/sessions/active',
  terminateSession: (id: number | string) => `/sessions/${id}/terminate`,

  // Pins
  createPin: '/pins',
  revokePin: (id: number | string) => `/pins/${id}/revoke`,
  pinsByAccount: (accountId: number | string) => `/pins/by-account/${accountId}`,

  // Analytics
  analyticsOverview: '/analytics/overview',

  // Audit
  auditLogs: '/audit/logs',
  auditLogById: (id: number | string) => `/audit/logs/${id}`,

  // Terminals
  terminals: '/terminals',
  terminalById: (id: number | string) => `/terminals/${id}`,
  terminalStatus: (id: number | string) => `/terminals/${id}/status`,
  terminalByName: (name: string) => `/terminals/by-name/${name}`,

  // Cards
  cards: '/cards',
  cardById: (id: number | string) => `/cards/${id}`,
  cardsByAccount: (accountId: number | string) => `/cards/by-account/${accountId}`,
  cardBlock: (id: number | string) => `/cards/${id}/block`,
  cardLost: (id: number | string) => `/cards/${id}/lost`,
  cardReplace: (id: number | string) => `/cards/${id}/replace`,
  cardLookup: '/cards/by-uid',

  // Marketplace
  products: '/marketplace/products',
  productById: (id: number | string) => `/marketplace/products/${id}`,
  productStatus: (id: number | string) => `/marketplace/products/${id}/status`,
  productStock: (id: number | string) => `/marketplace/products/${id}/stock`,
  marketOrders: '/marketplace/orders',
  posPurchase: '/marketplace/pos-purchase',
  myOrders: '/marketplace/orders/me',
  orderById: (id: number | string) => `/marketplace/orders/${id}`,
  cancelOrder: (id: number | string) => `/marketplace/orders/${id}/cancel`,
  massPriceOp: '/marketplace/price-op',
  scenarios: '/marketplace/scenarios',
  scenarioById: (id: number | string) => `/marketplace/scenarios/${id}`,
  applyScenario: (id: number | string) => `/marketplace/scenarios/${id}/apply`,

  // Media (shared upload store on the gateway; used by news + marketplace)
  media: '/news/media',

  // News
  news: '/news',
  newsMedia: '/news/media',
  newsById: (id: number | string) => `/news/${id}`,
  newsStatus: (id: number | string) => `/news/${id}/status`,
  newsPin: (id: number | string) => `/news/${id}/pin`,
};
