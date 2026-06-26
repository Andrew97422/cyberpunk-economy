export function formatDate(iso?: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export function extractError(err: unknown): string {
  if (!err) return 'Неизвестная ошибка'
  const e = err as { response?: { data?: { message?: string; error?: string } }; message?: string }
  return e?.response?.data?.message
    || e?.response?.data?.error
    || e?.message
    || 'Произошла ошибка. Попробуйте ещё раз.'
}

export const ROLE_LABELS: Record<string, string> = {
  PLAYER: 'Игрок',
  BANKER: 'Банкир',
  ADMIN: 'Администратор',
  DEVELOPER: 'Разработчик',
}

export const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Активен',
  BLOCKED: 'Заблокирован',
  ARCHIVED: 'Архив',
}
