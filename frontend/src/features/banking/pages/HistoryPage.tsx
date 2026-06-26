import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { OperationResultResponse, PagedResponse, TransactionResponse } from '../../../shared/types';
import { formatDate, formatMoney, getCurrencyLabel, getTransactionTypeLabel, extractError } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import AccountPicker from '../../../shared/ui/AccountPicker';
import Receipt from '../components/Receipt';

export default function HistoryPage({ selfOnly = false }: { selfOnly?: boolean }) {
  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<{ id: number; publicName: string } | null>(null);
  const [lastUrl, setLastUrl] = useState(ENDPOINTS.myTransactions);

  // reverse-from-history dialog
  const [reversing, setReversing] = useState<TransactionResponse | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState('');

  // receipt modal
  const [receipt, setReceipt] = useState<TransactionResponse | null>(null);

  const load = async (url: string) => {
    setLastUrl(url);
    setLoading(true);
    setError('');
    try {
      const res = await client.get<PagedResponse<TransactionResponse>>(url);
      setTransactions(res.data.content || []);
      setLoaded(true);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  const loadMine = () => {
    setSelected(null);
    load(ENDPOINTS.myTransactions);
  };

  useEffect(() => {
    loadMine(); // show something immediately without a click
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selfOnly]);

  // Operators can reverse a successful, non-reversal operation straight from the list.
  const canReverse = (t: TransactionResponse) =>
    !selfOnly && !!t.id && t.status === 'SUCCESS' && t.type !== 'REVERSAL_IN' && t.type !== 'REVERSAL_OUT';

  const openReverse = (t: TransactionResponse) => {
    setReversing(t);
    setReason('');
    setDialogError('');
  };

  const confirmReverse = async () => {
    if (!reversing) return;
    setBusy(true);
    setDialogError('');
    try {
      await client.post<OperationResultResponse>(ENDPOINTS.reverse, {
        transactionId: reversing.id,
        comment: reason.trim() || `Отмена операции #${reversing.id} из истории`,
      });
      setReversing(null);
      load(lastUrl); // refresh so the reversal + restored balance are reflected
    } catch (err) {
      setDialogError(extractError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <h1 className="page-title">История операций</h1>
      <p className="subtext">{selfOnly ? 'Ваши последние операции.' : 'Последние операции по выбранному игроку.'}</p>

      {!selfOnly && (
        <div className="toolbar">
          <button className="btn btn-primary" onClick={loadMine} disabled={loading}>Мои операции</button>
          <AccountPicker
            placeholder="Найти игрока по имени…"
            selected={selected}
            onSelect={(a) => { setSelected({ id: a.id, publicName: a.publicName }); load(ENDPOINTS.transactions(a.id)); }}
            onClear={() => setSelected(null)}
          />
        </div>
      )}

      {error && <ErrorBlock message={error} />}
      {loading && <LoadingBlock text="Загружаем историю операций..." />}
      {loaded && !loading && transactions.length === 0 && <p className="empty-state">Операции не найдены.</p>}

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
            {item.comment && <div className="comment">Комментарий: {item.comment}</div>}
            <div className="action-footer" style={{ marginTop: 12 }}>
              <button className="btn btn-sm btn-ghost" onClick={() => setReceipt(item)}>Чек</button>
              {canReverse(item) && (
                <button className="btn btn-sm btn-danger" onClick={() => openReverse(item)}>Отменить операцию</button>
              )}
            </div>
          </div>
        ))}
      </div>

      {reversing && (
        <div className="overlay" onClick={() => !busy && setReversing(null)}>
          <div className="dialog" onClick={(e) => e.stopPropagation()}>
            <h2 className="section-title">Отменить операцию #{reversing.id}?</h2>
            <p style={{ marginBottom: 12 }}>
              {getTransactionTypeLabel(reversing.type)} · {formatMoney(reversing.amount)} {getCurrencyLabel(reversing.currencyType)}
              {reversing.publicName ? ` · ${reversing.publicName}` : ''}
            </p>
            <p className="subtext" style={{ marginBottom: 12 }}>
              Операция будет откатана, баланс восстановлен. Это действие отражается в аудите.
            </p>
            <label className="field" style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <span>Причина (необязательно)</span>
              <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Например: ошибочное зачисление" />
            </label>
            {dialogError && <div style={{ marginTop: 10 }}><ErrorBlock message={dialogError} /></div>}
            <div className="dialog-actions" style={{ marginTop: 16 }}>
              <button className="btn btn-danger" onClick={confirmReverse} disabled={busy}>
                {busy ? 'Отменяю…' : 'Подтвердить отмену'}
              </button>
              <button className="btn btn-secondary" onClick={() => setReversing(null)} disabled={busy}>Отмена</button>
            </div>
          </div>
        </div>
      )}

      {receipt && <Receipt kind="transaction" data={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
