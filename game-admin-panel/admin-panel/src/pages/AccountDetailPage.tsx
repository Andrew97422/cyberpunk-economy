import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { AccountResponse, BalanceResponse, SessionResponse, PagedResponse } from '../types'
import { formatDate, extractError, ROLE_LABELS, STATUS_LABELS } from '../utils/helpers'
import StatusBadge from '../components/StatusBadge'
import LoadingBlock from '../components/LoadingBlock'
import ErrorBlock from '../components/ErrorBlock'
import PageSection from '../components/PageSection'

export default function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [balance, setBalance] = useState<BalanceResponse | null>(null)
  const [sessions, setSessions] = useState<SessionResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState('')

  useEffect(() => {
    if (!id) return
    setLoading(true)
    Promise.all([
      client.get<AccountResponse>(ENDPOINTS.accountById(id)),
      client.get<BalanceResponse>(ENDPOINTS.balance(id)).catch(() => null),
      client.get<PagedResponse<SessionResponse>>(ENDPOINTS.activeSessions, { params: { page: 0, size: 5 } }).catch(() => null),
    ]).then(([accRes, balRes, sesRes]) => {
      setAccount(accRes.data)
      if (balRes) setBalance(balRes.data)
      if (sesRes) {
        // фильтруем по accountId чтобы показывать только сессии этого аккаунта
        const filtered = sesRes.data.content.filter(s => String(s.accountId) === String(id))
        setSessions(filtered)
      }
    }).catch(err => setError(extractError(err)))
    .finally(() => setLoading(false))
  }, [id])

  if (loading) return <div className="page"><LoadingBlock /></div>
  if (error)   return <div className="page"><ErrorBlock message={error} /></div>
  if (!account) return <div className="page"><p>Аккаунт не найден</p></div>

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <Link to="/accounts" className="back-link">← Все аккаунты</Link>
          <h1 className="page-title">{account.publicName}</h1>
        </div>
        <div className="btn-group">
          <Link to={`/accounts/${id}/edit`} className="btn btn-secondary">Редактировать</Link>
          <Link to={`/accounts/${id}/status`} className="btn btn-secondary">Изменить статус</Link>
          <Link to={`/accounts/${id}/password`} className="btn btn-ghost">Сменить пароль</Link>
        </div>
      </div>

      <PageSection title="Основная информация">
        <dl className="detail-list">
          <dt>ID</dt>            <dd className="mono">{account.id}</dd>
          <dt>Публичное имя</dt> <dd>{account.publicName}</dd>
          <dt>Имя персонажа</dt><dd>{account.characterName || '—'}</dd>
          <dt>Роль</dt>          <dd>{ROLE_LABELS[account.role] || account.role}</dd>
          <dt>Статус</dt>        <dd><StatusBadge status={account.status} /></dd>
          <dt>Заметки</dt>       <dd>{account.notes || '—'}</dd>
          <dt>Создан</dt>        <dd>{formatDate(account.createdAt)}</dd>
          <dt>Обновлён</dt>      <dd>{formatDate(account.updatedAt)}</dd>
        </dl>
      </PageSection>

      {balance && (
        <PageSection title="Баланс">
          <dl className="detail-list">
            <dt>Безналичные</dt> <dd>{balance.cashlessAmount}</dd>
            <dt>Крипта</dt>      <dd>{balance.cryptoAmount}</dd>
          </dl>
          <div style={{marginTop: '0.75rem'}}>
            <Link to={`/accounts/${id}`} className="btn btn-ghost btn-sm"
              onClick={e => { e.preventDefault(); window.location.href = `/banking/transactions/${id}` }}>
            </Link>
          </div>
        </PageSection>
      )}

      {sessions.length > 0 && (
        <PageSection title="Активные сессии">
          <table className="table">
            <thead>
              <tr>
                <th>ID сессии</th><th>Терминал</th><th>Статус</th><th>Начало</th><th>Действие</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map(s => (
                <tr key={s.id}>
                  <td className="mono">{s.id}</td>
                  <td>{s.terminalName || '—'}</td>
                  <td>{s.status}</td>
                  <td>{formatDate(s.startedAt)}</td>
                  <td>
                    <button className="btn btn-sm btn-danger"
                      onClick={async () => {
                        if (!confirm('Завершить сессию?')) return
                        await client.post(ENDPOINTS.terminateSession(s.id))
                        setSessions(prev => prev.filter(x => x.id !== s.id))
                      }}>
                      Завершить
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </PageSection>
      )}
    </div>
  )
}
