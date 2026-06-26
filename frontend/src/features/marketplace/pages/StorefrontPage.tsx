import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { OrderResponse, PagedResponse, ProductResponse } from '../../../shared/types';
import { extractError, formatMoney, getCurrencyLabel } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock, SuccessMessage } from '../../../shared/ui';

const SIZE = 24;

export default function StorefrontPage() {
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [qty, setQty] = useState<Record<number, number>>({});
  const [buyingId, setBuyingId] = useState<number | null>(null);
  const [message, setMessage] = useState('');
  const [buyError, setBuyError] = useState('');

  const fetchProducts = async () => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, unknown> = { status: 'ACTIVE', page: 0, size: SIZE };
      if (search.trim()) params.search = search.trim();
      const res = await client.get<PagedResponse<ProductResponse>>(ENDPOINTS.products, { params });
      setProducts(res.data.content);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const buy = async (product: ProductResponse) => {
    setBuyingId(product.id);
    setMessage('');
    setBuyError('');
    try {
      const quantity = qty[product.id] && qty[product.id] > 0 ? qty[product.id] : 1;
      const res = await client.post<OrderResponse>(ENDPOINTS.marketOrders, {
        productId: product.id,
        quantity,
      });
      setMessage(
        `Куплено: ${res.data.productName} ×${res.data.quantity} за ${formatMoney(res.data.totalPrice)} ${getCurrencyLabel(res.data.currencyType)}`,
      );
      fetchProducts();
    } catch (err) {
      setBuyError(extractError(err));
    } finally {
      setBuyingId(null);
    }
  };

  const outOfStock = (p: ProductResponse) => p.stockQuantity != null && p.stockQuantity <= 0;

  return (
    <div className="page" style={{ maxWidth: 1200 }}>
      <h1 className="page-title">Магазин</h1>
      <p className="subtext">Каталог товаров и услуг. Покупка списывается с вашего счёта.</p>

      <div className="filters-bar">
        <input
          type="text"
          placeholder="Поиск по названию или SKU..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && fetchProducts()}
          style={{ minWidth: 280 }}
        />
        <button className="btn btn-ghost" onClick={fetchProducts}>Найти</button>
      </div>

      {message && <SuccessMessage message={message} />}
      {buyError && <ErrorBlock message={buyError} />}
      {error && <ErrorBlock message={error} />}

      {loading ? (
        <LoadingBlock />
      ) : products.length === 0 ? (
        <p className="empty-state">Товары не найдены</p>
      ) : (
        <div className="card-grid">
          {products.map((p) => (
            <div key={p.id} className="dashboard-card" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {p.imageUrl && (
                <img src={p.imageUrl} alt={p.name} style={{ width: '100%', height: 140, objectFit: 'cover', borderRadius: 8 }} />
              )}
              <div style={{ fontWeight: 600 }}>{p.name}</div>
              {p.category && <div className="text-muted" style={{ fontSize: 13 }}>{p.category}</div>}
              {p.description && <div style={{ fontSize: 13 }}>{p.description}</div>}
              <div style={{ fontSize: 18, fontWeight: 700 }}>
                {formatMoney(p.price)} <span style={{ fontSize: 13, fontWeight: 400 }}>{getCurrencyLabel(p.currencyType)}</span>
              </div>
              <div className="text-muted" style={{ fontSize: 12 }}>
                {p.stockQuantity == null ? 'В наличии' : `Остаток: ${p.stockQuantity}`}
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 'auto' }}>
                <input
                  type="number"
                  min={1}
                  value={qty[p.id] ?? 1}
                  onChange={(e) => setQty((q) => ({ ...q, [p.id]: Math.max(1, Number(e.target.value) || 1) }))}
                  style={{ width: 64 }}
                  disabled={outOfStock(p)}
                />
                <button
                  className="btn btn-primary"
                  style={{ flex: 1 }}
                  disabled={buyingId === p.id || outOfStock(p)}
                  onClick={() => buy(p)}
                >
                  {outOfStock(p) ? 'Нет в наличии' : buyingId === p.id ? 'Покупаю...' : 'Купить'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
