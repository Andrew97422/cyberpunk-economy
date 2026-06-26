import { useRef, useState, FormEvent } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { AccountResponse, CardResponse, CardLookupResponse, PagedResponse } from '../../../shared/types';
import { extractError, formatDate, ROLE_LABELS, CARD_STATUS_LABELS } from '../../../shared/utils';
import { PageSection, FormField, ErrorBlock, SuccessMessage, LoadingBlock } from '../../../shared/ui';
import AccountPicker from '../../../shared/ui/AccountPicker';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';
import { useRfidScan } from '../../../shared/ui/RfidScanField';

interface Selected {
  id: number;
  publicName: string;
}

function CardStatusBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{CARD_STATUS_LABELS[status] || status}</span>;
}

export default function CardsPage() {
  // --- lookup by UID ---
  const [lookupUid, setLookupUid] = useState('');
  const [lookup, setLookup] = useState<CardLookupResponse | null>(null);
  const [lookupError, setLookupError] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);

  // --- selected account ---
  const [selected, setSelected] = useState<Selected | null>(null);

  // --- cards list ---
  const [cards, setCards] = useState<CardResponse[]>([]);
  const [cardsLoading, setCardsLoading] = useState(false);
  const [cardsError, setCardsError] = useState('');

  // --- issue form ---
  const [cardUid, setCardUid] = useState('');
  const [notes, setNotes] = useState('');
  const [issuing, setIssuing] = useState(false);
  const [issueError, setIssueError] = useState('');
  const [issueSuccess, setIssueSuccess] = useState('');

  // --- RFID reader: tap a card to fill UID fields (EM4100 keyboard-emulation) ---
  const lookupInputRef = useRef<HTMLInputElement>(null);
  const lookupScan = useRfidScan((code) => {
    setLookupUid(code);
    runLookup(code);
  }, lookupInputRef);

  const cardUidInputRef = useRef<HTMLInputElement>(null);
  const cardUidScan = useRfidScan((code) => setCardUid(code), cardUidInputRef);

  const runLookup = async (uidOverride?: string) => {
    const uid = (uidOverride ?? lookupUid).trim();
    if (!uid) return;
    setLookupLoading(true);
    setLookupError('');
    setLookup(null);
    try {
      const res = await client.get<CardLookupResponse>(ENDPOINTS.cardLookup, { params: { uid } });
      setLookup(res.data);
    } catch (err) {
      setLookupError(extractError(err));
    } finally {
      setLookupLoading(false);
    }
  };

  const loadCards = async (accountId: number) => {
    setCardsLoading(true);
    setCardsError('');
    try {
      const res = await client.get<CardResponse[] | PagedResponse<CardResponse>>(ENDPOINTS.cardsByAccount(accountId));
      setCards(Array.isArray(res.data) ? res.data : res.data.content || []);
    } catch (err) {
      setCardsError(extractError(err));
    } finally {
      setCardsLoading(false);
    }
  };

  const selectAccount = (a: AccountResponse) => {
    setSelected({ id: a.id, publicName: a.publicName });
    setIssueError('');
    setIssueSuccess('');
    setCardUid('');
    setNotes('');
    loadCards(a.id);
  };

  const handleIssue = async (e: FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    setIssuing(true);
    setIssueError('');
    setIssueSuccess('');
    try {
      await client.post<CardResponse>(ENDPOINTS.cards, {
        publicName: selected.publicName,
        cardUid: cardUid.trim(),
        notes: notes || undefined,
      });
      setIssueSuccess(`Карта ${cardUid.trim()} выпущена`);
      setCardUid('');
      setNotes('');
      loadCards(selected.id);
    } catch (err) {
      setIssueError(extractError(err));
    } finally {
      setIssuing(false);
    }
  };

  const cardsSort = useTableSort(cards, 'issuedAt', 'desc');

  const refresh = () => selected && loadCards(selected.id);

  const block = async (card: CardResponse) => {
    const reason = window.prompt(`Заблокировать карту ${card.cardUid}? Причина (необязательно):`, '');
    if (reason === null) return;
    try {
      await client.post(ENDPOINTS.cardBlock(card.id), { reason: reason || undefined });
      refresh();
    } catch (err) {
      setCardsError(extractError(err));
    }
  };

  const markLost = async (card: CardResponse) => {
    const reason = window.prompt(`Отметить карту ${card.cardUid} как утерянную? Причина (необязательно):`, '');
    if (reason === null) return;
    try {
      await client.post(ENDPOINTS.cardLost(card.id), { reason: reason || undefined });
      refresh();
    } catch (err) {
      setCardsError(extractError(err));
    }
  };

  const replace = async (card: CardResponse) => {
    const newUid = window.prompt(`Заменить карту ${card.cardUid}. UID новой карты:`, '');
    if (newUid === null) return;
    if (!newUid.trim()) {
      setCardsError('UID новой карты обязателен');
      return;
    }
    try {
      await client.post(ENDPOINTS.cardReplace(card.id), { newCardUid: newUid.trim() });
      refresh();
    } catch (err) {
      setCardsError(extractError(err));
    }
  };

  return (
    <div className="page">
      <h1 className="page-title">Карты</h1>
      <p className="subtext">Привязка NFC-карт к аккаунтам: выпуск, блокировка, замена и поиск владельца по UID.</p>

      <PageSection title="Поиск владельца по UID">
        <div className="filters-bar" style={{ margin: 0 }}>
          <input
            ref={lookupInputRef}
            type="text"
            className="filter-input"
            placeholder="UID карты (или приложите карту к считывателю)..."
            value={lookupUid}
            autoComplete="off"
            onChange={(e) => setLookupUid(e.target.value)}
            onKeyDown={(e) => {
              lookupScan.onKeyDown(e);
              if (e.key === 'Enter' && !e.defaultPrevented) runLookup();
            }}
          />
          <button className="btn btn-primary" onClick={() => runLookup()} disabled={lookupLoading}>
            {lookupLoading ? 'Ищу...' : 'Найти'}
          </button>
        </div>
        {lookupScan.scanning && <p className="hint" style={{ marginTop: 8 }}>Считывание карты…</p>}
        {lookupError && <ErrorBlock message={lookupError} />}
        {lookup && (
          <dl className="detail-list" style={{ marginTop: 12 }}>
            <dt>UID</dt>        <dd className="mono">{lookup.cardUid}</dd>
            <dt>Статус карты</dt><dd><CardStatusBadge status={lookup.cardStatus || ''} /></dd>
            <dt>Владелец</dt>   <dd>{lookup.publicName || '—'}</dd>
            <dt>Роль</dt>       <dd>{lookup.accountRole ? ROLE_LABELS[lookup.accountRole] || lookup.accountRole : '—'}</dd>
            <dt>Account ID</dt> <dd className="mono">{lookup.accountId ?? '—'}</dd>
          </dl>
        )}
      </PageSection>

      <PageSection title="Выбор игрока">
        <AccountPicker
          placeholder="Поиск по имени игрока…"
          selected={selected}
          onSelect={selectAccount}
          onClear={() => { setSelected(null); setCards([]); }}
        />
      </PageSection>

      {selected && (
        <>
          <PageSection title={`Выпустить карту — ${selected.publicName}`}>
            <form onSubmit={handleIssue} className="form-grid">
              <FormField label="UID карты (NFC — можно приложить карту к считывателю)" id="cardUid" required>
                <input
                  id="cardUid"
                  ref={cardUidInputRef}
                  type="text"
                  value={cardUid}
                  autoComplete="off"
                  onChange={(e) => setCardUid(e.target.value)}
                  onKeyDown={cardUidScan.onKeyDown}
                  required
                />
              </FormField>
              <FormField label="Заметка" id="notes">
                <input id="notes" type="text" value={notes} onChange={(e) => setNotes(e.target.value)} />
              </FormField>
              {issueError && <div className="full"><ErrorBlock message={issueError} /></div>}
              {issueSuccess && <div className="full"><SuccessMessage message={issueSuccess} /></div>}
              <div className="full">
                <button type="submit" className="btn btn-primary" disabled={issuing || !cardUid.trim()}>
                  {issuing ? 'Выпускаю...' : 'Выпустить карту'}
                </button>
              </div>
            </form>
            <p className="hint" style={{ marginTop: 12 }}>
              У аккаунта может быть только одна активная карта. UID должен быть уникальным.
            </p>
          </PageSection>

          <PageSection title="Карты аккаунта">
            {cardsError && <ErrorBlock message={cardsError} />}
            {cardsLoading ? (
              <LoadingBlock />
            ) : cards.length === 0 ? (
              <p className="empty-state">Карты не найдены</p>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <SortTh k="id" label="ID" sort={cardsSort} />
                    <SortTh k="cardUid" label="UID" sort={cardsSort} />
                    <SortTh k="status" label="Статус" sort={cardsSort} />
                    <SortTh k="issuedAt" label="Выпущена" sort={cardsSort} />
                    <th>Заметка</th>
                    <th>Действия</th>
                  </tr>
                </thead>
                <tbody>
                  {cardsSort.sorted.map((c) => (
                    <tr key={c.id}>
                      <td className="mono">{c.id}</td>
                      <td className="mono">{c.cardUid}</td>
                      <td><CardStatusBadge status={c.status} /></td>
                      <td>{formatDate(c.issuedAt)}</td>
                      <td>{c.notes || (c.blockedReason ? `(${c.blockedReason})` : '—')}</td>
                      <td className="actions">
                        {c.status === 'ISSUED' && (
                          <>
                            <button className="btn btn-sm btn-ghost" onClick={() => block(c)}>Блок</button>
                            <button className="btn btn-sm btn-ghost" onClick={() => markLost(c)}>Утеряна</button>
                            <button className="btn btn-sm btn-secondary" onClick={() => replace(c)}>Заменить</button>
                          </>
                        )}
                        {c.status === 'LOST' && (
                          <button className="btn btn-sm btn-secondary" onClick={() => replace(c)}>Заменить</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </PageSection>
        </>
      )}
    </div>
  );
}
