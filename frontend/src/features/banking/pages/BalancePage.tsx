import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { BalanceResponse } from '../../../shared/types';
import { formatMoney, extractError } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import AccountPicker from '../../../shared/ui/AccountPicker';

export default function BalancePage({ selfOnly = false }: { selfOnly?: boolean }) {
  const [balance, setBalance] = useState<BalanceResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<{ id: number; publicName: string } | null>(null);

  const load = async (url: string) => {
    setLoading(true);
    setError('');
    try {
      const res = await client.get<BalanceResponse>(url);
      setBalance(res.data);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  const loadMine = () => {
    setSelected(null);
    load(ENDPOINTS.myBalance);
  };

  useEffect(() => {
    loadMine(); // show something immediately (own balance) without a click
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selfOnly]);

  return (
    <div className="page">
      <div className="section-head">
        <div>
          <h1 className="page-title">{selfOnly ? 'Мой баланс' : 'Баланс'}</h1>
          <p className="subtext">
            {selfOnly ? 'Ваш текущий баланс.' : 'Посмотреть свой баланс или баланс конкретного игрока.'}
          </p>
        </div>
      </div>

      {!selfOnly && (
        <div className="toolbar">
          <button className="btn btn-primary" onClick={loadMine} disabled={loading}>Мой баланс</button>
          <AccountPicker
            placeholder="Найти игрока по имени…"
            selected={selected}
            onSelect={(a) => { setSelected({ id: a.id, publicName: a.publicName }); load(ENDPOINTS.balance(a.id)); }}
            onClear={() => setSelected(null)}
          />
        </div>
      )}

      {error && <ErrorBlock message={error} />}
      {loading && <LoadingBlock text="Загружаем баланс..." />}

      {balance && !loading && (
        <div className="stat-grid">
          <div className="stat-box"><span>Игрок</span><strong>{balance.publicName || '—'}</strong></div>
          <div className="stat-box"><span>Account ID</span><strong>{balance.accountId ?? '—'}</strong></div>
          <div className="stat-box"><span>Безналичные</span><strong>{formatMoney(balance.cashlessAmount)}</strong></div>
          <div className="stat-box"><span>Крипта</span><strong>{formatMoney(balance.cryptoAmount)}</strong></div>
        </div>
      )}
    </div>
  );
}
