import { useEffect, useState, FormEvent } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { PagedResponse, ProductResponse } from '../../../shared/types';
import { CURRENCY_TYPES, PRODUCT_STATUSES } from '../../../shared/types';
import { extractError, formatMoney, getCurrencyLabel, PRODUCT_STATUS_LABELS } from '../../../shared/utils';
import { PageSection, FormField, ErrorBlock, SuccessMessage, LoadingBlock } from '../../../shared/ui';
import ImageUpload from '../../../shared/ui/ImageUpload';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';

const EMPTY_FORM = {
  name: '',
  description: '',
  price: '',
  currencyType: 'CASHLESS',
  stockQuantity: '',
  category: '',
  imageUrl: '',
};

const SIZE = 20;

function ProductBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{PRODUCT_STATUS_LABELS[status] || status}</span>;
}

export default function ProductsAdminPage() {
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [form, setForm] = useState(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');
  const [createSuccess, setCreateSuccess] = useState('');

  const setF = (k: keyof typeof EMPTY_FORM) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  const fetchProducts = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, unknown> = { page: p, size: SIZE };
      if (statusFilter) params.status = statusFilter;
      const res = await client.get<PagedResponse<ProductResponse>>(ENDPOINTS.products, { params });
      setProducts(res.data.content);
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
    fetchProducts(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError('');
    setCreateSuccess('');
    try {
      await client.post(ENDPOINTS.products, {
        name: form.name.trim(),
        description: form.description || undefined,
        price: Number(form.price),
        currencyType: form.currencyType,
        stockQuantity: form.stockQuantity === '' ? null : Number(form.stockQuantity),
        category: form.category || undefined,
        imageUrl: form.imageUrl || undefined,
      });
      setCreateSuccess(`Товар «${form.name.trim()}» создан`);
      setForm(EMPTY_FORM);
      fetchProducts(0);
    } catch (err) {
      setCreateError(extractError(err));
    } finally {
      setCreating(false);
    }
  };

  const changeStatus = async (p: ProductResponse, status: string) => {
    try {
      await client.post(ENDPOINTS.productStatus(p.id), { status });
      fetchProducts();
    } catch (err) {
      setError(extractError(err));
    }
  };

  const adjustStock = async (p: ProductResponse, delta: number) => {
    try {
      await client.post(ENDPOINTS.productStock(p.id), { delta });
      fetchProducts();
    } catch (err) {
      setError(extractError(err));
    }
  };

  const sort = useTableSort(products, 'id', 'desc');

  return (
    <div className="page" style={{ maxWidth: 1200 }}>
      <h1 className="page-title">Товары</h1>
      <p className="subtext">Управление каталогом маркетплейса: создание, статусы, остатки.</p>

      <PageSection title="Создать товар">
        <form onSubmit={handleCreate} className="form-grid">
          <FormField label="Название" id="p-name" required>
            <input id="p-name" type="text" value={form.name} onChange={(e) => setF('name')(e.target.value)} required />
          </FormField>
          <FormField label="Цена" id="p-price" required>
            <input id="p-price" type="number" min="0.01" step="0.01" value={form.price} onChange={(e) => setF('price')(e.target.value)} required />
          </FormField>
          <FormField label="Валюта" id="p-cur">
            <select id="p-cur" value={form.currencyType} onChange={(e) => setF('currencyType')(e.target.value)}>
              {CURRENCY_TYPES.map((c) => <option key={c} value={c}>{getCurrencyLabel(c)}</option>)}
            </select>
          </FormField>
          <FormField label="Остаток (пусто = безлимит)" id="p-stock">
            <input id="p-stock" type="number" min="0" value={form.stockQuantity} onChange={(e) => setF('stockQuantity')(e.target.value)} />
          </FormField>
          <FormField label="Категория" id="p-cat">
            <input id="p-cat" type="text" value={form.category} onChange={(e) => setF('category')(e.target.value)} />
          </FormField>
          <div className="full">
            <FormField label="Описание" id="p-desc">
              <input id="p-desc" type="text" value={form.description} onChange={(e) => setF('description')(e.target.value)} />
            </FormField>
          </div>
          <div className="full">
            <ImageUpload label="Изображение товара" value={form.imageUrl} onChange={setF('imageUrl')} />
          </div>
          {createError && <div className="full"><ErrorBlock message={createError} /></div>}
          {createSuccess && <div className="full"><SuccessMessage message={createSuccess} /></div>}
          <div className="full">
            <button type="submit" className="btn btn-primary" disabled={creating || !form.name.trim() || !form.price}>
              {creating ? 'Создаю...' : 'Создать товар'}
            </button>
          </div>
        </form>
      </PageSection>

      <div className="filters-bar">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Все статусы</option>
          {PRODUCT_STATUSES.map((s) => <option key={s} value={s}>{PRODUCT_STATUS_LABELS[s]}</option>)}
        </select>
        <button className="btn btn-ghost" onClick={() => fetchProducts(page)}>Обновить</button>
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
                  <SortTh k="sku" label="SKU" sort={sort} />
                  <SortTh k="name" label="Название" sort={sort} />
                  <SortTh k="price" label="Цена" sort={sort} />
                  <SortTh k="stockQuantity" label="Остаток" sort={sort} />
                  <SortTh k="status" label="Статус" sort={sort} />
                  <th>Действия</th>
                </tr>
              </thead>
              <tbody>
                {sort.sorted.map((p) => (
                  <tr key={p.id}>
                    <td className="mono">{p.id}</td>
                    <td className="mono">{p.sku}</td>
                    <td>{p.name}</td>
                    <td>{formatMoney(p.price)} {getCurrencyLabel(p.currencyType)}</td>
                    <td>{p.stockQuantity == null ? '∞' : p.stockQuantity}</td>
                    <td><ProductBadge status={p.status} /></td>
                    <td className="actions">
                      <select value={p.status} onChange={(e) => changeStatus(p, e.target.value)} style={{ maxWidth: 130 }} title="Сменить статус">
                        {PRODUCT_STATUSES.map((s) => <option key={s} value={s}>{PRODUCT_STATUS_LABELS[s]}</option>)}
                      </select>
                      {p.stockQuantity != null && (
                        <>
                          <button className="btn btn-sm btn-ghost" title="−1 к остатку" onClick={() => adjustStock(p, -1)}>−1</button>
                          <button className="btn btn-sm btn-ghost" title="+10 к остатку" onClick={() => adjustStock(p, 10)}>+10</button>
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {products.length === 0 && <p className="empty-state">Товары не найдены</p>}
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => fetchProducts(page - 1)}>← Назад</button>
            <span>Страница {page + 1} из {totalPages || 1}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => fetchProducts(page + 1)}>Вперёд →</button>
          </div>
        </>
      )}
    </div>
  );
}
