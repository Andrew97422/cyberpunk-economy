export function formatMoney(value?: number) {
  if (typeof value !== 'number') return '—';
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
}

export function formatDate(value?: string) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ru-RU');
}

export function getTransactionTypeLabel(type?: string) {
  switch (type) {
    case 'DEPOSIT': return 'Пополнение';
    case 'WITHDRAW': return 'Списание';
    case 'TRANSFER': return 'Перевод';
    case 'ADJUST': return 'Корректировка';
    case 'REVERSE': return 'Отмена операции';
    default: return type || 'Операция';
  }
}

export function getCurrencyLabel(value?: string) {
  if (!value) return '—';
  if (value === 'CASHLESS') return 'Безналичные';
  if (value === 'CRYPTO') return 'Крипта';
  return value;
}

export function getSuccessMessage(operation?: string) {
  switch (operation) {
    case 'deposit': return 'Баланс игрока успешно пополнен';
    case 'withdraw': return 'Деньги у игрока успешно списаны';
    case 'transfer': return 'Перевод между игроками выполнен';
    case 'adjust': return 'Баланс игрока успешно скорректирован';
    case 'reverse': return 'Операция успешно отменена';
    default: return 'Операция выполнена успешно';
  }
}
