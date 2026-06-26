import { Link } from 'react-router-dom'
import type { AccountResponse } from '../types'
import StatusBadge from './StatusBadge'
import { formatDate, ROLE_LABELS } from '../utils/helpers'

interface Props {
  accounts: AccountResponse[]
}

export default function AccountsTable({ accounts }: Props) {
  if (accounts.length === 0) {
    return <p className="empty-state">Аккаунты не найдены</p>
  }
  return (
    <table className="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Публичное имя</th>
          <th>Имя персонажа</th>
          <th>Роль</th>
          <th>Статус</th>
          <th>Создан</th>
          <th>Действия</th>
        </tr>
      </thead>
      <tbody>
        {accounts.map(a => (
          <tr key={a.id}>
            <td className="mono">{a.id}</td>
            <td>{a.publicName}</td>
            <td>{a.characterName || '—'}</td>
            <td>{ROLE_LABELS[a.role] || a.role}</td>
            <td><StatusBadge status={a.status} /></td>
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
  )
}
