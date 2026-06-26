import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { PagedResponse, AccountResponse } from '../types'
import { ROLE_LABELS } from '../utils/helpers'
import PageSection from '../components/PageSection'
import LoadingBlock from '../components/LoadingBlock'

export default function DashboardPage() {
  const { user } = useAuth()
  const [totalAccounts, setTotalAccounts] = useState<number | null>(null)
  const [recentAccounts, setRecentAccounts] = useState<AccountResponse[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    client.get<PagedResponse<AccountResponse>>(ENDPOINTS.accounts, { params: { page: 0, size: 5, sortBy: 'createdAt', direction: 'DESC' } })
      .then(r => {
        setTotalAccounts(r.data.totalElements)
        setRecentAccounts(r.data.content)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="page">
      <h1 className="page-title">Главная</h1>

      <PageSection>
        <div className="dashboard-cards">
          <div className="dashboard-card">
            <div className="dashboard-card-label">Текущий пользователь</div>
            <div className="dashboard-card-value">{user?.publicName}</div>
          </div>
          <div className="dashboard-card">
            <div className="dashboard-card-label">Роль</div>
            <div className="dashboard-card-value">{user ? (ROLE_LABELS[user.role] || user.role) : '—'}</div>
          </div>
          <div className="dashboard-card">
            <div className="dashboard-card-label">Всего аккаунтов</div>
            <div className="dashboard-card-value">{totalAccounts !== null ? totalAccounts : '—'}</div>
          </div>
        </div>
      </PageSection>

      <PageSection>
        <div className="dashboard-actions">
          <Link to="/accounts" className="btn btn-primary">Список аккаунтов</Link>
          <Link to="/accounts/new" className="btn btn-secondary">Создать аккаунт</Link>
        </div>
      </PageSection>

      <PageSection title="Последние аккаунты">
        {loading ? <LoadingBlock /> : (
          <ul className="recent-list">
            {recentAccounts.map(a => (
              <li key={a.id} className="recent-item">
                <Link to={`/accounts/${a.id}`}>
                  <strong>{a.publicName}</strong>
                  {a.characterName ? ` / ${a.characterName}` : ''}
                </Link>
                <span className={`badge badge-${a.status.toLowerCase()}`}>{a.status}</span>
              </li>
            ))}
          </ul>
        )}
      </PageSection>
    </div>
  )
}
