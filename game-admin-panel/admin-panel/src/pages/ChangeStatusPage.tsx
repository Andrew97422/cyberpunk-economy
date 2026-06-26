import { useEffect, useState, FormEvent } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { AccountResponse } from '../types'
import { ACCOUNT_STATUSES } from '../types'
import { STATUS_LABELS, extractError } from '../utils/helpers'
import StatusBadge from '../components/StatusBadge'
import SelectField from '../components/SelectField'
import FormField from '../components/FormField'
import ErrorBlock from '../components/ErrorBlock'
import SuccessMessage from '../components/SuccessMessage'
import LoadingBlock from '../components/LoadingBlock'

export default function ChangeStatusPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [account, setAccount]   = useState<AccountResponse | null>(null)
  const [newStatus, setNewStatus] = useState('')
  const [loading, setLoading]   = useState(true)
  const [saving, setSaving]     = useState(false)
  const [error, setError]       = useState('')
  const [success, setSuccess]   = useState('')
  const [confirm, setConfirm]   = useState(false)

  useEffect(() => {
    if (!id) return
    client.get<AccountResponse>(ENDPOINTS.accountById(id))
      .then(r => { setAccount(r.data); setNewStatus(r.data.status) })
      .catch(err => setError(extractError(err)))
      .finally(() => setLoading(false))
  }, [id])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!confirm) { setConfirm(true); return }
    setSaving(true)
    setError('')
    try {
      await client.patch(ENDPOINTS.updateStatus(id!), { status: newStatus })
      setSuccess(`Статус изменён на "${STATUS_LABELS[newStatus] || newStatus}"`)
      setTimeout(() => navigate(`/accounts/${id}`), 1200)
    } catch (err) {
      setError(extractError(err))
    } finally {
      setSaving(false)
      setConfirm(false)
    }
  }

  if (loading) return <div className="page"><LoadingBlock /></div>

  return (
    <div className="page">
      <div className="page-header">
        <Link to={`/accounts/${id}`} className="back-link">← Профиль аккаунта</Link>
        <h1 className="page-title">Изменить статус</h1>
      </div>

      {account && (
        <div className="info-row">
          <span>Текущий статус:</span>
          <StatusBadge status={account.status} />
          <span className="text-muted">({account.publicName})</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="form form-wide" style={{ marginTop: '1.5rem' }}>
        <FormField label="Новый статус" id="newStatus" required>
          <SelectField id="newStatus" value={newStatus} onChange={v => { setNewStatus(v); setConfirm(false) }}
            options={ACCOUNT_STATUSES.map(s => ({ value: s, label: STATUS_LABELS[s] }))} />
        </FormField>

        {confirm && (
          <div className="warning-block">
            Вы уверены, что хотите изменить статус на <strong>{STATUS_LABELS[newStatus] || newStatus}</strong>?
          </div>
        )}

        {error && <ErrorBlock message={error} />}
        {success && <SuccessMessage message={success} />}

        <div className="btn-group">
          <button type="submit" className="btn btn-primary" disabled={saving || newStatus === account?.status}>
            {confirm ? 'Подтвердить изменение' : 'Изменить статус'}
          </button>
          {confirm && (
            <button type="button" className="btn btn-ghost" onClick={() => setConfirm(false)}>Отмена</button>
          )}
        </div>
      </form>
    </div>
  )
}
