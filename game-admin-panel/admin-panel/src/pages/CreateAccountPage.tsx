import { useState, FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { AccountResponse } from '../types'
import { ACCOUNT_ROLES, ACCOUNT_STATUSES } from '../types'
import { ROLE_LABELS, STATUS_LABELS, extractError } from '../utils/helpers'
import FormField from '../components/FormField'
import SelectField from '../components/SelectField'
import ErrorBlock from '../components/ErrorBlock'
import SuccessMessage from '../components/SuccessMessage'

export default function CreateAccountPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    publicName: '', characterName: '', role: 'PLAYER', status: 'ACTIVE', password: '', notes: ''
  })
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState('')
  const [createdId, setCreatedId] = useState<number | null>(null)

  const set = (key: string) => (v: string) => setForm(f => ({ ...f, [key]: v }))

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await client.post<AccountResponse>(ENDPOINTS.accounts, form)
      setCreatedId(res.data.id)
    } catch (err) {
      setError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  if (createdId !== null) {
    return (
      <div className="page">
        <SuccessMessage message={`Аккаунт успешно создан (ID: ${createdId})`} />
        <div className="btn-group" style={{ marginTop: '1rem' }}>
          <button className="btn btn-primary" onClick={() => navigate(`/accounts/${createdId}`)}>
            Открыть аккаунт
          </button>
          <button className="btn btn-ghost" onClick={() => { setCreatedId(null); setForm({ publicName: '', characterName: '', role: 'PLAYER', status: 'ACTIVE', password: '', notes: '' }) }}>
            Создать ещё
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="page">
      <div className="page-header">
        <Link to="/accounts" className="back-link">← Аккаунты</Link>
        <h1 className="page-title">Создать аккаунт</h1>
      </div>

      <form onSubmit={handleSubmit} className="form form-wide">
        <FormField label="Публичное имя" id="publicName" required>
          <input id="publicName" type="text" required value={form.publicName} onChange={e => set('publicName')(e.target.value)} />
        </FormField>
        <FormField label="Имя персонажа" id="characterName">
          <input id="characterName" type="text" value={form.characterName} onChange={e => set('characterName')(e.target.value)} />
        </FormField>
        <FormField label="Роль" id="role" required>
          <SelectField id="role" value={form.role} onChange={set('role')}
            options={ACCOUNT_ROLES.map(r => ({ value: r, label: ROLE_LABELS[r] }))} />
        </FormField>
        <FormField label="Статус" id="status" required>
          <SelectField id="status" value={form.status} onChange={set('status')}
            options={ACCOUNT_STATUSES.map(s => ({ value: s, label: STATUS_LABELS[s] }))} />
        </FormField>
        <FormField label="Пароль" id="password">
          <input id="password" type="password" autoComplete="new-password" value={form.password} onChange={e => set('password')(e.target.value)} />
        </FormField>
        <FormField label="Заметки" id="notes">
          <textarea id="notes" rows={3} value={form.notes} onChange={e => set('notes')(e.target.value)} />
        </FormField>

        {error && <ErrorBlock message={error} />}
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Создаю...' : 'Создать аккаунт'}
        </button>
      </form>
    </div>
  )
}
