import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { PagedResponse, AccountResponse } from '../../../shared/types';
import { ACCOUNT_ROLES, ACCOUNT_STATUSES } from '../../../shared/types';
import { ROLE_LABELS, STATUS_LABELS, extractError } from '../../../shared/utils';
import { LoadingBlock, ErrorBlock } from '../../../shared/ui';
import AccountsTable from '../components/AccountsTable';

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<AccountResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState('');
  const [roleFilter, setRole] = useState('');
  const [statusFilter, setStatus] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchAccounts = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, unknown> = { page: p, size: 20, sortBy: 'createdAt', direction: 'DESC' };
      if (search) params.search = search;
      if (roleFilter) params.role = roleFilter;
      if (statusFilter) params.status = statusFilter;
      const res = await client.get<PagedResponse<AccountResponse>>(ENDPOINTS.accounts, { params });
      setAccounts(res.data.content);
      setTotal(res.data.totalElements);
      setTotalPages(res.data.totalPages);
      setPage(p);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  // Auto-apply: search/role/status changes refetch from the backend (debounced for typing).
  useEffect(() => {
    const t = setTimeout(() => fetchAccounts(0), 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, roleFilter, statusFilter]);

  const handleReset = () => {
    setSearch('');
    setRole('');
    setStatus('');
    setTimeout(() => fetchAccounts(0), 0);
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1 className="page-title">Аккаунты</h1>
        <Link to="/accounts/new" className="btn btn-primary">+ Создать аккаунт</Link>
      </div>

      <div className="filters-bar">
        <input
          type="text"
          placeholder="Поиск по имени / персонажу…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="filter-input"
        />
        <select value={roleFilter} onChange={(e) => setRole(e.target.value)}>
          <option value="">Все роли</option>
          {ACCOUNT_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
        </select>
        <select value={statusFilter} onChange={(e) => setStatus(e.target.value)}>
          <option value="">Все статусы</option>
          {ACCOUNT_STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
        </select>
        <button className="btn btn-ghost" onClick={handleReset}>Сброс</button>
      </div>

      {error && <ErrorBlock message={error} />}
      {loading ? (
        <LoadingBlock />
      ) : (
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
  );
}
