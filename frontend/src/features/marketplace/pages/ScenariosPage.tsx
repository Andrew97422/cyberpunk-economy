import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import { extractError, formatDate } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock, ConfirmDialog } from '../../../shared/ui';

// ---------------------------------------------------------------------------
// Local endpoint paths for scheduling (config.ts is owned by another process;
// the gateway will be wired to match exactly these paths/methods).
//   POST   /marketplace/scenarios/{id}/schedule   body { mode, startAt?, intervalSeconds?, endAt? }
//   POST   /marketplace/scenarios/{id}/trigger    (run now as a timed sequence)
//   GET    /marketplace/schedules
//   DELETE /marketplace/schedules/{id}
// ---------------------------------------------------------------------------
const SCHEDULE_ENDPOINTS = {
  schedules: '/marketplace/schedules',
  scheduleScenario: (id: number | string) => `/marketplace/scenarios/${id}/schedule`,
  triggerScenario: (id: number | string) => `/marketplace/scenarios/${id}/trigger`,
  scheduleById: (id: number | string) => `/marketplace/schedules/${id}`,
};

type Mode = 'MULTIPLY' | 'INCREASE_PCT' | 'DECREASE_PCT' | 'RESET';
type TimeUnit = 'sec' | 'min' | 'hour';
interface Step { mode: Mode; value: string; delayAmount: string; delayUnit: TimeUnit }
interface SavedStep { mode: Mode; value?: number; delaySeconds?: number | null }
interface Scenario { id: number; name: string; description?: string; steps: SavedStep[]; createdAt: string }
type Tab = 'quick' | 'builder' | 'saved';

type ScheduleMode = 'ONCE' | 'RECURRING';
interface Schedule {
  id: number;
  scenarioId: number;
  scenarioName?: string;
  mode: ScheduleMode;
  startAt?: string;
  intervalSeconds?: number | null;
  endAt?: string | null;
  active: boolean;
  nextFireAt?: string | null;
  lastFiredAt?: string | null;
  fireCount: number;
  createdAt: string;
}

// How the user picks WHEN to run a scenario.
type WhenKind = 'now' | 'after' | 'at' | 'recurring';

const MODE_LABELS: Record<Mode, string> = {
  MULTIPLY: 'Умножить цену на коэффициент',
  INCREASE_PCT: 'Повысить цены на %',
  DECREASE_PCT: 'Понизить цены на %',
  RESET: 'Сбросить к базовым ценам',
};

const UNIT_LABELS: Record<TimeUnit, string> = { sec: 'сек', min: 'мин', hour: 'час' };
const UNIT_SECONDS: Record<TimeUnit, number> = { sec: 1, min: 60, hour: 3600 };

function toSeconds(amount: string, unit: TimeUnit): number {
  const n = Number(amount);
  if (!Number.isFinite(n) || n < 0) return 0;
  return Math.round(n * UNIT_SECONDS[unit]);
}

/** Human description of a delay in seconds, e.g. "30 мин" or "сразу". */
function describeDelay(seconds: number): string {
  if (!seconds || seconds <= 0) return 'сразу';
  if (seconds % 3600 === 0) return `${seconds / 3600} ч`;
  if (seconds % 60 === 0) return `${seconds / 60} мин`;
  return `${seconds} сек`;
}

/** Human description of an interval, e.g. "каждые 30 мин". */
function describeInterval(seconds?: number | null): string {
  if (!seconds || seconds <= 0) return '';
  return `каждые ${describeDelay(seconds)}`;
}

function priceText(mode: Mode, value?: number | string): string {
  const v = value === undefined || value === '' ? '' : Number(value);
  switch (mode) {
    case 'MULTIPLY': return `Умножить все цены на ×${v}${typeof v === 'number' && v ? ` (${v > 1 ? '+' : ''}${Math.round((v - 1) * 100)}%)` : ''}`;
    case 'INCREASE_PCT': return `Повысить цены на +${v}%`;
    case 'DECREASE_PCT': return `Понизить цены на −${v}%`;
    case 'RESET': return 'Сбросить цены к базовым';
  }
}

/** Builder preview: includes the per-step delay, e.g. "через 30 мин: Понизить цены на −10%". */
function previewBuilderStep(s: Step, index: number): string {
  const secs = toSeconds(s.delayAmount, s.delayUnit);
  const prefix = index === 0
    ? (secs > 0 ? `через ${describeDelay(secs)}: ` : 'сразу: ')
    : `через ${describeDelay(secs)}: `;
  return prefix + priceText(s.mode, s.value);
}

/** Saved-scenario step preview: includes stored delaySeconds when present. */
function previewSavedStep(st: SavedStep, index: number): string {
  const secs = st.delaySeconds ?? 0;
  const prefix = index === 0
    ? (secs > 0 ? `через ${describeDelay(secs)}: ` : 'сразу: ')
    : `через ${describeDelay(secs)}: `;
  return prefix + priceText(st.mode, st.value);
}

const PRESETS: { label: string; emoji: string; mode: Mode; value?: number; desc: string }[] = [
  { label: 'Обвал цен −30%', emoji: '📉', mode: 'DECREASE_PCT', value: 30, desc: 'Кризис: все цены падают на 30%.' },
  { label: 'Инфляция +20%', emoji: '📈', mode: 'INCREASE_PCT', value: 20, desc: 'Цены растут на 20%.' },
  { label: 'Лёгкий рост +10%', emoji: '🔺', mode: 'INCREASE_PCT', value: 10, desc: 'Небольшое подорожание на 10%.' },
  { label: 'Распродажа −10%', emoji: '🏷️', mode: 'DECREASE_PCT', value: 10, desc: 'Скидка 10% на всё.' },
  { label: 'Сброс к базовым', emoji: '🔄', mode: 'RESET', desc: 'Вернуть все цены к исходным.' },
];

// ---------------------------------------------------------------------------
// Inline schedule chooser for one saved scenario.
// ---------------------------------------------------------------------------
interface SchedulePanelProps {
  scenario: Scenario;
  schedules: Schedule[];
  busy: boolean;
  onSchedule: (scenarioId: number, body: ScheduleBody, confirmMsg: string) => void;
  onTriggerNow: (scenarioId: number, confirmMsg: string) => void;
  onCancel: (schedule: Schedule) => void;
}

interface ScheduleBody {
  mode: ScheduleMode;
  startAt?: string;
  intervalSeconds?: number;
  endAt?: string;
}

function SchedulePanel({ scenario, schedules, busy, onSchedule, onTriggerNow, onCancel }: SchedulePanelProps) {
  const [when, setWhen] = useState<WhenKind>('now');
  const [afterAmount, setAfterAmount] = useState('10');
  const [afterUnit, setAfterUnit] = useState<TimeUnit>('min');
  const [atDateTime, setAtDateTime] = useState('');
  const [everyAmount, setEveryAmount] = useState('30');
  const [everyUnit, setEveryUnit] = useState<TimeUnit>('min');
  const [untilDateTime, setUntilDateTime] = useState('');

  const myActive = schedules.filter((s) => s.scenarioId === scenario.id && s.active);

  const submit = () => {
    if (when === 'now') {
      onTriggerNow(scenario.id, `Запустить сценарий «${scenario.name}» прямо сейчас (как таймлайн с задержками шагов)?`);
      return;
    }
    if (when === 'after') {
      const secs = toSeconds(afterAmount, afterUnit);
      const startAt = new Date(Date.now() + secs * 1000).toISOString();
      onSchedule(scenario.id, { mode: 'ONCE', startAt },
        `Запланировать «${scenario.name}» через ${describeDelay(secs)}?`);
      return;
    }
    if (when === 'at') {
      if (!atDateTime) return;
      const startAt = new Date(atDateTime).toISOString();
      onSchedule(scenario.id, { mode: 'ONCE', startAt },
        `Запланировать «${scenario.name}» на ${formatDate(startAt)}?`);
      return;
    }
    // recurring
    const interval = toSeconds(everyAmount, everyUnit);
    if (interval <= 0) return;
    const body: ScheduleBody = { mode: 'RECURRING', intervalSeconds: interval };
    if (untilDateTime) body.endAt = new Date(untilDateTime).toISOString();
    const untilText = untilDateTime ? ` до ${formatDate(body.endAt!)}` : '';
    onSchedule(scenario.id, body,
      `Запускать «${scenario.name}» ${describeInterval(interval)}${untilText}?`);
  };

  const canSubmit =
    when === 'now' ||
    when === 'after' ||
    (when === 'at' && !!atDateTime) ||
    (when === 'recurring' && toSeconds(everyAmount, everyUnit) > 0);

  return (
    <div className="page-section" style={{ marginTop: 10, background: 'transparent' }}>
      <strong style={{ fontSize: 14 }}>⏰ Расписание</strong>
      <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap', marginTop: 8 }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 200 }}>
          <span>Когда запустить</span>
          <select value={when} onChange={(e) => setWhen(e.target.value as WhenKind)}>
            <option value="now">Сразу (таймлайном)</option>
            <option value="after">Через…</option>
            <option value="at">В момент времени</option>
            <option value="recurring">Периодически</option>
          </select>
        </label>

        {when === 'after' && (
          <>
            <label style={{ width: 90, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span>Через</span>
              <input type="number" min="0" step="1" value={afterAmount} onChange={(e) => setAfterAmount(e.target.value)} />
            </label>
            <label style={{ width: 90, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span>Единица</span>
              <select value={afterUnit} onChange={(e) => setAfterUnit(e.target.value as TimeUnit)}>
                {(Object.keys(UNIT_LABELS) as TimeUnit[]).map((u) => <option key={u} value={u}>{UNIT_LABELS[u]}</option>)}
              </select>
            </label>
          </>
        )}

        {when === 'at' && (
          <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span>Момент времени</span>
            <input type="datetime-local" value={atDateTime} onChange={(e) => setAtDateTime(e.target.value)} />
          </label>
        )}

        {when === 'recurring' && (
          <>
            <label style={{ width: 90, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span>Каждые</span>
              <input type="number" min="1" step="1" value={everyAmount} onChange={(e) => setEveryAmount(e.target.value)} />
            </label>
            <label style={{ width: 90, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span>Единица</span>
              <select value={everyUnit} onChange={(e) => setEveryUnit(e.target.value as TimeUnit)}>
                {(Object.keys(UNIT_LABELS) as TimeUnit[]).map((u) => <option key={u} value={u}>{UNIT_LABELS[u]}</option>)}
              </select>
            </label>
            <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <span>До (необязательно)</span>
              <input type="datetime-local" value={untilDateTime} onChange={(e) => setUntilDateTime(e.target.value)} />
            </label>
          </>
        )}

        <button className="btn btn-secondary btn-sm" disabled={busy || !canSubmit} onClick={submit}>Запланировать</button>
      </div>

      {myActive.length > 0 && (
        <ul style={{ margin: '10px 0 0', paddingLeft: 18 }}>
          {myActive.map((sch) => {
            const desc = sch.mode === 'RECURRING'
              ? `${describeInterval(sch.intervalSeconds)}${sch.nextFireAt ? `, следующий запуск ${formatDate(sch.nextFireAt)}` : ''}${sch.endAt ? `, до ${formatDate(sch.endAt)}` : ''}`
              : `разово${sch.nextFireAt ? `, запуск ${formatDate(sch.nextFireAt)}` : ''}`;
            return (
              <li key={sch.id} className="subtext" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                <span>{desc} (срабатываний: {sch.fireCount})</span>
                <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => onCancel(sch)}>Отменить</button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export default function ScenariosPage() {
  const [tab, setTab] = useState<Tab>('quick');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<{ message: string; run: () => Promise<void> } | null>(null);

  // builder
  const [steps, setSteps] = useState<Step[]>([{ mode: 'DECREASE_PCT', value: '30', delayAmount: '0', delayUnit: 'min' }]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  // saved
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [loadingSaved, setLoadingSaved] = useState(false);
  const [schedules, setSchedules] = useState<Schedule[]>([]);

  const flash = (msg: string) => { setSuccess(msg); setError(''); setTimeout(() => setSuccess(''), 6000); };

  const loadScenarios = async () => {
    setLoadingSaved(true);
    setError('');
    try {
      const [scRes, schRes] = await Promise.all([
        client.get<Scenario[]>(ENDPOINTS.scenarios),
        client.get<Schedule[]>(SCHEDULE_ENDPOINTS.schedules),
      ]);
      setScenarios(scRes.data || []);
      setSchedules(schRes.data || []);
    } catch (err) { setError(extractError(err)); }
    finally { setLoadingSaved(false); }
  };

  const loadSchedules = async () => {
    try {
      const res = await client.get<Schedule[]>(SCHEDULE_ENDPOINTS.schedules);
      setSchedules(res.data || []);
    } catch (err) { setError(extractError(err)); }
  };

  useEffect(() => { if (tab === 'saved') loadScenarios(); }, [tab]);

  const runConfirmed = (message: string, run: () => Promise<void>) => setConfirm({ message, run });

  const doConfirm = async () => {
    if (!confirm) return;
    setBusy(true);
    setError('');
    try { await confirm.run(); }
    catch (err) { setError(extractError(err)); }
    finally { setBusy(false); setConfirm(null); }
  };

  const applyPreset = (p: typeof PRESETS[number]) =>
    runConfirmed(`${p.desc}\n\nПрименить ко всем активным товарам?`, async () => {
      const res = await client.post<{ affected: number }>(ENDPOINTS.massPriceOp,
        { mode: p.mode, value: p.value });
      flash(`Готово. Изменено товаров: ${res.data.affected}.`);
    });

  const addStep = () => setSteps([...steps, { mode: 'INCREASE_PCT', value: '10', delayAmount: '0', delayUnit: 'min' }]);
  const removeStep = (i: number) => setSteps(steps.filter((_, idx) => idx !== i));
  const updateStep = (i: number, patch: Partial<Step>) =>
    setSteps(steps.map((s, idx) => (idx === i ? { ...s, ...patch } : s)));

  const saveScenario = async () => {
    if (!name.trim()) { setError('Укажите название сценария'); return; }
    setBusy(true); setError('');
    try {
      const payloadSteps = steps.map((s) => ({
        mode: s.mode,
        value: s.mode === 'RESET' ? null : Number(s.value),
        delaySeconds: toSeconds(s.delayAmount, s.delayUnit),
      }));
      await client.post(ENDPOINTS.scenarios, { name: name.trim(), description: description.trim() || undefined, steps: payloadSteps });
      flash('Сценарий сохранён.');
      setName(''); setDescription('');
      setSteps([{ mode: 'DECREASE_PCT', value: '30', delayAmount: '0', delayUnit: 'min' }]);
      setTab('saved');
    } catch (err) { setError(extractError(err)); }
    finally { setBusy(false); }
  };

  const applyScenario = (s: Scenario) =>
    runConfirmed(`Применить сценарий «${s.name}» (${s.steps.length} шаг(ов)) ко всем активным товарам сразу (без задержек)?`, async () => {
      const res = await client.post<{ totalAffected: number }>(ENDPOINTS.applyScenario(s.id), {});
      flash(`Сценарий «${s.name}» применён. Изменений цен: ${res.data.totalAffected}.`);
    });

  const deleteScenario = (s: Scenario) =>
    runConfirmed(`Удалить сценарий «${s.name}»?`, async () => {
      await client.delete(ENDPOINTS.scenarioById(s.id));
      flash('Сценарий удалён.');
      setScenarios((prev) => prev.filter((x) => x.id !== s.id));
      setSchedules((prev) => prev.filter((x) => x.scenarioId !== s.id));
    });

  const scheduleScenario = (scenarioId: number, body: ScheduleBody, confirmMsg: string) =>
    runConfirmed(confirmMsg, async () => {
      await client.post(SCHEDULE_ENDPOINTS.scheduleScenario(scenarioId), body);
      flash('Расписание создано.');
      await loadSchedules();
    });

  const triggerNow = (scenarioId: number, confirmMsg: string) =>
    runConfirmed(confirmMsg, async () => {
      const res = await client.post<{ }>(SCHEDULE_ENDPOINTS.triggerScenario(scenarioId), {});
      const enqueued = typeof res.data === 'number' ? res.data : undefined;
      flash(enqueued !== undefined ? `Запущено. Шагов в очереди: ${enqueued}.` : 'Сценарий запущен таймлайном.');
    });

  const cancelSchedule = (sch: Schedule) =>
    runConfirmed(`Отменить это расписание?`, async () => {
      await client.delete(SCHEDULE_ENDPOINTS.scheduleById(sch.id));
      flash('Расписание отменено.');
      setSchedules((prev) => prev.filter((x) => x.id !== sch.id));
    });

  return (
    <div className="page" style={{ maxWidth: 820 }}>
      <h1 className="page-title">Сценарии экономики</h1>
      <p className="subtext">Меняйте цены в магазине пакетно — кризисы, инфляция, распродажи — вместо ручной правки ценников.</p>

      <div className="toolbar" style={{ gap: 8, marginTop: 12 }}>
        <button className={`btn btn-sm ${tab === 'quick' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('quick')}>⚡ Быстрые события</button>
        <button className={`btn btn-sm ${tab === 'builder' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('builder')}>🧩 Конструктор</button>
        <button className={`btn btn-sm ${tab === 'saved' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('saved')}>📚 Мои сценарии</button>
      </div>

      {error && <div style={{ marginTop: 12 }}><ErrorBlock message={error} /></div>}
      {success && <div className="success-block" style={{ marginTop: 12, whiteSpace: 'pre-line' }}>✔ {success}</div>}

      {tab === 'quick' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(230px, 1fr))', gap: 12, marginTop: 16 }}>
          {PRESETS.map((p) => (
            <div key={p.label} className="dashboard-card" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ fontSize: 22 }}>{p.emoji}</div>
              <strong>{p.label}</strong>
              <span className="subtext" style={{ flex: 1 }}>{p.desc}</span>
              <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => applyPreset(p)}>Применить</button>
            </div>
          ))}
        </div>
      )}

      {tab === 'builder' && (
        <div style={{ marginTop: 16 }}>
          <div className="page-section">
            <h2 className="section-title" style={{ marginBottom: 12 }}>Шаги сценария</h2>
            {steps.map((s, i) => (
              <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'flex-end', marginBottom: 10, flexWrap: 'wrap' }}>
                <label style={{ flex: '1 1 220px', display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span>Действие</span>
                  <select value={s.mode} onChange={(e) => updateStep(i, { mode: e.target.value as Mode })}>
                    {(Object.keys(MODE_LABELS) as Mode[]).map((m) => <option key={m} value={m}>{MODE_LABELS[m]}</option>)}
                  </select>
                </label>
                {s.mode !== 'RESET' && (
                  <label style={{ width: 120, display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span>{s.mode === 'MULTIPLY' ? 'Коэффициент' : 'Процент'}</span>
                    <input type="number" step="0.01" min="0" value={s.value} onChange={(e) => updateStep(i, { value: e.target.value })} />
                  </label>
                )}
                <label style={{ width: 90, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span>Задержка</span>
                  <input type="number" step="1" min="0" value={s.delayAmount} onChange={(e) => updateStep(i, { delayAmount: e.target.value })} />
                </label>
                <label style={{ width: 90, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span>Единица</span>
                  <select value={s.delayUnit} onChange={(e) => updateStep(i, { delayUnit: e.target.value as TimeUnit })}>
                    {(Object.keys(UNIT_LABELS) as TimeUnit[]).map((u) => <option key={u} value={u}>{UNIT_LABELS[u]}</option>)}
                  </select>
                </label>
                {steps.length > 1 && <button className="btn btn-ghost btn-sm" onClick={() => removeStep(i)}>✕</button>}
                <div className="subtext" style={{ flexBasis: '100%' }}>→ {previewBuilderStep(s, i)}</div>
              </div>
            ))}
            <button className="btn btn-secondary btn-sm" onClick={addStep}>+ Добавить шаг</button>
            <p className="subtext" style={{ marginTop: 8 }}>
              Задержка отсчитывается от предыдущего шага. У первого шага «0» означает «сразу».
            </p>
          </div>

          <div className="form-grid" style={{ marginTop: 16 }}>
            <label><span>Название сценария</span><input value={name} onChange={(e) => setName(e.target.value)} placeholder="Например: Кризис на бирже" /></label>
            <label><span>Описание (необязательно)</span><input value={description} onChange={(e) => setDescription(e.target.value)} /></label>
          </div>
          <button className="btn btn-primary" style={{ marginTop: 12 }} disabled={busy} onClick={saveScenario}>Сохранить сценарий</button>
        </div>
      )}

      {tab === 'saved' && (
        <div style={{ marginTop: 16 }}>
          {loadingSaved && <LoadingBlock />}
          {!loadingSaved && scenarios.length === 0 && <p className="empty-state">Сохранённых сценариев пока нет. Создайте в «Конструкторе».</p>}
          {scenarios.map((s) => (
            <div key={s.id} className="page-section" style={{ marginBottom: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <div>
                  <strong>{s.name}</strong>
                  {s.description && <div className="subtext">{s.description}</div>}
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-primary btn-sm" disabled={busy} onClick={() => applyScenario(s)}>Применить</button>
                  <button className="btn btn-danger btn-sm" disabled={busy} onClick={() => deleteScenario(s)}>Удалить</button>
                </div>
              </div>
              <ul style={{ margin: '10px 0 0', paddingLeft: 18 }}>
                {s.steps.map((st, idx) => <li key={idx} className="subtext">{previewSavedStep(st, idx)}</li>)}
              </ul>
              <div className="subtext" style={{ marginTop: 6 }}>Создан: {formatDate(s.createdAt)}</div>
              <SchedulePanel
                scenario={s}
                schedules={schedules}
                busy={busy}
                onSchedule={scheduleScenario}
                onTriggerNow={triggerNow}
                onCancel={cancelSchedule}
              />
            </div>
          ))}
        </div>
      )}

      {confirm && (
        <ConfirmDialog message={confirm.message} onConfirm={doConfirm} onCancel={() => setConfirm(null)} />
      )}
    </div>
  );
}
