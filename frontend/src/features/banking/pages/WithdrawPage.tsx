import { useState, FormEvent } from 'react';
import { ENDPOINTS } from '../../../shared/api/config';
import { CURRENCY_TYPES } from '../../../shared/types';
import { getCurrencyLabel } from '../../../shared/utils';
import { useOperation } from '../useOperation';
import OperationResult from '../components/OperationResult';
import AccountPicker from '../../../shared/ui/AccountPicker';

export default function WithdrawPage() {
  const [form, setForm] = useState({ publicName: '', currencyType: 'CASHLESS', amount: '', comment: '' });
  const [selected, setSelected] = useState<{ id: number; publicName: string } | null>(null);
  const op = useOperation();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    op.submit(ENDPOINTS.withdraw, {
      publicName: form.publicName,
      currencyType: form.currencyType,
      amount: Number(form.amount),
      comment: form.comment || undefined,
    });
  };

  return (
    <div className="page">
      <h1 className="page-title">Списать у игрока</h1>
      <p className="subtext">Уменьшить баланс выбранного игрока.</p>

      <form onSubmit={submit} style={{ marginTop: 20 }}>
        <div className="form-grid">
          <div className="form-field">
            <AccountPicker
              label="Игрок"
              selected={selected}
              onSelect={(a) => { setSelected({ id: a.id, publicName: a.publicName }); setForm({ ...form, publicName: a.publicName }); }}
              onClear={() => { setSelected(null); setForm({ ...form, publicName: '' }); }}
            />
          </div>
          <label>
            <span>Тип валюты</span>
            <select value={form.currencyType} onChange={(e) => setForm({ ...form, currencyType: e.target.value })}>
              {CURRENCY_TYPES.map((c) => <option key={c} value={c}>{getCurrencyLabel(c)}</option>)}
            </select>
          </label>
          <label><span>Сумма списания</span><input type="number" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} required /></label>
          <label className="full"><span>Комментарий</span><textarea rows={3} value={form.comment} onChange={(e) => setForm({ ...form, comment: e.target.value })} /></label>
        </div>
        <OperationResult {...op} />
      </form>
    </div>
  );
}
