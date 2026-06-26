import { FormEvent, useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { api, clearAuth, getStoredAuth, getStoredToken, storeAuth } from './api';
import { endpoints } from './config';
import type {
  AuthResponse,
  BalanceResponse,
  LoginMode,
  OperationResultResponse,
  PageKey,
  TransactionResponse,
  TransactionsPageResponse,
} from './types';
import { formatDate, formatMoney, getCurrencyLabel, getSuccessMessage, getTransactionTypeLabel } from './utils';

const menu: Array<{ key: PageKey; label: string }> = [
  { key: 'home', label: 'Главная' },
  { key: 'balance', label: 'Баланс' },
  { key: 'history', label: 'История операций' },
  { key: 'deposit', label: 'Пополнить игрока' },
  { key: 'withdraw', label: 'Списать у игрока' },
  { key: 'transfer', label: 'Перевод между игроками' },
  { key: 'adjust', label: 'Корректировка баланса' },
  { key: 'reverse', label: 'Отменить операцию' },
];

export default function App() {
  const [token, setToken] = useState('');
  const [auth, setAuth] = useState<AuthResponse | null>(null);
  const [page, setPage] = useState<PageKey>('home');
  const [loginMode, setLoginMode] = useState<LoginMode>('admin');
  const [loginName, setLoginName] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginPin, setLoginPin] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState('');

  const [balance, setBalance] = useState<BalanceResponse | null>(null);
  const [balanceLoading, setBalanceLoading] = useState(false);
  const [balanceError, setBalanceError] = useState('');
  const [balanceSearchId, setBalanceSearchId] = useState('');

  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [transactionsLoading, setTransactionsLoading] = useState(false);
  const [transactionsError, setTransactionsError] = useState('');
  const [historySearchId, setHistorySearchId] = useState('');

  const [depositForm, setDepositForm] = useState({ publicName: '', currencyType: 'CASHLESS', amount: '', comment: '' });
  const [withdrawForm, setWithdrawForm] = useState({ publicName: '', currencyType: 'CASHLESS', amount: '', comment: '' });
  const [transferForm, setTransferForm] = useState({ fromPublicName: '', toPublicName: '', currencyType: 'CASHLESS', amount: '', comment: '' });
  const [adjustForm, setAdjustForm] = useState({ publicName: '', currencyType: 'CASHLESS', amount: '', direction: 'INCREASE', comment: '' });
  const [reverseForm, setReverseForm] = useState({ transactionId: '', comment: '' });

  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');
  const [actionResult, setActionResult] = useState<OperationResultResponse | null>(null);

  useEffect(() => {
    setToken(getStoredToken());
    setAuth(getStoredAuth());
  }, []);

  useEffect(() => {
    const interceptor = api.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error?.response?.status === 401) {
          clearAuth();
          setToken('');
          setAuth(null);
          setPage('home');
        }
        return Promise.reject(error);
      },
    );

    return () => {
      api.interceptors.response.eject(interceptor);
    };
  }, []);

  const loggedIn = Boolean(token);

  const welcomeText = useMemo(() => {
    if (!auth?.publicName) return 'Вы вошли в банковую панель.';
    return `Вы вошли как ${auth.publicName}${auth.role ? ` (${auth.role})` : ''}.`;
  }, [auth]);

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setLoginLoading(true);
    setLoginError('');

    try {
      const url = loginMode === 'admin' ? endpoints.adminLogin : endpoints.playerLogin;
      const payload = loginMode === 'admin'
        ? { publicName: loginName, password: loginPassword }
        : { publicName: loginName, pin: loginPin };

      const response = await api.post<AuthResponse>(url, payload);
      const nextToken = response.data.token || '';

      if (!nextToken) {
        setLoginError('Сервер не вернул токен.');
        return;
      }

      storeAuth(response.data, nextToken);
      setToken(nextToken);
      setAuth(response.data);
      setPage('home');
    } catch (error) {
      setLoginError(extractErrorMessage(error, 'Не удалось выполнить вход.'));
    } finally {
      setLoginLoading(false);
    }
  }

  function handleLogout() {
    clearAuth();
    setToken('');
    setAuth(null);
    setBalance(null);
    setTransactions([]);
    setActionResult(null);
    setActionSuccess('');
    setActionError('');
  }

  async function loadMyBalance() {
    setBalanceLoading(true);
    setBalanceError('');
    try {
      const response = await api.get<BalanceResponse>(endpoints.myBalance);
      setBalance(response.data);
    } catch (error) {
      setBalanceError(extractErrorMessage(error, 'Не удалось загрузить баланс.'));
    } finally {
      setBalanceLoading(false);
    }
  }

  async function loadPlayerBalance() {
    if (!balanceSearchId) {
      setBalanceError('Укажите accountId игрока.');
      return;
    }
    setBalanceLoading(true);
    setBalanceError('');
    try {
      const response = await api.get<BalanceResponse>(endpoints.balanceByAccountId(balanceSearchId));
      setBalance(response.data);
    } catch (error) {
      setBalanceError(extractErrorMessage(error, 'Не удалось загрузить баланс игрока.'));
    } finally {
      setBalanceLoading(false);
    }
  }

  async function loadMyTransactions() {
    setTransactionsLoading(true);
    setTransactionsError('');
    try {
      const response = await api.get<TransactionsPageResponse>(endpoints.myTransactions);
      setTransactions(response.data.content || []);
    } catch (error) {
      setTransactionsError(extractErrorMessage(error, 'Не удалось загрузить историю операций.'));
    } finally {
      setTransactionsLoading(false);
    }
  }

  async function loadPlayerTransactions() {
    if (!historySearchId) {
      setTransactionsError('Укажите accountId игрока.');
      return;
    }
    setTransactionsLoading(true);
    setTransactionsError('');
    try {
      const response = await api.get<TransactionsPageResponse>(endpoints.transactionsByAccountId(historySearchId));
      setTransactions(response.data.content || []);
    } catch (error) {
      setTransactionsError(extractErrorMessage(error, 'Не удалось загрузить операции игрока.'));
    } finally {
      setTransactionsLoading(false);
    }
  }

  async function submitAction(url: string, payload: unknown) {
    setActionLoading(true);
    setActionError('');
    setActionSuccess('');
    setActionResult(null);
    try {
      const response = await api.post<OperationResultResponse>(url, payload);
      setActionResult(response.data);
      setActionSuccess(getSuccessMessage(response.data.operation));
    } catch (error) {
      setActionError(extractErrorMessage(error, 'Не удалось выполнить действие.'));
    } finally {
      setActionLoading(false);
    }
  }

  if (!loggedIn) {
    return (
      <div className="login-screen">
        <form className="login-card" onSubmit={handleLogin}>
          <div>
            <h1>Banking Admin Panel</h1>
            <p className="subtext">Простой интерфейс для работы с игроками и их балансами.</p>
          </div>

          <div className="switcher">
            <button type="button" className={loginMode === 'admin' ? 'active' : ''} onClick={() => setLoginMode('admin')}>
              Администратор
            </button>
            <button type="button" className={loginMode === 'player' ? 'active' : ''} onClick={() => setLoginMode('player')}>
              Игрок
            </button>
          </div>

          <label>
            <span>Имя пользователя</span>
            <input value={loginName} onChange={(e) => setLoginName(e.target.value)} placeholder="publicName" />
          </label>

          {loginMode === 'admin' ? (
            <label>
              <span>Пароль</span>
              <input type="password" value={loginPassword} onChange={(e) => setLoginPassword(e.target.value)} placeholder="Введите пароль" />
            </label>
          ) : (
            <label>
              <span>PIN</span>
              <input value={loginPin} onChange={(e) => setLoginPin(e.target.value)} placeholder="Введите PIN" />
            </label>
          )}

          {loginError ? <div className="message error">{loginError}</div> : null}

          <button className="primary" type="submit" disabled={loginLoading}>
            {loginLoading ? 'Входим...' : 'Войти'}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <h2>Banking</h2>
          <p>Панель управления</p>
        </div>

        <div className="user-box">
          <div className="user-name">{auth?.publicName || 'Пользователь'}</div>
          <div className="user-role">{auth?.role || 'Без роли'}</div>
        </div>

        <nav className="menu">
          {menu.map((item) => (
            <button
              key={item.key}
              className={page === item.key ? 'menu-item active' : 'menu-item'}
              onClick={() => {
                setPage(item.key);
                setActionSuccess('');
                setActionError('');
              }}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <button className="secondary" onClick={handleLogout}>Выйти</button>
      </aside>

      <main className="content">
        {page === 'home' && (
          <section className="panel">
            <h1>Главная</h1>
            <p>{welcomeText}</p>
            <div className="cards">
              <div className="info-card">
                <h3>Пополнить игрока</h3>
                <p>Зачислить деньги выбранному игроку.</p>
              </div>
              <div className="info-card">
                <h3>Списать у игрока</h3>
                <p>Снять деньги с баланса игрока.</p>
              </div>
              <div className="info-card">
                <h3>Перевод между игроками</h3>
                <p>Переместить деньги от одного игрока другому.</p>
              </div>
              <div className="info-card">
                <h3>Корректировка баланса</h3>
                <p>Вручную увеличить или уменьшить баланс.</p>
              </div>
            </div>
          </section>
        )}

        {page === 'balance' && (
          <section className="panel">
            <div className="section-head">
              <div>
                <h1>Баланс</h1>
                <p className="subtext">Посмотреть свой баланс или баланс конкретного игрока.</p>
              </div>
            </div>

            <div className="toolbar">
              <button className="primary" onClick={loadMyBalance} disabled={balanceLoading}>Показать мой баланс</button>
              <input value={balanceSearchId} onChange={(e) => setBalanceSearchId(e.target.value)} placeholder="accountId игрока" />
              <button className="secondary" onClick={loadPlayerBalance} disabled={balanceLoading}>Показать баланс игрока</button>
            </div>

            {balanceError ? <div className="message error">{balanceError}</div> : null}
            {balanceLoading ? <div className="message info">Загружаем баланс...</div> : null}

            {balance ? (
              <div className="cards two-columns">
                <div className="stat-box">
                  <span>Игрок</span>
                  <strong>{balance.publicName || '—'}</strong>
                </div>
                <div className="stat-box">
                  <span>Account ID</span>
                  <strong>{balance.accountId || '—'}</strong>
                </div>
                <div className="stat-box">
                  <span>Безналичные</span>
                  <strong>{formatMoney(balance.cashlessAmount)}</strong>
                </div>
                <div className="stat-box">
                  <span>Крипта</span>
                  <strong>{formatMoney(balance.cryptoAmount)}</strong>
                </div>
              </div>
            ) : null}
          </section>
        )}

        {page === 'history' && (
          <section className="panel">
            <h1>История операций</h1>
            <p className="subtext">Без JSON и без pageable: просто список последних операций, который вернул backend.</p>

            <div className="toolbar">
              <button className="primary" onClick={loadMyTransactions} disabled={transactionsLoading}>Показать мои операции</button>
              <input value={historySearchId} onChange={(e) => setHistorySearchId(e.target.value)} placeholder="accountId игрока" />
              <button className="secondary" onClick={loadPlayerTransactions} disabled={transactionsLoading}>Показать операции игрока</button>
            </div>

            {transactionsError ? <div className="message error">{transactionsError}</div> : null}
            {transactionsLoading ? <div className="message info">Загружаем историю операций...</div> : null}

            {!transactionsLoading && transactions.length === 0 ? (
              <div className="message info">Операции пока не найдены.</div>
            ) : null}

            <div className="transaction-list">
              {transactions.map((item) => (
                <div className="transaction-card" key={item.id || `${item.createdAt}-${item.amount}`}>
                  <div className="transaction-top">
                    <strong>{getTransactionTypeLabel(item.type)}</strong>
                    <span>{formatDate(item.createdAt)}</span>
                  </div>
                  <div className="transaction-grid">
                    <div><span>Игрок</span><strong>{item.publicName || '—'}</strong></div>
                    <div><span>Связанный игрок</span><strong>{item.relatedPublicName || '—'}</strong></div>
                    <div><span>Валюта</span><strong>{getCurrencyLabel(item.currencyType)}</strong></div>
                    <div><span>Сумма</span><strong>{formatMoney(item.amount)}</strong></div>
                    <div><span>Баланс до</span><strong>{formatMoney(item.balanceBefore)}</strong></div>
                    <div><span>Баланс после</span><strong>{formatMoney(item.balanceAfter)}</strong></div>
                    <div><span>Статус</span><strong>{item.status || '—'}</strong></div>
                    <div><span>ID операции</span><strong>{item.id || '—'}</strong></div>
                  </div>
                  {item.comment ? <div className="comment">Комментарий: {item.comment}</div> : null}
                </div>
              ))}
            </div>
          </section>
        )}

        {page === 'deposit' && (
          <section className="panel form-panel">
            <h1>Пополнить игрока</h1>
            <p className="subtext">Зачислить деньги выбранному игроку.</p>
            <form onSubmit={(e) => {
              e.preventDefault();
              submitAction(endpoints.deposit, {
                publicName: depositForm.publicName,
                currencyType: depositForm.currencyType,
                amount: Number(depositForm.amount),
                comment: depositForm.comment || undefined,
              });
            }}>
              <FormFields>
                <label><span>Имя игрока</span><input value={depositForm.publicName} onChange={(e) => setDepositForm({ ...depositForm, publicName: e.target.value })} /></label>
                <label><span>Тип валюты</span><input value={depositForm.currencyType} onChange={(e) => setDepositForm({ ...depositForm, currencyType: e.target.value })} /></label>
                <label><span>Сумма</span><input type="number" step="0.01" value={depositForm.amount} onChange={(e) => setDepositForm({ ...depositForm, amount: e.target.value })} /></label>
                <label className="full"><span>Комментарий</span><textarea rows={4} value={depositForm.comment} onChange={(e) => setDepositForm({ ...depositForm, comment: e.target.value })} /></label>
              </FormFields>
              <ActionFooter loading={actionLoading} error={actionError} success={actionSuccess} result={actionResult} />
            </form>
          </section>
        )}

        {page === 'withdraw' && (
          <section className="panel form-panel">
            <h1>Списать у игрока</h1>
            <p className="subtext">Уменьшить баланс выбранного игрока.</p>
            <form onSubmit={(e) => {
              e.preventDefault();
              submitAction(endpoints.withdraw, {
                publicName: withdrawForm.publicName,
                currencyType: withdrawForm.currencyType,
                amount: Number(withdrawForm.amount),
                comment: withdrawForm.comment || undefined,
              });
            }}>
              <FormFields>
                <label><span>Имя игрока</span><input value={withdrawForm.publicName} onChange={(e) => setWithdrawForm({ ...withdrawForm, publicName: e.target.value })} /></label>
                <label><span>Тип валюты</span><input value={withdrawForm.currencyType} onChange={(e) => setWithdrawForm({ ...withdrawForm, currencyType: e.target.value })} /></label>
                <label><span>Сумма списания</span><input type="number" step="0.01" value={withdrawForm.amount} onChange={(e) => setWithdrawForm({ ...withdrawForm, amount: e.target.value })} /></label>
                <label className="full"><span>Комментарий</span><textarea rows={4} value={withdrawForm.comment} onChange={(e) => setWithdrawForm({ ...withdrawForm, comment: e.target.value })} /></label>
              </FormFields>
              <ActionFooter loading={actionLoading} error={actionError} success={actionSuccess} result={actionResult} />
            </form>
          </section>
        )}

        {page === 'transfer' && (
          <section className="panel form-panel">
            <h1>Перевод между игроками</h1>
            <p className="subtext">Списать деньги у одного игрока и зачислить другому.</p>
            <form onSubmit={(e) => {
              e.preventDefault();
              submitAction(endpoints.transfer, {
                fromPublicName: transferForm.fromPublicName,
                toPublicName: transferForm.toPublicName,
                currencyType: transferForm.currencyType,
                amount: Number(transferForm.amount),
                comment: transferForm.comment || undefined,
              });
            }}>
              <FormFields>
                <label><span>От кого</span><input value={transferForm.fromPublicName} onChange={(e) => setTransferForm({ ...transferForm, fromPublicName: e.target.value })} /></label>
                <label><span>Кому</span><input value={transferForm.toPublicName} onChange={(e) => setTransferForm({ ...transferForm, toPublicName: e.target.value })} /></label>
                <label><span>Тип валюты</span><input value={transferForm.currencyType} onChange={(e) => setTransferForm({ ...transferForm, currencyType: e.target.value })} /></label>
                <label><span>Сумма перевода</span><input type="number" step="0.01" value={transferForm.amount} onChange={(e) => setTransferForm({ ...transferForm, amount: e.target.value })} /></label>
                <label className="full"><span>Комментарий</span><textarea rows={4} value={transferForm.comment} onChange={(e) => setTransferForm({ ...transferForm, comment: e.target.value })} /></label>
              </FormFields>
              <ActionFooter loading={actionLoading} error={actionError} success={actionSuccess} result={actionResult} />
            </form>
          </section>
        )}

        {page === 'adjust' && (
          <section className="panel form-panel">
            <h1>Корректировка баланса</h1>
            <p className="subtext">Ручной режим для увеличения или уменьшения баланса игрока.</p>
            <form onSubmit={(e) => {
              e.preventDefault();
              submitAction(endpoints.adjust, {
                publicName: adjustForm.publicName,
                currencyType: adjustForm.currencyType,
                amount: Number(adjustForm.amount),
                direction: adjustForm.direction,
                comment: adjustForm.comment || undefined,
              });
            }}>
              <FormFields>
                <label><span>Имя игрока</span><input value={adjustForm.publicName} onChange={(e) => setAdjustForm({ ...adjustForm, publicName: e.target.value })} /></label>
                <label><span>Тип валюты</span><input value={adjustForm.currencyType} onChange={(e) => setAdjustForm({ ...adjustForm, currencyType: e.target.value })} /></label>
                <label>
                  <span>Что сделать</span>
                  <select value={adjustForm.direction} onChange={(e) => setAdjustForm({ ...adjustForm, direction: e.target.value })}>
                    <option value="INCREASE">Увеличить баланс</option>
                    <option value="DECREASE">Уменьшить баланс</option>
                  </select>
                </label>
                <label><span>Сумма</span><input type="number" step="0.01" value={adjustForm.amount} onChange={(e) => setAdjustForm({ ...adjustForm, amount: e.target.value })} /></label>
                <label className="full"><span>Комментарий</span><textarea rows={4} value={adjustForm.comment} onChange={(e) => setAdjustForm({ ...adjustForm, comment: e.target.value })} /></label>
              </FormFields>
              <ActionFooter loading={actionLoading} error={actionError} success={actionSuccess} result={actionResult} />
            </form>
          </section>
        )}

        {page === 'reverse' && (
          <section className="panel form-panel">
            <h1>Отменить операцию</h1>
            <p className="subtext">Отменить уже созданную операцию по её ID.</p>
            <form onSubmit={(e) => {
              e.preventDefault();
              submitAction(endpoints.reverse, {
                transactionId: Number(reverseForm.transactionId),
                comment: reverseForm.comment,
              });
            }}>
              <FormFields>
                <label><span>ID операции</span><input type="number" value={reverseForm.transactionId} onChange={(e) => setReverseForm({ ...reverseForm, transactionId: e.target.value })} /></label>
                <label className="full"><span>Почему отменяем</span><textarea rows={4} value={reverseForm.comment} onChange={(e) => setReverseForm({ ...reverseForm, comment: e.target.value })} /></label>
              </FormFields>
              <ActionFooter loading={actionLoading} error={actionError} success={actionSuccess} result={actionResult} />
            </form>
          </section>
        )}
      </main>
    </div>
  );
}

function extractErrorMessage(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    const responseMessage = error.response?.data?.message;
    if (typeof responseMessage === 'string' && responseMessage.trim()) return responseMessage;
    if (typeof error.message === 'string' && error.message.trim()) return error.message;
  }
  return fallback;
}

function FormFields({ children }: { children: React.ReactNode }) {
  return <div className="form-grid">{children}</div>;
}

function ActionFooter({
  loading,
  error,
  success,
  result,
}: {
  loading: boolean;
  error: string;
  success: string;
  result: OperationResultResponse | null;
}) {
  return (
    <div className="action-footer">
      <button className="primary" type="submit" disabled={loading}>{loading ? 'Отправляем...' : 'Выполнить'}</button>
      {error ? <div className="message error">{error}</div> : null}
      {success ? <div className="message success">{success}</div> : null}
      {result ? (
        <div className="result-card">
          <div><span>Игрок</span><strong>{result.publicName || '—'}</strong></div>
          <div><span>Связанный игрок</span><strong>{result.relatedPublicName || '—'}</strong></div>
          <div><span>Сумма</span><strong>{typeof result.amount === 'number' ? formatMoney(result.amount) : '—'}</strong></div>
          <div><span>Валюта</span><strong>{getCurrencyLabel(result.currencyType)}</strong></div>
          <div><span>Баланс после операции</span><strong>{typeof result.balanceAfter === 'number' ? formatMoney(result.balanceAfter) : '—'}</strong></div>
          <div><span>ID новой операции</span><strong>{result.transactionId || '—'}</strong></div>
          {result.message ? <div className="full-row"><span>Сообщение</span><strong>{result.message}</strong></div> : null}
        </div>
      ) : null}
    </div>
  );
}
