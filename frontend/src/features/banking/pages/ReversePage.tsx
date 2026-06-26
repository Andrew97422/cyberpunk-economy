import { useState, FormEvent } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { PagedResponse, TransactionResponse } from '../../../shared/types';
import { formatDate, formatMoney, getCurrencyLabel, getTransactionTypeLabel, extractError } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import AccountPicker from '../../../shared/ui/AccountPicker';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';
import { useOperation } from '../useOperation';
import OperationResult from '../components/OperationResult';

export default function ReversePage() {
  const [form, setForm] = useState({ transactionId: '', comment: '' });
  const [selected, setSelected] = useState<{ id: number; publicName: string } | null>(null);
  const [txs, setTxs] = useState<TransactionResponse[]>([]);
  const [loadingTxs, setLoadingTxs] = useState(false);
  const [txError, setTxError] = useState('');
  const op = useOperation();

  const loadTxs = async (accountId: number) => {
    setLoadingTxs(true);
    setTxError('');
    try {
      const res = await client.get<PagedResponse<TransactionResponse>>(ENDPOINTS.transactions(accountId), { params: { size: 30 } });
      setTxs(res.data.content || []);
    } catch (err) {
      setTxError(extractError(err));
    } finally {
      setLoadingTxs(false);
    }
  };

  const chooseTx = (t: TransactionResponse) => setForm((f) => ({ ...f, transactionId: String(t.id ?? '') }));

  const sort = useTableSort(txs, 'createdAt', 'desc');

  const reversible = (t: TransactionResponse) =>
    t.status === 'SUCCESS' && t.type !== 'REVERSAL_IN' && t.type !== 'REVERSAL_OUT';

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    await op.submit(ENDPOINTS.reverse, { transactionId: Number(form.transactionId), comment: form.comment });
    if (selected) loadTxs(selected.id); // refresh so the reversal is reflected
  };

  return (
    <div className="page" style={{ maxWidth: 1000 }}>
      <h1 className="page-title">Отменить операцию</h1>
      <p className="subtext">Найдите игрока и выберите операцию для отмены — или укажите ID операции вручную.</p>

      <div className="toolbar">
        <AccountPicker
          placeholder="Найти игрока по имени…"
          selected={selected}
          onSelect={(a) => { setSelected({ id: a.id, publicName: a.publicName }); loadTxs(a.id); }}
          onClear={() => { setSelected(null); setTxs([]); }}
        />
      </div>

      {txError && <ErrorBlock message={txError} />}
      {loadingTxs && <LoadingBlock text="Загружаем операции игрока..." />}
      {selected && !loadingTxs && txs.length === 0 && <p className="empty-state">У игрока нет операций.</p>}

      {txs.length > 0 && (
        <div className="page-section" style={{ padding: 0, overflow: 'hidden' }}>
          <table className="table">
            <thead>
              <tr>
                <SortTh k="id" label="ID" sort={sort} />
                <SortTh k="type" label="Тип" sort={sort} />
                <SortTh k="amount" label="Сумма" sort={sort} />
                <SortTh k="currencyType" label="Валюта" sort={sort} />
                <SortTh k="status" label="Статус" sort={sort} />
                <SortTh k="createdAt" label="Дата" sort={sort} />
                <th></th>
              </tr>
            </thead>
            <tbody>
              {sort.sorted.map((t) => (
                <tr key={t.id} className={String(t.id) === form.transactionId ? 'row-open' : undefined}>
                  <td className="mono">{t.id}</td>
                  <td>{getTransactionTypeLabel(t.type)}</td>
                  <td>{formatMoney(t.amount)}</td>
                  <td>{getCurrencyLabel(t.currencyType)}</td>
                  <td>{t.status}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>{formatDate(t.createdAt)}</td>
                  <td className="actions">
                    {reversible(t)
                      ? <button type="button" className="btn btn-sm btn-secondary" onClick={() => chooseTx(t)}>Выбрать</button>
                      : <span className="text-muted">—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form onSubmit={submit} style={{ marginTop: 20 }}>
        <div className="form-grid">
          <label><span>ID операции</span><input type="number" value={form.transactionId} onChange={(e) => setForm({ ...form, transactionId: e.target.value })} required /></label>
          <label className="full"><span>Почему отменяем</span><textarea rows={3} required value={form.comment} onChange={(e) => setForm({ ...form, comment: e.target.value })} /></label>
        </div>
        <OperationResult {...op} submitLabel="Отменить операцию" />
      </form>
    </div>
  );
}
