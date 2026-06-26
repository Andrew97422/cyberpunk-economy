import { useEffect, useState, FormEvent } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { AccountResponse } from '../types'
import { ACCOUNT_ROLES, ACCOUNT_STATUSES } from '../types'
import { ROLE_LABELS, STATUS_LABELS, extractError } from '../utils/helpers'
import FormField from '../components/FormField'
import SelectField from '../components/SelectField'
import ErrorBlock from '../components/ErrorBlock'
import SuccessMessage from '../components/SuccessMessage'
import LoadingBlock from '../components/LoadingBlock'

export default function EditAccountPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [form, setForm] = useState({ publicName: '', characterName: '', role: '', status: '', notes: '' })
  const [loading, setLoading]   = useState(true)
  const [saving, setSaving]     = useState(false)
  const [error, setError]       = useState('')
  const [success, setSuccess]   = useState('')

  useEffect(() => {
    if (!id) return
    client.get<AccountResponse>(ENDPOINTS.accountById(id))
      .then(r => setForm({
        publicName: r.data.publicName,
        characterName: r.data.characterName || '',
        role: r.data.role,
        status: r.data.status,
        notes: r.data.notes || '',
      }))
      .catch(err => setError(extractError(err)))
      .finally(() => setLoading(false))
  }, [id])

  const set = (key: string) => (v: string) => setForm(f => ({ ...f, [key]: v }))

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError('')
    setSuccess('')
    try {
      // Обновляем роль (отдельный endpoint по OpenAPI)
      await client.patch(ENDPOINTS.updateRole(id!), { role: form.role })
      // Обновляем статус (отдельный endpoint)
      await client.patch(ENDPOINTS.updateStatus(id!), { status: form.status })
      // NOTE: publicName / characterName / notes — если бекенд добавит PATCH /accounts/:id, добавь сюда
      setSuccess('Изменения сохранены')
      setTimeout(() => navigate(`/accounts/${id}`), 1200)
    } catch (err) {
      setError(extractError(err))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="page"><LoadingBlock /></div>

  return (
    <div className="page">
      <div className="page-header">
        <Link to={`/accounts/${id}`} className="back-link">← Профиль аккаунта</Link>
        <h1 className="page-title">Редактирование аккаунта</h1>
      </div>

      <form onSubmit={handleSubmit} className="form form-wide">
        <FormField label="Публичное имя" id="publicName">
          <input id="publicName" type="text" value={form.publicName} disabled
            title="Изменение publicName не поддерживается текущим API" />
        </FormField>
        <FormField label="Имя персонажа" id="characterName">
          <input id="characterName" type="text" value={form.characterName} disabled
            title="Изменение через API пока не поддерживается" />
        </FormField>
        <FormField label="Роль" id="role" required>
          <SelectField id="role" value={form.role} onChange={set('role')}
            options={ACCOUNT_ROLES.map(r => ({ value: r, label: ROLE_LABELS[r] }))} />
        </FormField>
        <FormField label="Статус" id="status" required>
          <SelectField id="status" value={form.status} onChange={set('status')}
            options={ACCOUNT_STATUSES.map(s => ({ value: s, label: STATUS_LABELS[s] }))} />
        </FormField>
        <FormField label="Заметки" id="notes">
          <textarea id="notes" rows={3} value={form.notes} disabled
            title="Изменение через API пока не поддерживается" />
        </FormField>
        <p className="hint">Поля без активного редактирования будут обновляться по мере расширения API.</p>

        {error && <ErrorBlock message={error} />}
        {success && <SuccessMessage message={success} />}
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Сохраняю...' : 'Сохранить изменения'}
        </button>
      </form>
    </div>
  )
}
