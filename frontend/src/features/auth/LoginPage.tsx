import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../shared/auth/AuthContext';
import { extractError, isOperator } from '../../shared/utils';

type Mode = 'admin' | 'player';

export default function LoginPage() {
  const { loginAdmin, loginPlayer } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState<Mode>('admin');
  const [publicName, setPublicName] = useState('');
  const [password, setPassword] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const user =
        mode === 'admin' ? await loginAdmin(publicName, password) : await loginPlayer(pin);
      navigate(isOperator(user.role) ? '/dashboard' : '/me/balance', { replace: true });
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-box">
        <h1 className="login-title">Game Console</h1>
        <p className="login-sub">Единая панель управления: администрирование и банк.</p>

        <div className="switcher">
          <button type="button" className={mode === 'admin' ? 'active' : ''} onClick={() => setMode('admin')}>
            Сотрудник
          </button>
          <button type="button" className={mode === 'player' ? 'active' : ''} onClick={() => setMode('player')}>
            Игрок (PIN)
          </button>
        </div>

        <form onSubmit={handleSubmit} className="form">
          {mode === 'admin' ? (
            <>
              <div className="form-field">
                <label htmlFor="publicName">Публичное имя</label>
                <input
                  id="publicName"
                  type="text"
                  autoComplete="username"
                  value={publicName}
                  onChange={(e) => setPublicName(e.target.value)}
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
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
            </>
          ) : (
            <div className="form-field">
              <label htmlFor="pin">PIN-код</label>
              <input
                id="pin"
                type="text"
                inputMode="numeric"
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                placeholder="Введите выданный PIN"
                required
                autoFocus
              />
            </div>
          )}

          {error && <div className="error-block">{error}</div>}

          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading ? 'Вход...' : 'Войти'}
          </button>
        </form>
      </div>
    </div>
  );
}
