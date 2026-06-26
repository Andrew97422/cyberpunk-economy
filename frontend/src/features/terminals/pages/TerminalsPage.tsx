import { useEffect, useState, FormEvent } from 'react';
import { useAuth } from '../../../shared/auth/AuthContext';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { TerminalResponse, PagedResponse } from '../../../shared/types';
import { TERMINAL_STATUSES } from '../../../shared/types';
import { extractError, formatDate, isAdmin, TERMINAL_STATUS_LABELS } from '../../../shared/utils';
import { PageSection, FormField, ErrorBlock, SuccessMessage, LoadingBlock } from '../../../shared/ui';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';

const EMPTY_FORM = { name: '', terminalType: '', location: '', ipAddress: '', notes: '' };
const SIZE = 20;

// Human-readable terminal kinds (the value stored is the label itself).
const TERMINAL_TYPES = ['Банкомат', 'Касса (POS)', 'Турникет / Ворота', 'Платёжный киоск', 'Сканер карт', 'Другое'];

function TerminalBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{TERMINAL_STATUS_LABELS[status] || status}</span>;
}

export default function TerminalsPage() {
  const { user } = useAuth();
  const admin = isAdmin(user?.role);

  const [statusFilter, setStatusFilter] = useState('');
  const [terminals, setTerminals] = useState<TerminalResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // register form
  const [form, setForm] = useState(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');
  const [createSuccess, setCreateSuccess] = useState('');

  // edit panel
  const [selected, setSelected] = useState<TerminalResponse | null>(null);
  const [edit, setEdit] = useState({ terminalType: '', location: '', ipAddress: '', notes: '' });
  const [saving, setSaving] = useState(false);
  const [editError, setEditError] = useState('');

  const setF = (k: keyof typeof EMPTY_FORM) => (v: string) => setForm((f) => ({ ...f, [k]: v }));
  const setE = (k: keyof typeof edit) => (v: string) => setEdit((e) => ({ ...e, [k]: v }));

  const sort = useTableSort(terminals, 'updatedAt', 'desc');

  const fetchTerminals = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, unknown> = { page: p, size: SIZE };
      if (statusFilter) params.status = statusFilter;
      const res = await client.get<PagedResponse<TerminalResponse>>(ENDPOINTS.terminals, { params });
      setTerminals(res.data.content);
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
    fetchTerminals(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  const handleRegister = async (e: FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError('');
    setCreateSuccess('');
    try {
      await client.post<TerminalResponse>(ENDPOINTS.terminals, {
        name: form.name.trim(),
        terminalType: form.terminalType || undefined,
        location: form.location || undefined,
        ipAddress: form.ipAddress || undefined,
        notes: form.notes || undefined,
      });
      setCreateSuccess(`Терминал «${form.name.trim()}» зарегистрирован`);
      setForm(EMPTY_FORM);
      fetchTerminals(0);
    } catch (err) {
      setCreateError(extractError(err));
    } finally {
      setCreating(false);
    }
  };

  const changeStatus = async (t: TerminalResponse, status: string) => {
    try {
      await client.post(ENDPOINTS.terminalStatus(t.id), { status });
      fetchTerminals();
      if (selected?.id === t.id) setSelected({ ...selected, status });
    } catch (err) {
      setError(extractError(err));
    }
  };

  const openEdit = (t: TerminalResponse) => {
    setSelected(t);
    setEdit({
      terminalType: t.terminalType || '',
      location: t.location || '',
      ipAddress: t.ipAddress || '',
      notes: t.notes || '',
    });
    setEditError('');
  };

  const saveEdit = async (e: FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    setSaving(true);
    setEditError('');
    try {
      await client.patch(ENDPOINTS.terminalById(selected.id), {
        terminalType: edit.terminalType,
        location: edit.location,
        ipAddress: edit.ipAddress,
        notes: edit.notes,
      });
      setSelected(null);
      fetchTerminals();
    } catch (err) {
      setEditError(extractError(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="page" style={{ maxWidth: 1200 }}>
      <h1 className="page-title">Терминалы</h1>
      <p className="subtext">Физические точки (банкоматы, POS, ворота зон): регистрация, статусы и параметры.</p>

      {admin && (
        <PageSection title="Зарегистрировать терминал">
          <form onSubmit={handleRegister} className="form-grid">
            <FormField label="Название" id="t-name" required>
              <input id="t-name" type="text" value={form.name} onChange={(e) => setF('name')(e.target.value)} required />
            </FormField>
            <FormField label="Тип" id="t-type">
              <select id="t-type" value={form.terminalType} onChange={(e) => setF('terminalType')(e.target.value)}>
                <option value="">— выберите тип —</option>
                {TERMINAL_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </FormField>
            <FormField label="Локация" id="t-loc">
              <input id="t-loc" type="text" placeholder="например, зона А, вход" value={form.location} onChange={(e) => setF('location')(e.target.value)} />
            </FormField>
            <FormField label="IP-адрес (необязательно)" id="t-ip">
              <input id="t-ip" type="text" placeholder="необязательно" value={form.ipAddress} onChange={(e) => setF('ipAddress')(e.target.value)} />
            </FormField>
            <div className="full">
              <FormField label="Заметка" id="t-notes">
                <input id="t-notes" type="text" value={form.notes} onChange={(e) => setF('notes')(e.target.value)} />
              </FormField>
            </div>
            {createError && <div className="full"><ErrorBlock message={createError} /></div>}
            {createSuccess && <div className="full"><SuccessMessage message={createSuccess} /></div>}
            <div className="full">
              <button type="submit" className="btn btn-primary" disabled={creating || !form.name.trim()}>
                {creating ? 'Регистрирую...' : 'Зарегистрировать'}
              </button>
            </div>
          </form>
        </PageSection>
      )}

      <div className="filters-bar">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Все статусы</option>
          {TERMINAL_STATUSES.map((s) => <option key={s} value={s}>{TERMINAL_STATUS_LABELS[s]}</option>)}
        </select>
        <button className="btn btn-ghost" onClick={() => fetchTerminals(page)}>Обновить</button>
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
                  <SortTh k="name" label="Название" sort={sort} />
                  <SortTh k="terminalType" label="Тип" sort={sort} />
                  <SortTh k="location" label="Локация" sort={sort} />
                  <SortTh k="ipAddress" label="IP" sort={sort} />
                  <SortTh k="status" label="Статус" sort={sort} />
                  <SortTh k="updatedAt" label="Обновлён" sort={sort} />
                  <th>Действия</th>
                </tr>
              </thead>
              <tbody>
                {sort.sorted.map((t) => (
                  <tr key={t.id}>
                    <td className="mono">{t.id}</td>
                    <td>{t.name}</td>
                    <td>{t.terminalType || '—'}</td>
                    <td>{t.location || '—'}</td>
                    <td className="mono">{t.ipAddress || '—'}</td>
                    <td><TerminalBadge status={t.status} /></td>
                    <td>{formatDate(t.updatedAt)}</td>
                    <td className="actions">
                      {admin ? (
                        <>
                          <select
                            value={t.status}
                            onChange={(e) => changeStatus(t, e.target.value)}
                            style={{ maxWidth: 150 }}
                            title="Сменить статус"
                          >
                            {TERMINAL_STATUSES.map((s) => <option key={s} value={s}>{TERMINAL_STATUS_LABELS[s]}</option>)}
                          </select>
                          <button className="btn btn-sm btn-ghost" onClick={() => openEdit(t)}>Изменить</button>
                        </>
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {terminals.length === 0 && <p className="empty-state">Терминалы не найдены</p>}
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => fetchTerminals(page - 1)}>← Назад</button>
            <span>Страница {page + 1} из {totalPages || 1}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => fetchTerminals(page + 1)}>Вперёд →</button>
          </div>
        </>
      )}

      {admin && selected && (
        <PageSection title={`Изменить терминал — ${selected.name}`}>
          <form onSubmit={saveEdit} className="form-grid">
            <FormField label="Тип" id="e-type">
              <select id="e-type" value={edit.terminalType} onChange={(e) => setE('terminalType')(e.target.value)}>
                <option value="">— выберите тип —</option>
                {TERMINAL_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </FormField>
            <FormField label="Локация" id="e-loc">
              <input id="e-loc" type="text" value={edit.location} onChange={(e) => setE('location')(e.target.value)} />
            </FormField>
            <FormField label="IP-адрес" id="e-ip">
              <input id="e-ip" type="text" value={edit.ipAddress} onChange={(e) => setE('ipAddress')(e.target.value)} />
            </FormField>
            <FormField label="Заметка" id="e-notes">
              <input id="e-notes" type="text" value={edit.notes} onChange={(e) => setE('notes')(e.target.value)} />
            </FormField>
            <p className="hint full">Название терминала изменить нельзя.</p>
            {editError && <div className="full"><ErrorBlock message={editError} /></div>}
            <div className="full btn-group">
              <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Сохраняю...' : 'Сохранить'}</button>
              <button type="button" className="btn btn-ghost" onClick={() => setSelected(null)}>Отмена</button>
            </div>
          </form>
        </PageSection>
      )}
    </div>
  );
}
