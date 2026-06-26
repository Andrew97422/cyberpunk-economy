import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { OrderResponse, PagedResponse } from '../../../shared/types';
import { extractError, formatDate, formatMoney, getCurrencyLabel, ORDER_STATUS_LABELS } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';
import Receipt from '../../banking/components/Receipt';

const SIZE = 20;

function OrderBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{ORDER_STATUS_LABELS[status] || status}</span>;
}

export default function MyOrdersPage() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState<OrderResponse | null>(null);

  const fetchOrders = async (p = 0) => {
    setLoading(true);
    setError('');
    try {
      const res = await client.get<PagedResponse<OrderResponse>>(ENDPOINTS.myOrders, {
        params: { page: p, size: SIZE },
      });
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
  }, []);

  const sort = useTableSort(orders, 'createdAt', 'desc');

  return (
    <div className="page" style={{ maxWidth: 1000 }}>
      <h1 className="page-title">Мои покупки</h1>
      <p className="subtext">История ваших заказов и купленные товары.</p>

      {error && <ErrorBlock message={error} />}
      {loading ? (
        <LoadingBlock />
      ) : (
        <>
          <div className="table-meta">Всего: {total}</div>
          <div className="page-section" style={{ padding: 0, overflow: 'hidden' }}>
            <table className="table">
              <thead>
                <tr>
                  <SortTh k="id" label="ID" sort={sort} />
                  <SortTh k="productName" label="Товар" sort={sort} />
                  <SortTh k="quantity" label="Кол-во" sort={sort} />
                  <SortTh k="totalPrice" label="Сумма" sort={sort} />
                  <SortTh k="status" label="Статус" sort={sort} />
                  <SortTh k="createdAt" label="Дата" sort={sort} />
                  <th>Чек</th>
                </tr>
              </thead>
              <tbody>
                {sort.sorted.map((o) => (
                  <tr key={o.id}>
                    <td className="mono">{o.id}</td>
                    <td>{o.productName}</td>
                    <td>{o.quantity}</td>
                    <td>{formatMoney(o.totalPrice)} {getCurrencyLabel(o.currencyType)}</td>
                    <td><OrderBadge status={o.status} /></td>
                    <td>{formatDate(o.createdAt)}</td>
                    <td>
                      <button className="btn btn-sm btn-ghost" onClick={() => setReceipt(o)}>Чек</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {orders.length === 0 && <p className="empty-state">У вас пока нет покупок</p>}
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => fetchOrders(page - 1)}>← Назад</button>
            <span>Страница {page + 1} из {totalPages || 1}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => fetchOrders(page + 1)}>Вперёд →</button>
          </div>
        </>
      )}

      {receipt && <Receipt kind="order" data={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
