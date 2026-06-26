import { useCallback, useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { BalanceResponse } from '../../../shared/types';
import { useAuth } from '../../../shared/auth/AuthContext';
import { canBank, extractError, formatMoney } from '../../../shared/utils';
import { PageSection, FormField, ErrorBlock, SuccessMessage, ConfirmDialog } from '../../../shared/ui';

interface TickPoint { rate: number; at: string }
interface RateResponse {
  rate: number; baseline: number; drift: number; volatility: number;
  minRate: number; maxRate: number; updatedAt: string; history: TickPoint[];
}

// "Тренд" = направление, в котором админ толкает рынок.
const TREND_PRESETS: { label: string; value: number }[] = [
  { label: '📈 Сильный рост', value: 0.04 },
  { label: '🔺 Лёгкий рост', value: 0.015 },
  { label: '➖ Стабильно', value: 0 },
  { label: '🔻 Лёгкое падение', value: -0.015 },
  { label: '📉 Сильный обвал', value: -0.04 },
];
// "Разброс" = насколько сильно курс скачет сам по себе.
const SPREAD_PRESETS: { label: string; value: number }[] = [
  { label: '😌 Спокойно', value: 0.02 },
  { label: '🌊 Обычно', value: 0.05 },
  { label: '🌪 Буйно', value: 0.12 },
];

function describeTrend(d: number): string {
  if (d >= 0.025) return 'быстро растёт';
  if (d > 0) return 'растёт';
  if (d === 0) return 'стабилен';
  if (d > -0.025) return 'падает';
  return 'быстро падает';
}
function describeSpread(v: number): string {
  if (v < 0.03) return 'спокойный';
  if (v < 0.08) return 'обычный';
  return 'буйный';
}

function Sparkline({ points }: { points: number[] }) {
  if (points.length < 2) return <div className="subtext">Пока мало данных для графика</div>;
  const w = 600, h = 80, pad = 4;
  const min = Math.min(...points), max = Math.max(...points);
  const span = max - min || 1;
  const step = (w - pad * 2) / (points.length - 1);
  const path = points
    .map((p, i) => `${pad + i * step},${pad + (h - pad * 2) * (1 - (p - min) / span)}`)
    .join(' ');
  const up = points[points.length - 1] >= points[0];
  return (
    <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={h} preserveAspectRatio="none" style={{ display: 'block' }}>
      <polyline points={path} fill="none" stroke={up ? '#36d399' : '#f87272'} strokeWidth={2} />
    </svg>
  );
}

export default function CryptoExchangePage() {
  const { user } = useAuth();
  const isOp = canBank(user?.role);

  const [rate, setRate] = useState<RateResponse | null>(null);
  const [balance, setBalance] = useState<BalanceResponse | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);

  const [buyAmt, setBuyAmt] = useState('');
  const [sellAmt, setSellAmt] = useState('');
  const [confirm, setConfirm] = useState<{ message: string; run: () => Promise<void> } | null>(null);

  const [showAdvanced, setShowAdvanced] = useState(false);
  const [baseline, setBaseline] = useState('');
  const [shockPct, setShockPct] = useState('');
  const [shockReason, setShockReason] = useState('');

  const loadRate = useCallback(async () => {
    try { const res = await client.get<RateResponse>(ENDPOINTS.cryptoRate); setRate(res.data); }
    catch (err) { setError(extractError(err)); }
  }, []);
  const loadBalance = useCallback(() =>
    client.get<BalanceResponse>(ENDPOINTS.myBalance).then((r) => setBalance(r.data)).catch(() => setBalance(null)),
  []);

  useEffect(() => {
    loadRate(); loadBalance();
    const t = setInterval(loadRate, 20000);
    return () => clearInterval(t);
  }, [loadRate, loadBalance]);

  const flash = (m: string) => { setSuccess(m); setError(''); setTimeout(() => setSuccess(''), 6000); };
  const run = (message: string, fn: () => Promise<void>) => setConfirm({ message, run: fn });
  const doConfirm = async () => {
    if (!confirm) return;
    setBusy(true); setError('');
    try { await confirm.run(); } catch (err) { setError(extractError(err)); }
    finally { setBusy(false); setConfirm(null); }
  };

  const r = rate?.rate ?? 0;
  const buyNum = Number(buyAmt);
  const sellNum = Number(sellAmt);
  const buyEst = r > 0 && buyNum > 0 ? buyNum / r : 0;
  const sellEst = r > 0 && sellNum > 0 ? sellNum * r : 0;

  const buy = () => run(
    `Купить крипту на ${formatMoney(buyNum)} (получите ≈ ${formatMoney(buyEst)} ₿)?`,
    async () => { await client.post(ENDPOINTS.cryptoBuy, { amount: buyNum }); flash('Куплено!'); setBuyAmt(''); await Promise.all([loadRate(), loadBalance()]); });
  const sell = () => run(
    `Продать ${formatMoney(sellNum)} ₿ (получите ≈ ${formatMoney(sellEst)})?`,
    async () => { await client.post(ENDPOINTS.cryptoSell, { amount: sellNum }); flash('Продано!'); setSellAmt(''); await Promise.all([loadRate(), loadBalance()]); });

  const setTrend = (value: number, label: string) => run(`Сделать так, чтобы рынок: «${label}»?`,
    async () => { const res = await client.post<RateResponse>(ENDPOINTS.cryptoMarket, { drift: value }); setRate(res.data); flash('Готово.'); });
  const setSpread = (value: number, label: string) => run(`Сделать курс «${label}»?`,
    async () => { const res = await client.post<RateResponse>(ENDPOINTS.cryptoMarket, { volatility: value }); setRate(res.data); flash('Готово.'); });
  const applyBaseline = () => run('Сохранить базовый курс?', async () => {
    const res = await client.post<RateResponse>(ENDPOINTS.cryptoMarket, { baseline: Number(baseline) }); setRate(res.data); setBaseline(''); flash('Готово.');
  });
  const shock = (pct: number, reason: string) => run(
    `Резкое событие: курс ${pct > 0 ? 'подскочит' : 'рухнет'} на ${Math.abs(pct)}% (${reason || 'без описания'})?`,
    async () => { const res = await client.post<RateResponse>(ENDPOINTS.cryptoShock, { pct, reason }); setRate(res.data); setShockPct(''); setShockReason(''); flash('Применено!'); });

  const cryptoValue = balance && r ? (balance.cryptoAmount || 0) * r : 0;
  const prev = rate && rate.history.length >= 2 ? rate.history[rate.history.length - 2].rate : r;
  const up = r >= prev;

  return (
    <div className="page" style={{ maxWidth: 820 }}>
      <h1 className="page-title">Криптобиржа</h1>

      <div className="page-section" style={{ background: 'rgba(54,211,153,0.06)' }}>
        <p style={{ margin: 0 }}>
          📈 Курс крипты <strong>всё время меняется</strong>. Купите, когда дёшево, продайте, когда дорого — заработаете. Но если курс упадёт — потеряете. Рискуйте с умом!
        </p>
      </div>

      {error && <ErrorBlock message={error} />}
      {success && <SuccessMessage message={success} />}

      <PageSection title="Курс сейчас">
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 34, fontWeight: 700, color: up ? '#36d399' : '#f87272' }}>
            {formatMoney(r)} {up ? '▲' : '▼'}
          </span>
          <span className="subtext">за 1 ₿ · обновляется каждые ~45 секунд</span>
        </div>
        <div style={{ marginTop: 10 }}><Sparkline points={(rate?.history || []).map((h) => h.rate)} /></div>
        <button className="btn btn-ghost btn-sm" style={{ marginTop: 8 }} onClick={loadRate}>↻ Обновить</button>
      </PageSection>

      <PageSection title="Мои деньги">
        <div className="stat-grid">
          <div className="stat-box"><span>На счету</span><strong>{formatMoney(balance?.cashlessAmount)}</strong></div>
          <div className="stat-box"><span>Крипта</span><strong>{formatMoney(balance?.cryptoAmount)} ₿</strong></div>
          <div className="stat-box"><span>Крипта в деньгах</span><strong>{formatMoney(cryptoValue)}</strong></div>
        </div>
      </PageSection>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 16 }}>
        <PageSection title="Купить">
          <FormField label="Потратить денег" id="buy-amt">
            <input id="buy-amt" type="number" step="0.01" min="0" value={buyAmt} onChange={(e) => setBuyAmt(e.target.value)} disabled={busy} />
          </FormField>
          <p className="subtext">Получите ≈ <strong>{formatMoney(buyEst)} ₿</strong></p>
          <button className="btn btn-primary" disabled={busy || buyNum <= 0} onClick={buy}>Купить крипту</button>
        </PageSection>

        <PageSection title="Продать">
          <FormField label="Продать крипты (₿)" id="sell-amt">
            <input id="sell-amt" type="number" step="0.01" min="0" value={sellAmt} onChange={(e) => setSellAmt(e.target.value)} disabled={busy} />
          </FormField>
          <p className="subtext">Получите ≈ <strong>{formatMoney(sellEst)}</strong></p>
          <button className="btn btn-primary" disabled={busy || sellNum <= 0} onClick={sell}>Продать крипту</button>
        </PageSection>
      </div>

      {isOp && (
        <PageSection title="⚙ Управление биржей (админ)">
          <p className="subtext" style={{ marginBottom: 10 }}>
            Сейчас рынок <strong>{rate ? describeTrend(rate.drift) : '—'}</strong>, курс <strong>{rate ? describeSpread(rate.volatility) : '—'}</strong>.
          </p>

          <div className="subtext" style={{ marginBottom: 6 }}>Куда двигать рынок:</div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
            {TREND_PRESETS.map((p) => (
              <button key={p.label} className="btn btn-secondary btn-sm" disabled={busy} onClick={() => setTrend(p.value, p.label)}>{p.label}</button>
            ))}
          </div>

          <div className="subtext" style={{ marginBottom: 6 }}>Насколько сильно курс скачет:</div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
            {SPREAD_PRESETS.map((p) => (
              <button key={p.label} className="btn btn-secondary btn-sm" disabled={busy} onClick={() => setSpread(p.value, p.label)}>{p.label}</button>
            ))}
          </div>

          <div className="subtext" style={{ marginBottom: 6 }}>Резкое событие (новость, скандал):</div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => shock(-30, 'Корпоративный скандал')}>−30% обвал</button>
            <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => shock(-50, 'Крах биржи')}>−50% крах</button>
            <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => shock(50, 'Прорыв технологии')}>+50% взлёт</button>
          </div>

          <button className="btn btn-ghost btn-sm" style={{ marginTop: 14 }} onClick={() => setShowAdvanced((s) => !s)}>
            {showAdvanced ? '▾ Скрыть дополнительное' : '▸ Дополнительно'}
          </button>
          {showAdvanced && (
            <div style={{ marginTop: 10 }}>
              <div className="form-grid">
                <FormField label="Базовый курс (куда курс тянется со временем)" id="base">
                  <input id="base" type="number" step="0.01" min="0" value={baseline} onChange={(e) => setBaseline(e.target.value)} placeholder={rate ? String(rate.baseline) : ''} />
                </FormField>
                <div className="full"><button className="btn btn-secondary btn-sm" disabled={busy || baseline.trim() === ''} onClick={applyBaseline}>Сохранить базовый курс</button></div>
              </div>
              <div className="form-grid" style={{ marginTop: 8 }}>
                <FormField label="Своё событие, %" id="shock-pct">
                  <input id="shock-pct" type="number" step="1" value={shockPct} onChange={(e) => setShockPct(e.target.value)} placeholder="например -20 или 30" />
                </FormField>
                <FormField label="Описание" id="shock-reason">
                  <input id="shock-reason" type="text" value={shockReason} onChange={(e) => setShockReason(e.target.value)} />
                </FormField>
                <div className="full"><button className="btn btn-secondary btn-sm" disabled={busy || shockPct.trim() === ''} onClick={() => shock(Number(shockPct), shockReason)}>Применить событие</button></div>
              </div>
            </div>
          )}
        </PageSection>
      )}

      {confirm && <ConfirmDialog message={confirm.message} onConfirm={doConfirm} onCancel={() => setConfirm(null)} />}
    </div>
  );
}
