import { useEffect, useRef, useState } from 'react';
import type { AccountResponse } from '../types';
import { ROLE_LABELS } from '../utils';
import { searchCachedAccounts, useAccountsCache } from '../api/accountsCache';

interface Props {
  label?: string;
  placeholder?: string;
  selected?: { id: number; publicName: string } | null;
  onSelect: (account: AccountResponse) => void;
  onClear?: () => void;
}

/** Player picker with INSTANT per-keystroke filtering from the front-end accounts cache. */
export default function AccountPicker({ label, placeholder, selected, onSelect, onClear }: Props) {
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);
  const { refresh } = useAccountsCache(); // warms cache on mount + re-renders on updates

  // close on outside click
  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  const results = searchCachedAccounts(q, 10);

  const pick = (a: AccountResponse) => {
    setQ('');
    setOpen(false);
    onSelect(a);
  };

  return (
    <div className="account-picker" ref={boxRef}>
      {label && <span className="image-upload-label">{label}</span>}
      {selected ? (
        <div className="account-picker-selected">
          <span className="account-picker-chip">{selected.publicName} <span className="text-muted">#{selected.id}</span></span>
          {onClear && <button type="button" className="btn btn-sm btn-ghost" onClick={onClear}>Сменить</button>}
        </div>
      ) : (
        <div className="account-picker-input">
          <input
            type="text"
            value={q}
            placeholder={placeholder || 'Имя игрока…'}
            onFocus={() => { setOpen(true); refresh(); }}
            onChange={(e) => { setQ(e.target.value); setOpen(true); }}
          />
          {open && (
            <div className="account-picker-menu">
              {results.map((a) => (
                <button type="button" key={a.id} className="account-picker-item" onClick={() => pick(a)}>
                  <strong>{a.publicName}</strong>
                  {a.characterName ? <span className="text-muted"> — {a.characterName}</span> : null}
                  <span className="text-muted"> · {ROLE_LABELS[a.role] || a.role} · #{a.id}</span>
                </button>
              ))}
              {results.length === 0 && <div className="account-picker-item text-muted">Ничего не найдено</div>}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
