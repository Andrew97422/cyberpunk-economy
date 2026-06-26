import { useState, FormEvent } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { AccountResponse, PagedResponse, PinResponse } from '../../../shared/types';
import { extractError, formatDate, generatePin, ROLE_LABELS, PIN_STATUS_LABELS } from '../../../shared/utils';
import { PageSection, FormField, PinField, ErrorBlock, SuccessMessage, LoadingBlock } from '../../../shared/ui';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';

interface Selected {
  id: number;
  publicName: string;
}

export default function PinsPage() {
  // --- account picker ---
  const [search, setSearch] = useState('');
  const [results, setResults] = useState<AccountResponse[]>([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [selected, setSelected] = useState<Selected | null>(null);

  // --- pins list ---
  const [pins, setPins] = useState<PinResponse[]>([]);
  const [pinsLoading, setPinsLoading] = useState(false);
  const [pinsError, setPinsError] = useState('');

  // --- issue form ---
  const [rawPin, setRawPin] = useState(() => generatePin());
  const [duration, setDuration] = useState('120');
  const [comment, setComment] = useState('');
  const [issuing, setIssuing] = useState(false);
  const [issueError, setIssueError] = useState('');
  const [issuedPin, setIssuedPin] = useState('');

  const runSearch = async () => {
    setSearching(true);
    setSearchError('');
    try {
      const res = await client.get<PagedResponse<AccountResponse>>(ENDPOINTS.accounts, {
        params: { search, page: 0, size: 10, sortBy: 'createdAt', direction: 'DESC' },
      });
      setResults(res.data.content);
    } catch (err) {
      setSearchError(extractError(err));
    } finally {
      setSearching(false);
    }
  };

  const loadPins = async (accountId: number) => {
    setPinsLoading(true);
    setPinsError('');
    try {
      const res = await client.get<PinResponse[] | PagedResponse<PinResponse>>(ENDPOINTS.pinsByAccount(accountId));
      const list = Array.isArray(res.data) ? res.data : res.data.content;
      setPins(list || []);
    } catch (err) {
      setPinsError(extractError(err));
    } finally {
      setPinsLoading(false);
    }
  };

  const selectAccount = (a: AccountResponse) => {
    setSelected({ id: a.id, publicName: a.publicName });
    setResults([]);
    setSearch('');
    setIssuedPin('');
    setIssueError('');
    setRawPin(generatePin());
    loadPins(a.id);
  };

  const handleIssue = async (e: FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    setIssuing(true);
    setIssueError('');
    setIssuedPin('');
    try {
      await client.post<PinResponse>(ENDPOINTS.createPin, {
        publicName: selected.publicName,
        rawPin,
        durationMinutes: parseInt(duration, 10),
        comment: comment || undefined,
      });
      setIssuedPin(rawPin);
      setRawPin(generatePin());
      setComment('');
      loadPins(selected.id);
    } catch (err) {
      setIssueError(extractError(err));
    } finally {
      setIssuing(false);
    }
  };

  const pinsSort = useTableSort(pins, 'createdAt', 'desc');

  const revoke = async (pinId: number) => {
    if (!selected || !window.confirm('Отозвать этот PIN-код?')) return;
    try {
      await client.post(ENDPOINTS.revokePin(pinId));
      loadPins(selected.id);
    } catch (err) {
      setPinsError(extractError(err));
    }
  };

  return (
    <div className="page">
      <h1 className="page-title">PIN-коды</h1>
      <p className="subtext">Выпуск временных PIN-кодов для входа игроков и управление активными кодами.</p>

      <PageSection title="Выбор аккаунта">
        <div className="filters-bar" style={{ margin: 0 }}>
          <input
            type="text"
            className="filter-input"
            placeholder="Поиск по имени игрока..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && runSearch()}
          />
          <button className="btn btn-primary" onClick={runSearch} disabled={searching}>
            {searching ? 'Ищу...' : 'Найти'}
          </button>
        </div>

        {searchError && <ErrorBlock message={searchError} />}

        {results.length > 0 && (
          <table className="table" style={{ marginTop: 12 }}>
            <thead>
              <tr><th>ID</th><th>Публичное имя</th><th>Персонаж</th><th>Роль</th><th></th></tr>
            </thead>
            <tbody>
              {results.map((a) => (
                <tr key={a.id}>
                  <td className="mono">{a.id}</td>
                  <td>{a.publicName}</td>
                  <td>{a.characterName || '—'}</td>
                  <td>{ROLE_LABELS[a.role] || a.role}</td>
                  <td className="actions">
                    <button className="btn btn-sm btn-secondary" onClick={() => selectAccount(a)}>Выбрать</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </PageSection>

      {selected && (
        <>
          <PageSection title={`Выпустить PIN — ${selected.publicName}`}>
            {issuedPin ? (
              <div>
                <SuccessMessage message="PIN-код успешно создан. Передайте его игроку — позже значение не восстановить." />
                <div className="info-row" style={{ marginTop: 12 }}>
                  <span>Новый PIN:</span>
                  <strong className="mono" style={{ fontSize: '1.4rem', letterSpacing: '0.15em' }}>{issuedPin}</strong>
                </div>
                <button className="btn btn-ghost btn-sm" style={{ marginTop: 12 }} onClick={() => setIssuedPin('')}>
                  Выпустить ещё
                </button>
              </div>
            ) : (
              <form onSubmit={handleIssue} className="form-grid">
                <div className="full">
                  <FormField label="PIN-код (генерируется автоматически, 8 цифр)" id="rawPin">
                    <PinField value={rawPin} onRegenerate={() => setRawPin(generatePin())} />
                  </FormField>
                </div>
                <FormField label="Действителен (минуты, 1–1440)" id="duration" required>
                  <input id="duration" type="number" value={duration} onChange={(e) => setDuration(e.target.value)} min={1} max={1440} required />
                </FormField>
                <div className="full">
                  <FormField label="Комментарий" id="comment">
                    <input id="comment" type="text" value={comment} onChange={(e) => setComment(e.target.value)} />
                  </FormField>
                </div>
                {issueError && <div className="full"><ErrorBlock message={issueError} /></div>}
                <div className="full">
                  <button type="submit" className="btn btn-primary" disabled={issuing}>
                    {issuing ? 'Выпускаю...' : 'Выпустить PIN'}
                  </button>
                </div>
              </form>
            )}
            <p className="hint" style={{ marginTop: 12 }}>
              У аккаунта может быть только один активный PIN. Если код уже выпущен — отзовите его ниже перед выпуском нового.
            </p>
          </PageSection>

          <PageSection title="PIN-коды аккаунта">
            {pinsError && <ErrorBlock message={pinsError} />}
            {pinsLoading ? (
              <LoadingBlock />
            ) : pins.length === 0 ? (
              <p className="empty-state">PIN-коды не найдены</p>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <SortTh k="id" label="ID" sort={pinsSort} />
                    <SortTh k="status" label="Статус" sort={pinsSort} />
                    <SortTh k="durationMinutes" label="Срок (мин)" sort={pinsSort} />
                    <SortTh k="createdAt" label="Создан" sort={pinsSort} />
                    <SortTh k="usedAt" label="Использован" sort={pinsSort} />
                    <SortTh k="comment" label="Комментарий" sort={pinsSort} />
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {pinsSort.sorted.map((p) => (
                    <tr key={p.id}>
                      <td className="mono">{p.id}</td>
                      <td>{PIN_STATUS_LABELS[p.status] || p.status}</td>
                      <td>{p.durationMinutes}</td>
                      <td>{formatDate(p.createdAt)}</td>
                      <td>{formatDate(p.usedAt)}</td>
                      <td>{p.comment || '—'}</td>
                      <td className="actions">
                        {p.status === 'CREATED' && (
                          <button className="btn btn-sm btn-danger" onClick={() => revoke(p.id)}>Отозвать</button>
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
