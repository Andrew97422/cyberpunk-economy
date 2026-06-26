// Карта всех endpoint-ов. Меняй здесь — и везде подтянется.
export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const ENDPOINTS = {
  // Auth
  adminLogin:   '/auth/admin/login',
  logout:       '/auth/logout',
  me:           '/auth/me',

  // Accounts
  accounts:     '/accounts',
  accountById:  (id: number | string) => `/accounts/${id}`,
  accountMe:    '/accounts/me',

  // Account actions
  updateStatus: (id: number | string) => `/accounts/${id}/status`,
  updateRole:   (id: number | string) => `/accounts/${id}/role`,

  // Admin accounts (полная модель с passwordHash и enums)
  adminAccountById: (id: number | string) => `/admin/accounts/${id}`,

  // Sessions
  activeSessions:    '/sessions/active',
  terminateSession:  (id: number | string) => `/sessions/${id}/terminate`,

  // Pins
  createPin: '/admin/pins',
  revokePin: (id: number | string) => `/admin/pins/${id}/revoke`,

  // Banking
  balance:      (accountId: number | string) => `/banking/balance/${accountId}`,
  transactions: (accountId: number | string) => `/banking/transactions/${accountId}`,
  deposit:      '/banking/deposit',
  withdraw:     '/banking/withdraw',
  transfer:     '/banking/transfer',
  adjust:       '/banking/adjust',
  reverse:      '/banking/reverse',
}
