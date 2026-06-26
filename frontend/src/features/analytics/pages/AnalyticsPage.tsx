import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import { extractError, formatMoney, formatDate, ROLE_LABELS, AUDIT_EVENT_TYPE_LABELS } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';

interface Named { name?: string; publicName?: string; }
interface Overview {
  generatedAt: string;
  money: { supplyCashless: number; supplyCrypto: number; accountsWithBalance: number };
  flows: {
    totalOperations: number; volumeCashless: number; volumeCrypto: number;
    byOperation: { operation: string; count: number; amount: number }[];
    byType: { type: string; count: number }[];
  };
  accounts: { total: number; active: number; byRole: { role: string; count: number }[] };
  marketplace: {
    ordersPaid: number; ordersCancelled: number; revenueCashless: number; revenueCrypto: number;
    topProducts: { name: string; revenue: number; quantity: number }[];
  };
  topRichest: { accountId: number; publicName: string; cashless: number; crypto: number }[];
  topSpenders: { publicName: string; total: number }[];
  activityByDay: { day: string; operations: number; volume: number }[];
  newAccountsByDay: { day: string; count: number }[];
}

const OP_LABELS: Record<string, string> = {
  DEPOSIT: 'Пополнения', WITHDRAW: 'Списания', TRANSFER: 'Переводы (исх.)',
  TRANSFER_IN: 'Переводы (вх.)', PURCHASE: 'Покупки', EVENT: 'Эконом. события',
  REVERSAL: 'Отмены', '—': 'Прочее',
};

function Card({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="dashboard-card" style={{ minWidth: 170 }}>
      <div className="dashboard-card-label">{label}</div>
      <div className="dashboard-card-value" style={accent ? { color: 'var(--accent)' } : undefined}>{value}</div>
    </div>
  );
}

/** Horizontal labelled bars, width relative to the max value in the set. */
function Bars({ rows }: { rows: { label: string; value: number; sub?: string }[] }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  if (rows.length === 0) return <p className="empty-state">Нет данных</p>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {rows.map((r, i) => (
        <div key={i}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 3 }}>
            <span>{r.label}</span>
            <strong style={{ color: 'var(--text)' }}>{r.sub ?? r.value}</strong>
          </div>
          <div style={{ height: 8, background: 'var(--surface-2, rgba(255,255,255,0.06))', borderRadius: 4, overflow: 'hidden' }}>
            <div style={{ width: `${(r.value / max) * 100}%`, height: '100%', background: 'var(--accent)', borderRadius: 4 }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="page-section" style={{ marginTop: 16 }}>
      <h2 className="section-title" style={{ marginBottom: 12 }}>{title}</h2>
      {children}
    </div>
  );
}

export default function AnalyticsPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await client.get<Overview>(ENDPOINTS.analyticsOverview);
      setData(res.data);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const named = (n: Named) => n.publicName || n.name || '—';

  return (
    <div className="page" style={{ maxWidth: 1100 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
        <h1 className="page-title" style={{ marginBottom: 0 }}>Аналитика экономики</h1>
        <button className="btn btn-ghost btn-sm" onClick={load} disabled={loading}>↻ Обновить</button>
      </div>
      <p className="subtext">Сводная картина денег, операций, рынка и игроков Найт-Сити.</p>

      {error && <ErrorBlock message={error} />}
      {loading && <LoadingBlock text="Считаем экономику..." />}

      {data && !loading && (
        <>
          <div className="stat-grid" style={{ marginTop: 12 }}>
            <Card label="Денежная масса (нал)" value={formatMoney(data.money.supplyCashless)} accent />
            <Card label="Денежная масса (крипта)" value={formatMoney(data.money.supplyCrypto)} accent />
            <Card label="Всего операций" value={String(data.flows.totalOperations)} />
            <Card label="Оборот (нал)" value={formatMoney(data.flows.volumeCashless)} />
            <Card label="Счетов с балансом" value={String(data.money.accountsWithBalance)} />
            <Card label="Аккаунтов (активных)" value={`${data.accounts.total} (${data.accounts.active})`} />
            <Card label="Продаж оплачено" value={String(data.marketplace.ordersPaid)} />
            <Card label="Выручка рынка (нал)" value={formatMoney(data.marketplace.revenueCashless)} accent />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16, marginTop: 0 }}>
            <Section title="Операции по типам">
              <Bars rows={data.flows.byOperation.map((o) => ({
                label: OP_LABELS[o.operation] || o.operation,
                value: o.count,
                sub: `${o.count} · ${formatMoney(o.amount)}`,
              }))} />
            </Section>

            <Section title="Активность по дням">
              <Bars rows={data.activityByDay.map((d) => ({
                label: formatDate(d.day).slice(0, 5),
                value: d.operations,
                sub: `${d.operations} оп · ${formatMoney(d.volume)}`,
              }))} />
            </Section>

            <Section title="Топ-богачи (по налу)">
              <Bars rows={data.topRichest.map((r) => ({
                label: r.publicName,
                value: r.cashless,
                sub: `${formatMoney(r.cashless)}${r.crypto ? ` · ${formatMoney(r.crypto)}₿` : ''}`,
              }))} />
            </Section>

            <Section title="Топ-покупатели">
              <Bars rows={data.topSpenders.map((s) => ({
                label: named(s),
                value: s.total,
                sub: formatMoney(s.total),
              }))} />
            </Section>

            <Section title="Топ-товары">
              <Bars rows={data.marketplace.topProducts.map((p) => ({
                label: p.name,
                value: p.revenue,
                sub: `${formatMoney(p.revenue)} · ${p.quantity} шт`,
              }))} />
            </Section>

            <Section title="Аккаунты по ролям">
              <Bars rows={data.accounts.byRole.map((r) => ({
                label: ROLE_LABELS[r.role] || r.role,
                value: r.count,
                sub: String(r.count),
              }))} />
            </Section>

            <Section title="Распределение событий">
              <Bars rows={data.flows.byType.map((t) => ({
                label: AUDIT_EVENT_TYPE_LABELS[t.type] || t.type,
                value: t.count,
                sub: String(t.count),
              }))} />
            </Section>

            <Section title="Новые аккаунты по дням">
              <Bars rows={data.newAccountsByDay.map((d) => ({
                label: formatDate(d.day).slice(0, 5),
                value: d.count,
                sub: String(d.count),
              }))} />
            </Section>
          </div>

          <p className="subtext" style={{ marginTop: 16 }}>
            Обновлено: {formatDate(data.generatedAt)} · рынок: отменено заказов {data.marketplace.ordersCancelled}
          </p>
        </>
      )}
    </div>
  );
}
