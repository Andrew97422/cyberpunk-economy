export function formatDate(iso?: string): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatMoney(value?: number): string {
  if (typeof value !== 'number') return '—';
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
}

export function extractError(err: unknown): string {
  if (!err) return 'Неизвестная ошибка';
  const e = err as {
    response?: { data?: { message?: string; error?: string } };
    message?: string;
  };
  return (
    e?.response?.data?.message ||
    e?.response?.data?.error ||
    e?.message ||
    'Произошла ошибка. Попробуйте ещё раз.'
  );
}

export const ROLE_LABELS: Record<string, string> = {
  PLAYER: 'Игрок',
  BANKER: 'Банкир',
  ADMIN: 'Администратор',
  DEVELOPER: 'Разработчик',
};

export const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Активен',
  BLOCKED: 'Заблокирован',
  ARCHIVED: 'Архив',
};

/** Generate a random numeric PIN (default 8 digits) using the crypto RNG. */
export function generatePin(length = 8): string {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => String(b % 10)).join('');
}

export const PIN_STATUS_LABELS: Record<string, string> = {
  CREATED: 'Активен',
  USED: 'Использован',
  REVOKED: 'Отозван',
  EXPIRED: 'Истёк',
};

export const CARD_STATUS_LABELS: Record<string, string> = {
  ISSUED: 'Активна',
  BLOCKED: 'Заблокирована',
  LOST: 'Утеряна',
  REPLACED: 'Заменена',
};

export const TERMINAL_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Активен',
  MAINTENANCE: 'Обслуживание',
  BLOCKED: 'Заблокирован',
  DECOMMISSIONED: 'Списан',
};

export const PRODUCT_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'В продаже',
  HIDDEN: 'Скрыт',
  ARCHIVED: 'Архив',
};

export const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING_PAYMENT: 'Ожидает оплаты',
  PAID: 'Оплачен',
  CANCELLED: 'Отменён',
};

export const NEWS_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Черновик',
  PUBLISHED: 'Опубликовано',
  ARCHIVED: 'Архив',
};

// ===== Audit: friendly Russian labels for sources & event types =====
export const AUDIT_SOURCE_LABELS: Record<string, string> = {
  'main-server.auth': 'Аутентификация',
  'main-server.banking': 'Банк',
  'account-service': 'Аккаунты',
  'access-service': 'Доступ (PIN / сессии)',
  'card-service': 'Карты',
  'terminal-service': 'Терминалы',
  'marketplace-service': 'Маркетплейс',
  'news-service': 'Новости',
};

/** Sources offered in the filter dropdown (codes are the technical values sent to the API). */
export const AUDIT_SOURCES = [
  'main-server.auth',
  'account-service',
  'access-service',
  'main-server.banking',
  'card-service',
  'terminal-service',
];

export const AUDIT_EVENT_TYPE_LABELS: Record<string, string> = {
  'admin.login_success': 'Вход сотрудника: успех',
  'admin.login_failed': 'Вход сотрудника: неудача',
  'player.login_success': 'Вход игрока: успех',
  'player.login_failed': 'Вход игрока: неудача',
  'session.opened': 'Сессия открыта',
  'session.terminated': 'Сессия завершена',
  'session.expired': 'Сессия истекла',
  'session.logged_out': 'Выход из сессии',
  'session.logout_requested': 'Запрошен выход',
  'account.created': 'Аккаунт создан',
  'account.role_changed': 'Роль изменена',
  'account.status_changed': 'Статус аккаунта изменён',
  'balance.deposited': 'Пополнение',
  'balance.withdrawn': 'Списание',
  'balance.transferred': 'Перевод',
  'balance.adjusted': 'Экономическое событие',
  'balance.exchanged': 'Обмен крипты',
  'transaction.reversed': 'Операция отменена',
  'card.issued': 'Карта выпущена',
  'card.blocked': 'Карта заблокирована',
  'card.lost': 'Карта утеряна',
  'card.replaced': 'Карта заменена',
  'terminal.registered': 'Терминал зарегистрирован',
  'terminal.updated': 'Терминал обновлён',
  'terminal.blocked': 'Терминал заблокирован',
};

/** Grouped event types for the filter dropdown (optgroups). */
export const AUDIT_EVENT_TYPE_GROUPS: { group: string; types: string[] }[] = [
  { group: 'Аутентификация', types: ['admin.login_success', 'admin.login_failed', 'player.login_success', 'player.login_failed'] },
  { group: 'Сессии', types: ['session.opened', 'session.terminated', 'session.expired', 'session.logged_out', 'session.logout_requested'] },
  { group: 'Аккаунты', types: ['account.created', 'account.role_changed', 'account.status_changed'] },
  { group: 'Банк', types: ['balance.deposited', 'balance.withdrawn', 'balance.transferred', 'balance.adjusted', 'balance.exchanged', 'transaction.reversed'] },
  { group: 'Карты', types: ['card.issued', 'card.blocked', 'card.lost', 'card.replaced'] },
  { group: 'Терминалы', types: ['terminal.registered', 'terminal.updated', 'terminal.blocked'] },
];

export function getCurrencyLabel(value?: string): string {
  if (!value) return '—';
  if (value === 'CASHLESS') return 'Безналичные';
  if (value === 'CRYPTO') return 'Крипта';
  return value;
}

export function getTransactionTypeLabel(type?: string): string {
  switch (type) {
    case 'DEPOSIT':
      return 'Пополнение';
    case 'WITHDRAW':
      return 'Списание';
    case 'TRANSFER_IN':
      return 'Перевод (входящий)';
    case 'TRANSFER_OUT':
      return 'Перевод (исходящий)';
    case 'ADJUSTMENT':
      return 'Корректировка';
    case 'EXCHANGE':
      return 'Обмен крипты';
    case 'REVERSAL_IN':
      return 'Отмена (возврат)';
    case 'REVERSAL_OUT':
      return 'Отмена (списание)';
    default:
      return type || 'Операция';
  }
}

export function getOperationSuccessMessage(operation?: string): string {
  switch (operation) {
    case 'DEPOSIT':
      return 'Баланс игрока успешно пополнен';
    case 'WITHDRAW':
      return 'Деньги у игрока успешно списаны';
    case 'TRANSFER':
      return 'Перевод между игроками выполнен';
    case 'REVERSAL':
      return 'Операция успешно отменена';
    default:
      return 'Операция выполнена успешно';
  }
}

const OPERATOR_ROLES = new Set(['ADMIN', 'BANKER', 'DEVELOPER']);
const BANKING_ROLES = new Set(['ADMIN', 'BANKER']);

/** Full admin (terminal management, etc.). */
export function isAdmin(role?: string): boolean {
  return role === 'ADMIN';
}

/** Can manage accounts / see the admin section. */
export function isOperator(role?: string): boolean {
  return !!role && OPERATOR_ROLES.has(role);
}

/** Can perform banking operations (deposit/withdraw/...). */
export function canBank(role?: string): boolean {
  return !!role && BANKING_ROLES.has(role);
}
