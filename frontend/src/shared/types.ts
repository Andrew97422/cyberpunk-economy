// ============ Auth ============
export interface AdminLoginRequest {
  publicName: string;
  password: string;
}

export interface PlayerPinLoginRequest {
  pin: string;
}

export interface AuthResponse {
  token: string;
  accountId: number;
  publicName: string;
  role: string;
  sessionId: number;
}

export interface MeResponse {
  accountId: number;
  publicName: string;
  role: string;
  sessionId: number;
}

export interface AuthUser {
  accountId: number;
  publicName: string;
  role: string;
  sessionId?: number;
}

// ============ Accounts ============
export interface AccountResponse {
  id: number;
  publicName: string;
  characterName?: string;
  role: string;
  status: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  publicName: string;
  characterName?: string;
  role: string;
  status: string;
  password?: string;
  notes?: string;
}

// ============ Paging ============
export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// ============ Sessions ============
export interface SessionResponse {
  id: number;
  accountId: number;
  publicName: string;
  role: string;
  pinCodeId?: number;
  terminalName?: string;
  status: string;
  durationMinutes: number;
  startedAt: string;
  endedAt?: string;
  lastSeenAt?: string;
}

// ============ Pins ============
export interface PinResponse {
  id: number;
  accountId: number;
  publicName: string;
  status: string;
  durationMinutes: number;
  createdAt: string;
  usedAt?: string;
  comment?: string;
}

// ============ Terminals ============
export interface TerminalResponse {
  id: number;
  name: string;
  ipAddress?: string;
  location?: string;
  terminalType?: string;
  status: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export const TERMINAL_STATUSES = ['ACTIVE', 'MAINTENANCE', 'BLOCKED', 'DECOMMISSIONED'] as const;
export type TerminalStatus = (typeof TERMINAL_STATUSES)[number];

// ============ Audit ============
export interface AuditLogResponse {
  id: number;
  eventId?: string;
  eventType?: string;
  eventSource?: string;
  aggregateType?: string;
  aggregateId?: string;
  actorAccountId?: number;
  actorPublicName?: string;
  actorRole?: string;
  targetEntityType?: string;
  targetEntityId?: string;
  terminalId?: number;
  terminalName?: string;
  message?: string;
  payloadJson?: string;
  occurredAt?: string;
  createdAt?: string;
}

// ============ Cards ============
export interface CardResponse {
  id: number;
  accountId: number;
  publicName?: string;
  accountRole?: string;
  accountStatus?: string;
  cardUid: string;
  status: string;
  issuedAt?: string;
  issuedByAccountId?: number;
  blockedAt?: string;
  blockedByAccountId?: number;
  blockedReason?: string;
  replacedByCardId?: number;
  notes?: string;
}

export interface CardLookupResponse {
  cardId?: number;
  cardUid?: string;
  cardStatus?: string;
  accountId?: number;
  publicName?: string;
  accountRole?: string;
  accountStatus?: string;
}

export const CARD_STATUSES = ['ISSUED', 'BLOCKED', 'LOST', 'REPLACED'] as const;
export type CardStatus = (typeof CARD_STATUSES)[number];

// ============ Balance ============
export interface BalanceResponse {
  accountId?: number;
  publicName?: string;
  cashlessAmount?: number;
  cryptoAmount?: number;
}

// ============ Transactions ============
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

// ============ Marketplace ============
export interface ProductResponse {
  id: number;
  sku: string;
  name: string;
  description?: string;
  price: number;
  currencyType: string;
  stockQuantity?: number | null;
  status: string;
  category?: string;
  imageUrl?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OrderResponse {
  id: number;
  productId: number;
  productName: string;
  buyerAccountId: number;
  buyerPublicName?: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  currencyType: string;
  status: string;
  transactionId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export const PRODUCT_STATUSES = ['ACTIVE', 'HIDDEN', 'ARCHIVED'] as const;
export type ProductStatus = (typeof PRODUCT_STATUSES)[number];

export const ORDER_STATUSES = ['PENDING_PAYMENT', 'PAID', 'CANCELLED'] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

// ============ News ============
export interface NewsResponse {
  id: number;
  title: string;
  summary?: string;
  body: string;
  category?: string;
  pinned: boolean;
  status: string;
  coverImageUrl?: string;
  galleryUrls?: string[];
  publishAt?: string;
  publishedAt?: string;
  authorAccountId?: number;
  authorPublicName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export const NEWS_STATUSES = ['DRAFT', 'PUBLISHED', 'ARCHIVED'] as const;
export type NewsStatus = (typeof NEWS_STATUSES)[number];

// ============ Enums ============
export const ACCOUNT_ROLES = ['PLAYER', 'BANKER', 'ADMIN', 'DEVELOPER'] as const;
export const ACCOUNT_STATUSES = ['ACTIVE', 'BLOCKED', 'ARCHIVED'] as const;
export const CURRENCY_TYPES = ['CASHLESS', 'CRYPTO'] as const;

export type AccountRole = (typeof ACCOUNT_ROLES)[number];
export type AccountStatus = (typeof ACCOUNT_STATUSES)[number];
export type CurrencyType = (typeof CURRENCY_TYPES)[number];
