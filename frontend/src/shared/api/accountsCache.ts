import { useEffect, useState } from 'react';
import client from './client';
import { ENDPOINTS } from './config';
import type { AccountResponse, PagedResponse } from '../types';

// ── Front-end accounts cache ───────────────────────────────────────────────
// Loaded once at app start (for operators) and refreshed on demand / on focus,
// so the player pickers filter INSTANTLY on every keystroke (no per-key API call).

let cache: AccountResponse[] = [];
let loaded = false;
let inflight: Promise<AccountResponse[]> | null = null;
const listeners = new Set<() => void>();

function emit() { listeners.forEach((l) => l()); }

export function getCachedAccounts(): AccountResponse[] { return cache; }
export function isAccountsLoaded(): boolean { return loaded; }

export async function loadAccounts(force = false): Promise<AccountResponse[]> {
  if (!force && loaded) return cache;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const res = await client.get<PagedResponse<AccountResponse>>(ENDPOINTS.accounts, {
        params: { size: 1000, sortBy: 'publicName', direction: 'ASC' },
      });
      cache = res.data.content || [];
      loaded = true;
      emit();
      return cache;
    } catch {
      return cache; // keep whatever we had (e.g. non-operator → 403)
    } finally {
      inflight = null;
    }
  })();
  return inflight;
}

/** Re-fetch in the background (e.g. after an account was created / changed). */
export function invalidateAccounts(): void {
  loaded = false;
  loadAccounts(true);
}

export function searchCachedAccounts(query: string, limit = 8): AccountResponse[] {
  const q = query.trim().toLowerCase();
  if (!q) return cache.slice(0, limit);
  return cache
    .filter((a) =>
      a.publicName?.toLowerCase().includes(q) ||
      a.characterName?.toLowerCase().includes(q) ||
      String(a.id) === q)
    .slice(0, limit);
}

/** Subscribe a component to cache changes; warms the cache on first mount. */
export function useAccountsCache() {
  const [, setVersion] = useState(0);
  useEffect(() => {
    loadAccounts();
    const fn = () => setVersion((v) => v + 1);
    listeners.add(fn);
    return () => { listeners.delete(fn); };
  }, []);
  return { ready: loaded, refresh: () => loadAccounts(true) };
}
