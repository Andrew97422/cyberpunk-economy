import { Link } from 'react-router-dom';
import type { AccountResponse } from '../../../shared/types';
import { StatusBadge } from '../../../shared/ui';
import { formatDate, ROLE_LABELS } from '../../../shared/utils';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';

export default function AccountsTable({ accounts }: { accounts: AccountResponse[] }) {
  const sort = useTableSort(accounts, 'createdAt', 'desc');
  if (accounts.length === 0) {
    return <p className="empty-state">Аккаунты не найдены</p>;
  }
  return (
    <div className="page-section" style={{ padding: 0, overflow: 'hidden' }}>
      <table className="table">
        <thead>
          <tr>
            <SortTh k="id" label="ID" sort={sort} />
            <SortTh k="publicName" label="Публичное имя" sort={sort} />
            <SortTh k="characterName" label="Имя персонажа" sort={sort} />
            <SortTh k="role" label="Роль" sort={sort} />
            <SortTh k="status" label="Статус" sort={sort} />
            <SortTh k="createdAt" label="Создан" sort={sort} />
            <th>Действия</th>
          </tr>
        </thead>
        <tbody>
          {sort.sorted.map((a) => (
            <tr key={a.id}>
              <td className="mono">{a.id}</td>
              <td>{a.publicName}</td>
              <td>{a.characterName || '—'}</td>
              <td>{ROLE_LABELS[a.role] || a.role}</td>
              <td>
                <StatusBadge status={a.status} />
              </td>
              <td>{formatDate(a.createdAt)}</td>
              <td className="actions">
                <Link to={`/accounts/${a.id}`} className="btn btn-sm btn-secondary">Открыть</Link>
                <Link to={`/accounts/${a.id}/edit`} className="btn btn-sm btn-ghost">Изменить</Link>
                <Link to={`/accounts/${a.id}/status`} className="btn btn-sm btn-ghost">Статус</Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
