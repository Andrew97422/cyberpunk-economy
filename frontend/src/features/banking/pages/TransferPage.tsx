import { useState, FormEvent } from 'react';
import { ENDPOINTS } from '../../../shared/api/config';
import { CURRENCY_TYPES } from '../../../shared/types';
import { getCurrencyLabel } from '../../../shared/utils';
import { useOperation } from '../useOperation';
import OperationResult from '../components/OperationResult';
import AccountPicker from '../../../shared/ui/AccountPicker';

export default function TransferPage() {
  const [form, setForm] = useState({ fromPublicName: '', toPublicName: '', currencyType: 'CASHLESS', amount: '', comment: '' });
  const [fromSel, setFromSel] = useState<{ id: number; publicName: string } | null>(null);
  const [toSel, setToSel] = useState<{ id: number; publicName: string } | null>(null);
  const op = useOperation();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    op.submit(ENDPOINTS.transfer, {
      fromPublicName: form.fromPublicName,
      toPublicName: form.toPublicName,
      currencyType: form.currencyType,
      amount: Number(form.amount),
      comment: form.comment || undefined,
    });
  };

  return (
    <div className="page">
      <h1 className="page-title">Перевод между игроками</h1>
      <p className="subtext">Списать деньги у одного игрока и зачислить другому.</p>

      <form onSubmit={submit} style={{ marginTop: 20 }}>
        <div className="form-grid">
          <div className="form-field">
            <AccountPicker
              label="От кого"
              selected={fromSel}
              onSelect={(a) => { setFromSel({ id: a.id, publicName: a.publicName }); setForm({ ...form, fromPublicName: a.publicName }); }}
              onClear={() => { setFromSel(null); setForm({ ...form, fromPublicName: '' }); }}
            />
          </div>
          <div className="form-field">
            <AccountPicker
              label="Кому"
              selected={toSel}
              onSelect={(a) => { setToSel({ id: a.id, publicName: a.publicName }); setForm({ ...form, toPublicName: a.publicName }); }}
              onClear={() => { setToSel(null); setForm({ ...form, toPublicName: '' }); }}
            />
          </div>
          <label>
            <span>Тип валюты</span>
            <select value={form.currencyType} onChange={(e) => setForm({ ...form, currencyType: e.target.value })}>
              {CURRENCY_TYPES.map((c) => <option key={c} value={c}>{getCurrencyLabel(c)}</option>)}
            </select>
          </label>
          <label><span>Сумма перевода</span><input type="number" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required /></label>
          <label className="full"><span>Комментарий</span><textarea rows={3} value={form.comment} onChange={(e) => setForm({ ...form, comment: e.target.value })} /></label>
        </div>
        <OperationResult {...op} />
      </form>
    </div>
  );
}
