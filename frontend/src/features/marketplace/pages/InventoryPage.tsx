import { useEffect, useMemo, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { OrderResponse, PagedResponse } from '../../../shared/types';
import { extractError, formatDate, formatMoney, getCurrencyLabel } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';

interface InventoryItem {
  productName: string;
  quantity: number;
  total: number;
  currencyType: string;
  createdAt: string;
}

function aggregate(orders: OrderResponse[]): InventoryItem[] {
  const byName = new Map<string, InventoryItem>();
  for (const o of orders) {
    const createdAt = o.createdAt ?? '';
    const prev = byName.get(o.productName);
    if (!prev) {
      byName.set(o.productName, {
        productName: o.productName,
        quantity: o.quantity,
        total: o.totalPrice,
        currencyType: o.currencyType,
        createdAt,
      });
      continue;
    }
    byName.set(o.productName, {
      ...prev,
      quantity: prev.quantity + o.quantity,
      total: prev.total + o.totalPrice,
      createdAt: createdAt > prev.createdAt ? createdAt : prev.createdAt,
    });
  }
  return Array.from(byName.values());
}

export default function InventoryPage() {
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setError('');
      try {
        const res = await client.get<PagedResponse<OrderResponse>>(ENDPOINTS.myOrders, {
          params: { page: 0, size: 200 },
        });
        const paid = res.data.content.filter((o) => o.status === 'PAID');
        if (active) setItems(aggregate(paid));
      } catch (err) {
        if (active) setError(extractError(err));
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const sort = useTableSort(items, 'createdAt', 'desc');
  const totalUnits = useMemo(() => items.reduce((s, i) => s + i.quantity, 0), [items]);

  return (
    <div className="page" style={{ maxWidth: 1000 }}>
      <h1 className="page-title">Мой инвентарь</h1>
      <p className="subtext">Вещи, которыми вы владеете — из оплаченных покупок.</p>

      {error && <ErrorBlock message={error} />}
      {loading ? (
        <LoadingBlock />
      ) : items.length === 0 ? (
        <p className="empty-state">У вас пока нет вещей в инвентаре</p>
      ) : (
        <>
          <div className="table-meta" style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}>
            <span>Предметов: {items.length}</span>
            <span>Всего единиц: {totalUnits}</span>
            <SortTh k="createdAt" label="по дате" sort={sort} />
            <SortTh k="productName" label="по названию" sort={sort} />
            <SortTh k="quantity" label="по количеству" sort={sort} />
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
              gap: 16,
              marginTop: 16,
            }}
          >
            {sort.sorted.map((item) => (
              <div key={item.productName} className="transaction-card" style={{ borderRadius: 10 }}>
                <div className="mono" style={{ fontSize: 28, lineHeight: 1 }}>📦</div>
                <div style={{ fontWeight: 700, marginTop: 8 }}>{item.productName}</div>
                <div className="subtext" style={{ marginTop: 4 }}>× {item.quantity}</div>
                <div style={{ marginTop: 8 }}>
                  {formatMoney(item.total)} {getCurrencyLabel(item.currencyType)}
                </div>
                <div className="subtext" style={{ marginTop: 4 }}>
                  последняя покупка: {formatDate(item.createdAt)}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
