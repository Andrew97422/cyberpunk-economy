import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { OrderResponse, PagedResponse } from '../../../shared/types';
import { ORDER_STATUSES } from '../../../shared/types';
import { extractError, formatDate, formatMoney, getCurrencyLabel, ORDER_STATUS_LABELS } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock, ConfirmDialog } from '../../../shared/ui';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';

const SIZE = 20;

function OrderBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{ORDER_STATUS_LABELS[status] || status}</span>;
}

export default function OrdersAdminPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [confirm, setConfirm] = useState<OrderResponse | null>(null);

  const fetchOrders = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, unknown> = { page: p, size: SIZE };
      if (statusFilter) params.status = statusFilter;
      const res = await client.get<PagedResponse<OrderResponse>>(ENDPOINTS.marketOrders, { params });
      setOrders(res.data.content);
      setTotal(res.data.totalElements);
      setTotalPages(res.data.totalPages);
      setPage(p);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  const doCancel = async () => {
    if (!confirm) return;
    const order = confirm;
    setConfirm(null);
    try {
      await client.post(ENDPOINTS.cancelOrder(order.id));
      fetchOrders();
    } catch (err) {
      setError(extractError(err));
    }
  };

  const sort = useTableSort(orders, 'createdAt', 'desc');

  return (
    <div className="page" style={{ maxWidth: 1200 }}>
      <h1 className="page-title">Заказы</h1>
      <p className="subtext">Все заказы маркетплейса. Отмена оплаченного заказа автоматически возвращает деньги покупателю.</p>

      <div className="filters-bar">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Все статусы</option>
          {ORDER_STATUSES.map((s) => <option key={s} value={s}>{ORDER_STATUS_LABELS[s]}</option>)}
        </select>
        <button className="btn btn-ghost" onClick={() => fetchOrders(page)}>Обновить</button>
      </div>

      {error && <ErrorBlock message={error} />}
      {loading ? (
        <LoadingBlock />
      ) : (
        <>
          <div className="table-meta">Найдено: {total}</div>
          <div className="page-section" style={{ padding: 0, overflow: 'hidden' }}>
            <table className="table">
              <thead>
                <tr>
                  <SortTh k="id" label="ID" sort={sort} />
                  <SortTh k="productName" label="Товар" sort={sort} />
                  <SortTh k="buyerPublicName" label="Покупатель" sort={sort} />
                  <SortTh k="quantity" label="Кол-во" sort={sort} />
                  <SortTh k="totalPrice" label="Сумма" sort={sort} />
                  <SortTh k="status" label="Статус" sort={sort} />
                  <SortTh k="createdAt" label="Дата" sort={sort} />
                  <th>Действия</th>
                </tr>
              </thead>
              <tbody>
                {sort.sorted.map((o) => (
                  <tr key={o.id}>
                    <td className="mono">{o.id}</td>
                    <td>{o.productName}</td>
                    <td>{o.buyerPublicName || `#${o.buyerAccountId}`}</td>
                    <td>{o.quantity}</td>
                    <td>{formatMoney(o.totalPrice)} {getCurrencyLabel(o.currencyType)}</td>
                    <td><OrderBadge status={o.status} /></td>
                    <td>{formatDate(o.createdAt)}</td>
                    <td className="actions">
                      {o.status === 'CANCELLED' ? (
                        <span className="text-muted">—</span>
                      ) : (
                        <button className="btn btn-sm btn-danger" onClick={() => setConfirm(o)}>Отменить</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {orders.length === 0 && <p className="empty-state">Заказы не найдены</p>}
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => fetchOrders(page - 1)}>← Назад</button>
            <span>Страница {page + 1} из {totalPages || 1}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => fetchOrders(page + 1)}>Вперёд →</button>
          </div>
        </>
      )}

      {confirm && (
        <ConfirmDialog
          message={
            confirm.status === 'PAID'
              ? `Отменить заказ #${confirm.id} и вернуть ${formatMoney(confirm.totalPrice)} ${getCurrencyLabel(confirm.currencyType)} покупателю?`
              : `Отменить заказ #${confirm.id}?`
          }
          onConfirm={doCancel}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  );
}
