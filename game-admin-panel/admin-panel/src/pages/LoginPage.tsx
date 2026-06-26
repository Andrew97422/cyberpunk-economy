import { useState, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { extractError } from '../utils/helpers'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [publicName, setPublicName] = useState('')
  const [password, setPassword]   = useState('')
  const [error, setError]         = useState('')
  const [loading, setLoading]     = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login({ publicName, password })
      navigate('/dashboard')
    } catch (err) {
      setError(extractError(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-box">
        <h1 className="login-title">Вход в Admin Panel</h1>
        <form onSubmit={handleSubmit} className="form">
          <div className="form-field">
            <label htmlFor="publicName">Публичное имя</label>
            <input
              id="publicName"
              type="text"
              autoComplete="username"
              value={publicName}
              onChange={e => setPublicName(e.target.value)}
              required
              autoFocus
            />
          </div>
          <div className="form-field">
            <label htmlFor="password">Пароль</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
            />
          </div>
          {error && <div className="error-block">{error}</div>}
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? 'Вхожу...' : 'Войти'}
          </button>
        </form>
      </div>
    </div>
  )
}
