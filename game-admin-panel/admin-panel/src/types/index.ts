// ---- Auth ----
export interface AdminLoginRequest {
  publicName: string
  password: string
}

export interface AuthResponse {
  token: string
  accountId: number
  publicName: string
  role: string
  sessionId: number
}

export interface MeResponse {
  accountId: number
  publicName: string
  role: string
  sessionId: number
}

// ---- Accounts ----
export interface AccountResponse {
  id: number
  publicName: string
  characterName?: string
  role: string
  status: string
  notes?: string
  createdAt: string
  updatedAt: string
}

// Полная модель из /admin/accounts/:id
export interface AccountFull extends AccountResponse {
  passwordHash?: string
}

export interface CreateAccountRequest {
  publicName: string
  characterName?: string
  role: string       // PLAYER | BANKER | ADMIN | DEVELOPER
  status: string     // ACTIVE | BLOCKED | ARCHIVED
  password?: string
  notes?: string
}

export interface UpdateAccountStatusRequest {
  status: string
}

export interface UpdateAccountRoleRequest {
  role: string
}

// Нет отдельного endpoint смены пароля в спеке — реализовано через createPin / adminAccountById
// Если бекенд добавит PATCH /accounts/:id — добавь здесь
export interface ChangePasswordRequest {
  password: string
  confirmPassword: string
}

// ---- Paging ----
export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number   // текущая страница (0-indexed)
  size: number
  first: boolean
  last: boolean
}

// ---- Sessions ----
export interface SessionResponse {
  id: number
  accountId: number
  publicName: string
  role: string
  pinCodeId?: number
  terminalName?: string
  status: string
  durationMinutes: number
  startedAt: string
  endedAt?: string
  lastSeenAt?: string
}

// ---- Balance ----
export interface BalanceResponse {
  accountId: number
  publicName: string
  cashlessAmount: number
  cryptoAmount: number
}

// ---- Transactions ----
export interface TransactionResponse {
  id: number
  accountId: number
  publicName: string
  relatedAccountId?: number
  relatedPublicName?: string
  type: string
  currencyType: string
  status: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  comment?: string
  createdByAccountId?: number
  createdAt: string
}

// ---- Pins ----
export interface PinResponse {
  id: number
  accountId: number
  publicName: string
  status: string
  durationMinutes: number
  createdAt: string
  usedAt?: string
  comment?: string
}

export interface CreatePinRequest {
  publicName: string
  rawPin: string
  durationMinutes: number  // 1-1440
  comment?: string
}

// ---- Enums (реальные из OpenAPI) ----
export const ACCOUNT_ROLES = ['PLAYER', 'BANKER', 'ADMIN', 'DEVELOPER'] as const
export const ACCOUNT_STATUSES = ['ACTIVE', 'BLOCKED', 'ARCHIVED'] as const

export type AccountRole = typeof ACCOUNT_ROLES[number]
export type AccountStatus = typeof ACCOUNT_STATUSES[number]
