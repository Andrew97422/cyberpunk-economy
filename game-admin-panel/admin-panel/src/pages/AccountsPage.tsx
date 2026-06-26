import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { PagedResponse, AccountResponse } from '../types'
import { ACCOUNT_ROLES, ACCOUNT_STATUSES } from '../types'
import { ROLE_LABELS, STATUS_LABELS, extractError } from '../utils/helpers'
import AccountsTable from '../components/AccountsTable'
import LoadingBlock from '../components/LoadingBlock'
import ErrorBlock from '../components/ErrorBlock'

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [total, setTotal]     = useState(0)
  const [page, setPage]       = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [search, setSearch]   = useState('')
  const [roleFilter, setRole] = useState('')
  const [statusFilter, setStatus] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState('')

  const fetchAccounts = async (p = page) => {
    setLoading(true)
    setError('')
    try {
      const params: Record<string, unknown> = { page: p, size: 20, sortBy: 'createdAt', direction: 'DESC' }
      if (search)       params.search = search
      // role/status filter — если бекенд добавит query params, подставь их здесь
      const res = await client.get<PagedResponse<AccountResponse>>(ENDPOINTS.accounts, { params })
      let content = res.data.content
      // Клиентская фильтрация по role/status (пока бекенд не поддерживает query params)
      if (roleFilter)   content = content.filter(a => a.role === roleFilter)
      if (statusFilter) content = content.filter(a => a.status === statusFilter)
      setAccounts(content)
      setTotal(res.data.totalElements)
      setTotalPages(res.data.totalPages)
      setPage(p)
    } catch (err) {
      setError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchAccounts(0) }, [])

  const handleSearch = () => fetchAccounts(0)
  const handleReset  = () => { setSearch(''); setRole(''); setStatus(''); setTimeout(() => fetchAccounts(0), 0) }

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Аккаунты</h1>
        <Link to="/accounts/new" className="btn btn-primary">+ Создать аккаунт</Link>
      </div>

      <div className="filters-bar">
        <input
          type="text"
          placeholder="Поиск по имени..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleSearch()}
          className="filter-input"
        />
        <select value={roleFilter} onChange={e => setRole(e.target.value)}>
          <option value="">Все роли</option>
          {ACCOUNT_ROLES.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
        <select value={statusFilter} onChange={e => setStatus(e.target.value)}>
          <option value="">Все статусы</option>
          {ACCOUNT_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
        </select>
        <button className="btn btn-primary" onClick={handleSearch}>Найти</button>
        <button className="btn btn-ghost" onClick={handleReset}>Сброс</button>
      </div>

      {error && <ErrorBlock message={error} />}
      {loading ? <LoadingBlock /> : (
        <>
          <div className="table-meta">Найдено: {total}</div>
          <AccountsTable accounts={accounts} />
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => fetchAccounts(page - 1)}>← Назад</button>
            <span>Страница {page + 1} из {totalPages || 1}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => fetchAccounts(page + 1)}>Вперёд →</button>
          </div>
        </>
      )}
    </div>
  )
}
