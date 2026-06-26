import { useEffect, useState, FormEvent } from 'react'
import { useParams, Link } from 'react-router-dom'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { AccountResponse, PinResponse } from '../types'
import { extractError, formatDate } from '../utils/helpers'
import FormField from '../components/FormField'
import ErrorBlock from '../components/ErrorBlock'
import SuccessMessage from '../components/SuccessMessage'
import LoadingBlock from '../components/LoadingBlock'

// NOTE: В OpenAPI нет прямого PATCH /accounts/:id/password.
// Реализовано через создание временного PIN-кода (admin/pins),
// который игрок использует для входа.
// Если бекенд добавит endpoint смены пароля — замени логику ниже.

export default function ChangePasswordPage() {
  const { id } = useParams<{ id: string }>()
  const [publicName, setPublicName] = useState('')
  const [rawPin, setRawPin]         = useState('')
  const [duration, setDuration]     = useState('60')
  const [comment, setComment]       = useState('')
  const [loading, setLoading]       = useState(true)
  const [saving, setSaving]         = useState(false)
  const [error, setError]           = useState('')
  const [pinResult, setPinResult]   = useState<PinResponse | null>(null)

  useEffect(() => {
    if (!id) return
    client.get<AccountResponse>(ENDPOINTS.accountById(id))
      .then(r => setPublicName(r.data.publicName))
      .catch(err => setError(extractError(err)))
      .finally(() => setLoading(false))
  }, [id])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError('')
    setPinResult(null)
    try {
      const res = await client.post<PinResponse>(ENDPOINTS.createPin, {
        publicName,
        rawPin,
        durationMinutes: parseInt(duration, 10),
        comment: comment || undefined,
      })
      setPinResult(res.data)
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
        <h1 className="page-title">Создать временный PIN для входа</h1>
      </div>
      <p className="hint" style={{ marginBottom: '1.5rem' }}>
        Прямого сброса пароля в текущем API нет. Вместо этого можно выдать временный PIN-код,
        который аккаунт использует для входа через /auth/player/login.
      </p>

      {pinResult ? (
        <div>
          <SuccessMessage message="PIN-код успешно создан" />
          <dl className="detail-list" style={{ marginTop: '1rem' }}>
            <dt>Аккаунт</dt>  <dd>{pinResult.publicName}</dd>
            <dt>PIN</dt>       <dd className="mono" style={{ fontWeight: 'bold', fontSize: '1.3rem' }}>{rawPin}</dd>
            <dt>Статус</dt>    <dd>{pinResult.status}</dd>
            <dt>Истекает</dt>  <dd>{pinResult.durationMinutes} мин с момента создания</dd>
            <dt>Создан</dt>    <dd>{formatDate(pinResult.createdAt)}</dd>
            {pinResult.comment && <><dt>Комментарий</dt><dd>{pinResult.comment}</dd></>}
          </dl>
          <div className="btn-group" style={{ marginTop: '1rem' }}>
            <button className="btn btn-ghost" onClick={() => { setPinResult(null); setRawPin('') }}>
              Создать ещё PIN
            </button>
            <Link to={`/accounts/${id}`} className="btn btn-secondary">← Назад к профилю</Link>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="form form-wide">
          <FormField label="Аккаунт" id="accName">
            <input id="accName" type="text" value={publicName} disabled />
          </FormField>
          <FormField label="PIN-код (значение)" id="rawPin" required>
            <input id="rawPin" type="text" value={rawPin} onChange={e => setRawPin(e.target.value)} required minLength={1} />
          </FormField>
          <FormField label="Действителен (минуты, 1-1440)" id="duration" required>
            <input id="duration" type="number" value={duration} onChange={e => setDuration(e.target.value)} min={1} max={1440} required />
          </FormField>
          <FormField label="Комментарий" id="comment">
            <input id="comment" type="text" value={comment} onChange={e => setComment(e.target.value)} />
          </FormField>
          {error && <ErrorBlock message={error} />}
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Создаю...' : 'Создать PIN'}
          </button>
        </form>
      )}
    </div>
  )
}
