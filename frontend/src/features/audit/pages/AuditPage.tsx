import { Fragment, useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { AuditLogResponse, PagedResponse } from '../../../shared/types';
import {
  extractError, formatDate, ROLE_LABELS,
  AUDIT_SOURCE_LABELS, AUDIT_SOURCES, AUDIT_EVENT_TYPE_LABELS, AUDIT_EVENT_TYPE_GROUPS,
} from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import AccountPicker from '../../../shared/ui/AccountPicker';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';

const EMPTY_FILTERS = {
  search: '',
  eventType: '',
  eventSource: '',
  actorAccountId: '',
  from: '',
  to: '',
};

const SIZE = 25;

function toIso(local: string): string | undefined {
  if (!local) return undefined;
  const d = new Date(local);
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

export default function AuditPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [logs, setLogs] = useState<AuditLogResponse[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [actorSel, setActorSel] = useState<{ id: number; publicName: string } | null>(null);

  const set = (key: keyof typeof EMPTY_FILTERS) => (v: string) => setFilters((f) => ({ ...f, [key]: v }));
  const toggle = (id: number) => setExpandedId((prev) => (prev === id ? null : id));

  const sort = useTableSort(logs, 'occurredAt', 'desc');

  const fetchLogs = async (p = page) => {
    setLoading(true);
    setError('');
    setExpandedId(null);
    try {
      const params: Record<string, unknown> = { page: p, size: SIZE };
      if (filters.search) params.search = filters.search;
      if (filters.eventType) params.eventType = filters.eventType;
      if (filters.eventSource) params.eventSource = filters.eventSource;
      if (filters.actorAccountId) params.actorAccountId = filters.actorAccountId;
      const from = toIso(filters.from);
      const to = toIso(filters.to);
      if (from) params.from = from;
      if (to) params.to = to;

      const res = await client.get<PagedResponse<AuditLogResponse>>(ENDPOINTS.auditLogs, { params });
      setLogs(res.data.content);
      setTotal(res.data.totalElements);
      setTotalPages(res.data.totalPages);
      setPage(p);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  // Auto-apply: any filter change refetches (debounced so typing in search is smooth).
  useEffect(() => {
    const t = setTimeout(() => fetchLogs(0), 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.search, filters.eventType, filters.eventSource, filters.actorAccountId, filters.from, filters.to]);

  const reset = () => {
    setFilters(EMPTY_FILTERS);
    setActorSel(null);
    setExpandedId(null);
    setTimeout(() => fetchLogs(0), 0);
  };

  return (
    <div className="page" style={{ maxWidth: 1280 }}>
      <h1 className="page-title">Аудит-лог</h1>
      <p className="subtext">Журнал всех событий системы: входы, операции с аккаунтами, банк, карты, терминалы. Нажмите на строку, чтобы развернуть.</p>

      <div className="filters-bar">
        <input className="filter-input" placeholder="Поиск (сообщение / актор / тип)..." value={filters.search}
          onChange={(e) => set('search')(e.target.value)} />
        <select value={filters.eventType} onChange={(e) => set('eventType')(e.target.value)} style={{ maxWidth: 240 }}>
          <option value="">Все типы событий</option>
          {AUDIT_EVENT_TYPE_GROUPS.map((g) => (
            <optgroup key={g.group} label={g.group}>
              {g.types.map((t) => <option key={t} value={t}>{AUDIT_EVENT_TYPE_LABELS[t] || t}</option>)}
            </optgroup>
          ))}
        </select>
        <select value={filters.eventSource} onChange={(e) => set('eventSource')(e.target.value)} style={{ maxWidth: 200 }}>
          <option value="">Все источники</option>
          {AUDIT_SOURCES.map((s) => <option key={s} value={s}>{AUDIT_SOURCE_LABELS[s] || s}</option>)}
        </select>
        <AccountPicker
          placeholder="Актор (игрок)…"
          selected={actorSel}
          onSelect={(a) => { setActorSel({ id: a.id, publicName: a.publicName }); set('actorAccountId')(String(a.id)); }}
          onClear={() => { setActorSel(null); set('actorAccountId')(''); }}
        />
        <button className="btn btn-ghost" onClick={reset}>Сброс</button>
      </div>
      <div className="filters-bar" style={{ marginTop: -8 }}>
        <label className="field" style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <span>С</span><input type="datetime-local" value={filters.from} onChange={(e) => set('from')(e.target.value)} />
        </label>
        <label className="field" style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <span>По</span><input type="datetime-local" value={filters.to} onChange={(e) => set('to')(e.target.value)} />
        </label>
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
                  <th style={{ width: 28 }}></th>
                  <SortTh k="occurredAt" label="Время" sort={sort} />
                  <SortTh k="eventType" label="Событие" sort={sort} />
                  <SortTh k="eventSource" label="Источник" sort={sort} />
                  <SortTh k="actorPublicName" label="Актор" sort={sort} />
                  <SortTh k="aggregateType" label="Объект" sort={sort} />
                  <SortTh k="message" label="Сообщение" sort={sort} />
                </tr>
              </thead>
              <tbody>
                {sort.sorted.map((l) => {
                  const open = expandedId === l.id;
                  return (
                    <Fragment key={l.id}>
                      <tr onClick={() => toggle(l.id)} style={{ cursor: 'pointer' }} className={open ? 'row-open' : undefined}>
                        <td className="mono" style={{ color: 'var(--accent)' }}>{open ? '▾' : '▸'}</td>
                        <td style={{ whiteSpace: 'nowrap' }}>{formatDate(l.occurredAt)}</td>
                        <td>{l.eventType ? (AUDIT_EVENT_TYPE_LABELS[l.eventType] || l.eventType) : '—'}</td>
                        <td>{l.eventSource ? (AUDIT_SOURCE_LABELS[l.eventSource] || l.eventSource) : '—'}</td>
                        <td>{l.actorPublicName ? `${l.actorPublicName}${l.actorRole ? ` (${ROLE_LABELS[l.actorRole] || l.actorRole})` : ''}` : '—'}</td>
                        <td>{l.aggregateType ? `${l.aggregateType}${l.aggregateId ? ` #${l.aggregateId}` : ''}` : '—'}</td>
                        <td>{l.message || '—'}</td>
                      </tr>
                      {open && (
                        <tr className="audit-detail-row">
                          <td colSpan={7} style={{ background: 'var(--surface-muted)', padding: '14px 18px' }}>
                            <dl className="detail-list">
                              <dt>Тип</dt>          <dd className="mono">{l.eventType || '—'}</dd>
                              <dt>Источник</dt>     <dd>{l.eventSource || '—'}</dd>
                              <dt>Время</dt>        <dd>{formatDate(l.occurredAt)}</dd>
                              <dt>Записано</dt>     <dd>{formatDate(l.createdAt)}</dd>
                              <dt>Event ID</dt>     <dd className="mono">{l.eventId || '—'}</dd>
                              <dt>Актор</dt>        <dd>{l.actorPublicName || '—'}{l.actorAccountId ? ` (ID ${l.actorAccountId})` : ''}</dd>
                              <dt>Роль актора</dt>  <dd>{l.actorRole ? ROLE_LABELS[l.actorRole] || l.actorRole : '—'}</dd>
                              <dt>Агрегат</dt>      <dd>{l.aggregateType || '—'}{l.aggregateId ? ` #${l.aggregateId}` : ''}</dd>
                              <dt>Объект</dt>       <dd>{l.targetEntityType ? `${l.targetEntityType} #${l.targetEntityId ?? ''}` : '—'}</dd>
                              <dt>Терминал</dt>     <dd>{l.terminalName || (l.terminalId ? `#${l.terminalId}` : '—')}</dd>
                              <dt>Сообщение</dt>    <dd>{l.message || '—'}</dd>
                              {l.payloadJson && (<><dt>Payload</dt><dd><pre style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{l.payloadJson}</pre></dd></>)}
                            </dl>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
          {logs.length === 0 && <p className="empty-state">События не найдены</p>}
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => fetchLogs(page - 1)}>← Назад</button>
            <span>Страница {page + 1} из {totalPages || 1}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => fetchLogs(page + 1)}>Вперёд →</button>
          </div>
        </>
      )}
    </div>
  );
}
