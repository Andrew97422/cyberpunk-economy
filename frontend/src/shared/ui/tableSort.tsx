import { CSSProperties, useMemo, useState } from 'react';

export type SortDir = 'asc' | 'desc' | null;

function valOf(row: unknown, key: string): unknown {
  return key.split('.').reduce<unknown>((o, k) => (o == null ? undefined : (o as Record<string, unknown>)[k]), row);
}

function cmp(a: unknown, b: unknown): number {
  if (a == null && b == null) return 0;
  if (a == null) return -1;
  if (b == null) return 1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  if (typeof a === 'boolean' && typeof b === 'boolean') return (a ? 1 : 0) - (b ? 1 : 0);
  const as = String(a);
  const bs = String(b);
  const looksDate = /^\d{4}-\d{2}-\d{2}/.test(as) && /^\d{4}-\d{2}-\d{2}/.test(bs);
  if (looksDate) {
    const ad = Date.parse(as), bd = Date.parse(bs);
    if (!Number.isNaN(ad) && !Number.isNaN(bd)) return ad - bd;
  }
  const an = Number(as), bn = Number(bs);
  if (as.trim() !== '' && bs.trim() !== '' && !Number.isNaN(an) && !Number.isNaN(bn)) return an - bn;
  return as.localeCompare(bs, 'ru');
}

/**
 * Client-side column sort for a loaded page of rows. Three-state per column:
 * desc → asc → none. Defaults to `initialKey` desc (typically a date/time column).
 */
export function useTableSort<T>(rows: T[], initialKey?: string, initialDir: SortDir = 'desc') {
  const [sortKey, setSortKey] = useState<string | undefined>(initialKey);
  const [sortDir, setSortDir] = useState<SortDir>(initialKey ? initialDir : null);

  const onSort = (k: string) => {
    if (sortKey !== k) { setSortKey(k); setSortDir('desc'); return; }
    if (sortDir === 'desc') setSortDir('asc');
    else if (sortDir === 'asc') { setSortKey(undefined); setSortDir(null); }
    else setSortDir('desc');
  };

  const sorted = useMemo(() => {
    if (!sortKey || !sortDir) return rows;
    const copy = [...rows];
    copy.sort((a, b) => cmp(valOf(a, sortKey), valOf(b, sortKey)) * (sortDir === 'asc' ? 1 : -1));
    return copy;
  }, [rows, sortKey, sortDir]);

  return { sorted, sortKey, sortDir, onSort };
}

export interface SortState {
  sortKey?: string;
  sortDir: SortDir;
  onSort: (k: string) => void;
}

/** Clickable sortable <th>. Pass the column `k`, a `label`, and the hook result as `sort`. */
export function SortTh({ k, label, sort, style }: { k: string; label: string; sort: SortState; style?: CSSProperties }) {
  const active = sort.sortKey === k;
  const arrow = !active ? '⇅' : sort.sortDir === 'asc' ? '▲' : '▼';
  return (
    <th className="th-sort" style={style} onClick={() => sort.onSort(k)} title="Сортировать">
      {label} <span className="th-sort-arrow" data-active={active}>{arrow}</span>
    </th>
  );
}
