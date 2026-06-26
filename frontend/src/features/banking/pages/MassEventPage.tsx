import { useState, FormEvent } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import { CURRENCY_TYPES } from '../../../shared/types';
import { getCurrencyLabel, extractError, formatMoney } from '../../../shared/utils';
import { ErrorBlock } from '../../../shared/ui';

type Mode = 'CREDIT' | 'DEBIT' | 'MULTIPLY';

interface MassResult {
  success: boolean;
  mode: string;
  currencyType: string;
  affected: number;
  totalDelta: number;
  message: string;
}

const MODES: { value: Mode; label: string; hint: string; valueLabel: string; placeholder: string }[] = [
  { value: 'CREDIT', label: 'Начислить всем (+)', hint: 'Добавить фиксированную сумму к балансу каждого.', valueLabel: 'Сумма к начислению', placeholder: 'например 500' },
  { value: 'DEBIT', label: 'Списать у всех (−)', hint: 'Списать фиксированную сумму (баланс не уйдёт в минус).', valueLabel: 'Сумма к списанию', placeholder: 'например 200' },
  { value: 'MULTIPLY', label: 'Умножить баланс (× коэффициент)', hint: 'Инфляция/обвал: 0.9 = −10%, 1.1 = +10%, 2 = удвоить.', valueLabel: 'Коэффициент', placeholder: 'например 0.9' },
];

const TARGETS = [
  { value: '', label: 'Все счета' },
  { value: 'PLAYER', label: 'Только игроки' },
];

export default function MassEventPage() {
  const [mode, setMode] = useState<Mode>('CREDIT');
  const [currencyType, setCurrencyType] = useState<string>('CASHLESS');
  const [value, setValue] = useState('');
  const [targetRole, setTargetRole] = useState('');
  const [comment, setComment] = useState('');

  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<MassResult | null>(null);

  const modeInfo = MODES.find((m) => m.value === mode)!;
  const targetLabel = TARGETS.find((t) => t.value === targetRole)?.label ?? 'Все счета';
  const numValue = Number(value);
  const valid = value.trim() !== '' && !Number.isNaN(numValue) && numValue >= 0 && (mode === 'MULTIPLY' || numValue > 0);

  const summary = () => {
    const cur = getCurrencyLabel(currencyType);
    if (mode === 'CREDIT') return `Начислить +${formatMoney(numValue)} ${cur} каждому счёту (${targetLabel.toLowerCase()}).`;
    if (mode === 'DEBIT') return `Списать −${formatMoney(numValue)} ${cur} с каждого счёта (${targetLabel.toLowerCase()}).`;
    const pct = Math.round((numValue - 1) * 100);
    const dir = pct === 0 ? 'без изменения' : pct > 0 ? `+${pct}%` : `${pct}%`;
    return `Умножить балансы ${cur} на ×${numValue} (${dir}) для всех счетов (${targetLabel.toLowerCase()}).`;
  };

  const openConfirm = (e: FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    setError('');
    setResult(null);
    setConfirming(true);
  };

  const apply = async () => {
    setBusy(true);
    setError('');
    try {
      const res = await client.post<MassResult>(ENDPOINTS.massOperation, {
        mode,
        currencyType,
        value: numValue,
        targetRole: targetRole || undefined,
        comment: comment.trim() || undefined,
      });
      setResult(res.data);
      setConfirming(false);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page" style={{ maxWidth: 720 }}>
      <h1 className="page-title">Экономическое событие</h1>
      <p className="subtext">
        Массовое изменение балансов всех игроков сразу — начисление, списание или умножение (инфляция / обвал).
        Каждое изменение записывается в историю операций и аудит.
      </p>

      <form onSubmit={openConfirm} style={{ marginTop: 20 }}>
        <div className="form-grid">
          <label className="full">
            <span>Тип события</span>
            <select value={mode} onChange={(e) => setMode(e.target.value as Mode)}>
              {MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
            </select>
          </label>
          <p className="subtext full" style={{ marginTop: -8 }}>{modeInfo.hint}</p>

          <label>
            <span>Тип валюты</span>
            <select value={currencyType} onChange={(e) => setCurrencyType(e.target.value)}>
              {CURRENCY_TYPES.map((c) => <option key={c} value={c}>{getCurrencyLabel(c)}</option>)}
            </select>
          </label>
          <label>
            <span>Кому применить</span>
            <select value={targetRole} onChange={(e) => setTargetRole(e.target.value)}>
              {TARGETS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </label>
          <label>
            <span>{modeInfo.valueLabel}</span>
            <input type="number" step={mode === 'MULTIPLY' ? '0.01' : '0.01'} min="0" value={value}
                   placeholder={modeInfo.placeholder}
                   onChange={(e) => setValue(e.target.value)} required />
          </label>
          <label className="full">
            <span>Комментарий (попадёт в каждую операцию)</span>
            <input type="text" value={comment} placeholder="например: Кризис на бирже Найт-Сити"
                   onChange={(e) => setComment(e.target.value)} />
          </label>
        </div>

        {valid && (
          <div className="page-section" style={{ marginTop: 12 }}>
            <strong>Что произойдёт:</strong>
            <p style={{ marginTop: 6 }}>{summary()}</p>
          </div>
        )}

        {error && <ErrorBlock message={error} />}

        {result && (
          <div className="success-block" style={{ marginTop: 12 }}>
            ✔ {result.message}. Изменено счетов: <strong>{result.affected}</strong>,
            суммарное изменение: <strong>{formatMoney(result.totalDelta)} {getCurrencyLabel(result.currencyType)}</strong>.
          </div>
        )}

        <div style={{ marginTop: 16 }}>
          <button className="btn btn-primary" type="submit" disabled={!valid || busy}>Применить событие…</button>
        </div>
      </form>

      {confirming && (
        <div className="overlay" onClick={() => !busy && setConfirming(false)}>
          <div className="dialog" onClick={(e) => e.stopPropagation()}>
            <h2 className="section-title">Подтвердите экономическое событие</h2>
            <p style={{ margin: '12px 0' }}>{summary()}</p>
            <p className="subtext" style={{ marginBottom: 12 }}>
              Действие затронет балансы сразу всех счетов и не может быть отменено одним кликом
              (только пересчётом обратного события). Все изменения попадут в аудит.
            </p>
            {error && <ErrorBlock message={error} />}
            <div className="dialog-actions" style={{ marginTop: 16 }}>
              <button className="btn btn-danger" onClick={apply} disabled={busy}>
                {busy ? 'Применяю…' : 'Да, применить ко всем'}
              </button>
              <button className="btn btn-secondary" onClick={() => setConfirming(false)} disabled={busy}>Отмена</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
