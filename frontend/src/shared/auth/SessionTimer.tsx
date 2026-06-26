import { useEffect, useState } from 'react';
import { TOKEN_KEY, USER_KEY } from '../api/client';
import { getTokenExpMs } from './jwt';

function format(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
}

/**
 * Shows the remaining time of the current session (from the JWT `exp` claim) and, when it
 * runs out, clears the stored auth and reloads to the login screen.
 */
export default function SessionTimer() {
  const expMs = getTokenExpMs(localStorage.getItem(TOKEN_KEY));
  const [remaining, setRemaining] = useState(() => (expMs ? expMs - Date.now() : 0));

  useEffect(() => {
    if (!expMs) return;
    const tick = () => {
      const rem = expMs - Date.now();
      setRemaining(rem);
      if (rem <= 0) {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        window.location.href = '/login';
      }
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [expMs]);

  if (!expMs) return null;

  const warn = remaining <= 60_000;
  return (
    <div className={`session-timer${warn ? ' session-timer-warn' : ''}`}>
      <span className="session-timer-label">Осталось времени</span>
      <span className="session-timer-value">{format(remaining)}</span>
    </div>
  );
}
