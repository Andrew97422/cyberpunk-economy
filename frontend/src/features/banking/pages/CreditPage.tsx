import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import { useAuth } from '../../../shared/auth/AuthContext';
import { canBank, extractError, formatMoney, formatDate } from '../../../shared/utils';
import { PageSection, FormField, ErrorBlock, SuccessMessage, ConfirmDialog } from '../../../shared/ui';

interface DepositView { id: number; principal: number; currentAmount: number; openedAt: string; status: string }
interface LoanView { id: number; principal: number; debt: number; openedAt: string; status: string }
interface Overview {
  keyRatePct: number; depositRatePct: number; loanRatePct: number; accrualMinutes: number; maxLoan: number;
  cashlessBalance: number; totalDeposited: number; totalDebt: number;
  deposits: DepositView[]; loans: LoanView[];
}

const KEY_PRESETS = [
  { label: 'Низкая 5%', value: 5 },
  { label: 'Обычная 10%', value: 10 },
  { label: 'Высокая 20%', value: 20 },
  { label: 'Кризис 40%', value: 40 },
];

/** amount after `periods` compoundings at ratePct%. */
function grow(amount: number, ratePct: number, periods: number): number {
  return amount * Math.pow(1 + ratePct / 100, periods);
}

export default function CreditPage() {
  const { user } = useAuth();
  const isOp = canBank(user?.role);

  const [ov, setOv] = useState<Overview | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<{ message: string; run: () => Promise<void> } | null>(null);

  const [depAmt, setDepAmt] = useState('');
  const [loanAmt, setLoanAmt] = useState('');
  const [repay, setRepay] = useState<Record<number, string>>({});

  const [showAdvanced, setShowAdvanced] = useState(false);
  const [depSpread, setDepSpread] = useState('');
  const [loanSpread, setLoanSpread] = useState('');
  const [period, setPeriod] = useState('');
  const [maxLoan, setMaxLoan] = useState('');

  const load = async () => {
    try { const r = await client.get<Overview>(ENDPOINTS.creditOverview); setOv(r.data); }
    catch (err) { setError(extractError(err)); }
  };
  useEffect(() => { load(); }, []);

  const flash = (m: string) => { setSuccess(m); setError(''); setTimeout(() => setSuccess(''), 6000); };
  const run = (message: string, fn: () => Promise<void>) => setConfirm({ message, run: fn });
  const doConfirm = async () => {
    if (!confirm) return;
    setBusy(true); setError('');
    try { await confirm.run(); } catch (err) { setError(extractError(err)); }
    finally { setBusy(false); setConfirm(null); }
  };
  const post = async (url: string, body: unknown, msg: string) => {
    const r = await client.post<Overview>(url, body); setOv(r.data); flash(msg);
  };

  const mins = ov?.accrualMinutes ?? 10;
  const depRate = ov?.depositRatePct ?? 0;
  const loanRate = ov?.loanRatePct ?? 0;

  const openDeposit = () => run(`Открыть вклад на ${formatMoney(Number(depAmt))}?`, async () => {
    await post(ENDPOINTS.openDeposit, { amount: Number(depAmt) }, 'Вклад открыт. Деньги начнут расти.'); setDepAmt('');
  });
  const closeDeposit = (d: DepositView) => run(
    `Забрать вклад? Вернётся ${formatMoney(d.currentAmount)} (клали ${formatMoney(d.principal)}).`,
    async () => post(ENDPOINTS.closeDeposit, { depositId: d.id }, 'Готово — деньги на счету.'));

  const takeLoan = () => run(`Взять кредит ${formatMoney(Number(loanAmt))}? Их сразу зачислят, но долг будет расти, пока не вернёте.`,
    async () => { await post(ENDPOINTS.takeLoan, { amount: Number(loanAmt) }, 'Деньги зачислены на счёт.'); setLoanAmt(''); });
  const repayLoan = (l: LoanView) => {
    const amt = Number(repay[l.id] || 0);
    if (!(amt > 0)) return;
    run(`Внести ${formatMoney(amt)} в счёт кредита (долг ${formatMoney(l.debt)})?`, async () => {
      await post(ENDPOINTS.repayLoan, { loanId: l.id, amount: amt }, 'Платёж принят.');
      setRepay((p) => ({ ...p, [l.id]: '' }));
    });
  };

  const setKeyRate = (value: number, label: string) => run(`Поставить ключевую ставку «${label}»?`, async () =>
    post(ENDPOINTS.creditPolicy, { keyRatePct: value }, 'Ставка изменена.'));

  const saveAdvanced = () => run('Сохранить настройки?', async () => {
    const body: Record<string, number> = {};
    if (depSpread.trim() !== '') body.depositSpreadPct = Number(depSpread);
    if (loanSpread.trim() !== '') body.loanSpreadPct = Number(loanSpread);
    if (period.trim() !== '') body.accrualMinutes = Number(period);
    if (maxLoan.trim() !== '') body.maxLoan = Number(maxLoan);
    await post(ENDPOINTS.creditPolicy, body, 'Настройки сохранены.');
    setDepSpread(''); setLoanSpread(''); setPeriod(''); setMaxLoan('');
  });

  const depNum = Number(depAmt);
  const loanNum = Number(loanAmt);

  return (
    <div className="page" style={{ maxWidth: 820 }}>
      <h1 className="page-title">Кредиты и вклады</h1>

      {/* Plain-language explainer */}
      <div className="page-section" style={{ background: 'rgba(54,211,153,0.06)' }}>
        <p style={{ margin: 0 }}>
          💰 <strong>Вклад</strong> — кладёте деньги в банк, и они сами растут: <strong>+{depRate}%</strong> каждые {mins} мин. Заберёте — получите больше.
        </p>
        <p style={{ margin: '8px 0 0' }}>
          🏦 <strong>Кредит</strong> — берёте деньги сейчас, но долг растёт на <strong>+{loanRate}%</strong> каждые {mins} мин, пока не вернёте. Чем дольше тянете — тем больше отдадите.
        </p>
      </div>

      {error && <ErrorBlock message={error} />}
      {success && <SuccessMessage message={success} />}

      <PageSection title="Мои деньги">
        <div className="stat-grid">
          <div className="stat-box"><span>На счету</span><strong>{formatMoney(ov?.cashlessBalance)}</strong></div>
          <div className="stat-box"><span>В вкладах</span><strong style={{ color: '#36d399' }}>{formatMoney(ov?.totalDeposited)}</strong></div>
          <div className="stat-box"><span>Должен банку</span><strong style={{ color: '#f87272' }}>{formatMoney(ov?.totalDebt)}</strong></div>
        </div>
      </PageSection>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(330px, 1fr))', gap: 16 }}>
        {/* DEPOSITS */}
        <PageSection title="💰 Положить под проценты">
          <FormField label="Сколько положить" id="dep-amt">
            <input id="dep-amt" type="number" step="0.01" min="0" value={depAmt} onChange={(e) => setDepAmt(e.target.value)} disabled={busy} />
          </FormField>
          {depNum > 0 && (
            <p className="subtext">Через {mins} мин будет ≈ <strong>{formatMoney(grow(depNum, depRate, 1))}</strong>, через {mins * 3} мин ≈ <strong>{formatMoney(grow(depNum, depRate, 3))}</strong></p>
          )}
          <button className="btn btn-primary" disabled={busy || !(depNum > 0)} onClick={openDeposit}>Открыть вклад</button>

          <div style={{ marginTop: 14 }}>
            {(ov?.deposits || []).length === 0 && <p className="subtext">У вас нет вкладов.</p>}
            {(ov?.deposits || []).map((d) => (
              <div key={d.id} className="transaction-card" style={{ marginBottom: 8 }}>
                <div className="transaction-top"><strong style={{ color: '#36d399' }}>{formatMoney(d.currentAmount)}</strong><span>с {formatDate(d.openedAt)}</span></div>
                <div className="subtext">Положили {formatMoney(d.principal)} · уже заработали {formatMoney(d.currentAmount - d.principal)}</div>
                <button className="btn btn-sm btn-secondary" style={{ marginTop: 8 }} disabled={busy} onClick={() => closeDeposit(d)}>Забрать с процентами</button>
              </div>
            ))}
          </div>
        </PageSection>

        {/* LOANS */}
        <PageSection title="🏦 Взять в долг">
          <FormField label={`Сколько взять (максимум ${formatMoney(ov?.maxLoan)})`} id="loan-amt">
            <input id="loan-amt" type="number" step="0.01" min="0" value={loanAmt} onChange={(e) => setLoanAmt(e.target.value)} disabled={busy} />
          </FormField>
          {loanNum > 0 && (
            <p className="subtext">Зачислим {formatMoney(loanNum)} сразу. Вернуть: через {mins} мин ≈ <strong>{formatMoney(grow(loanNum, loanRate, 1))}</strong>, через {mins * 3} мин ≈ <strong>{formatMoney(grow(loanNum, loanRate, 3))}</strong></p>
          )}
          <button className="btn btn-primary" disabled={busy || !(loanNum > 0)} onClick={takeLoan}>Взять кредит</button>

          <div style={{ marginTop: 14 }}>
            {(ov?.loans || []).length === 0 && <p className="subtext">У вас нет кредитов.</p>}
            {(ov?.loans || []).map((l) => (
              <div key={l.id} className="transaction-card" style={{ marginBottom: 8 }}>
                <div className="transaction-top"><strong style={{ color: '#f87272' }}>Нужно вернуть {formatMoney(l.debt)}</strong><span>с {formatDate(l.openedAt)}</span></div>
                <div className="subtext">Брали {formatMoney(l.principal)} · набежало {formatMoney(l.debt - l.principal)}</div>
                <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
                  <input type="number" step="0.01" min="0" placeholder="сумма" value={repay[l.id] || ''}
                         onChange={(e) => setRepay((p) => ({ ...p, [l.id]: e.target.value }))} style={{ maxWidth: 110 }} disabled={busy} />
                  <button className="btn btn-sm btn-secondary" disabled={busy} onClick={() => repayLoan(l)}>Внести</button>
                  <button className="btn btn-sm btn-ghost" disabled={busy} onClick={() => setRepay((p) => ({ ...p, [l.id]: String(l.debt) }))}>Погасить всё</button>
                </div>
              </div>
            ))}
          </div>
        </PageSection>
      </div>

      {isOp && (
        <PageSection title="⚙ Управление банком (админ)">
          <p className="subtext" style={{ marginBottom: 8 }}>
            Сейчас: <strong>ключевая ставка {ov?.keyRatePct}%</strong> → вклады растут на <strong>{depRate}%</strong>, кредиты дорожают на <strong>{loanRate}%</strong> каждые {mins} мин.
          </p>
          <div className="subtext" style={{ marginBottom: 6 }}>Изменить ключевую ставку:</div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {KEY_PRESETS.map((p) => (
              <button key={p.label} className="btn btn-secondary btn-sm" disabled={busy} onClick={() => setKeyRate(p.value, p.label)}>{p.label}</button>
            ))}
          </div>

          <button className="btn btn-ghost btn-sm" style={{ marginTop: 14 }} onClick={() => setShowAdvanced((s) => !s)}>
            {showAdvanced ? '▾ Скрыть дополнительные настройки' : '▸ Дополнительные настройки'}
          </button>
          {showAdvanced && (
            <div className="form-grid" style={{ marginTop: 10 }}>
              <FormField label="Наценка по вкладам ниже ставки, %" id="p-deps">
                <input id="p-deps" type="number" step="0.1" value={depSpread} onChange={(e) => setDepSpread(e.target.value)} placeholder={ov ? String(ov.keyRatePct - ov.depositRatePct) : ''} />
              </FormField>
              <FormField label="Наценка по кредитам выше ставки, %" id="p-loans">
                <input id="p-loans" type="number" step="0.1" value={loanSpread} onChange={(e) => setLoanSpread(e.target.value)} placeholder={ov ? String(ov.loanRatePct - ov.keyRatePct) : ''} />
              </FormField>
              <FormField label="Как часто начислять, мин" id="p-period">
                <input id="p-period" type="number" step="1" min="1" value={period} onChange={(e) => setPeriod(e.target.value)} placeholder={String(mins)} />
              </FormField>
              <FormField label="Лимит кредита" id="p-max">
                <input id="p-max" type="number" step="1" value={maxLoan} onChange={(e) => setMaxLoan(e.target.value)} placeholder={ov ? String(ov.maxLoan) : ''} />
              </FormField>
              <div className="full"><button className="btn btn-secondary btn-sm" disabled={busy} onClick={saveAdvanced}>Сохранить</button></div>
            </div>
          )}
        </PageSection>
      )}

      {confirm && <ConfirmDialog message={confirm.message} onConfirm={doConfirm} onCancel={() => setConfirm(null)} />}
    </div>
  );
}
