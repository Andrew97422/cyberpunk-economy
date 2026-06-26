import { useCallback, useEffect, useRef, useState, KeyboardEvent } from 'react';

/**
 * RFID USB reader integration (JT308, 125 kHz, EM4100).
 *
 * Hardware fact: the reader is a USB HID keyboard. When a tag is tapped it
 * rapidly "types" the tag number (~8-10 digits) followed by Enter. There is no
 * driver/SDK — we just capture the keystroke burst in a focused input.
 *
 * A scan is distinguished from manual typing by speed: a scan delivers every
 * character with tiny inter-key gaps and terminates with Enter. Manual typing
 * is much slower, so its Enter is treated as a manual submit (still allowed),
 * not an auto-detected scan.
 */

/** Max gap (ms) between two keystrokes for them to count as part of a scan burst. */
const MAX_INTERKEY_GAP_MS = 35;
/** A valid EM4100 burst is at least this many characters. */
const MIN_SCAN_LENGTH = 4;

interface ScanBuffer {
  chars: string;
  lastKeyTime: number;
  fast: boolean;
}

function createBuffer(): ScanBuffer {
  return { chars: '', lastKeyTime: 0, fast: true };
}

/**
 * Attach a keydown listener that buffers fast keystrokes and fires `onScan`
 * when a fast burst terminates with Enter.
 *
 * @param onScan       called with the captured code on a detected scan.
 * @param targetRef    optional input element to listen on; if omitted, listens on window.
 * @returns onKeyDown  handler you may attach to an input to also support manual Enter submit.
 */
export function useRfidScan(
  onScan: (code: string) => void,
  targetRef?: React.RefObject<HTMLInputElement>,
): { onKeyDown: (e: KeyboardEvent<HTMLInputElement>) => void; scanning: boolean } {
  const bufferRef = useRef<ScanBuffer>(createBuffer());
  const [scanning, setScanning] = useState(false);
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;

  const handleKey = useCallback((key: string, now: number) => {
    const buf = bufferRef.current;
    const gap = buf.chars.length === 0 ? 0 : now - buf.lastKeyTime;

    if (key === 'Enter') {
      const code = buf.chars;
      const wasFast = buf.fast && code.length >= MIN_SCAN_LENGTH;
      bufferRef.current = createBuffer();
      setScanning(false);
      if (wasFast) {
        onScanRef.current(code);
        return true; // consumed as a scan
      }
      return false; // slow → let manual submit happen
    }

    // Only single printable characters belong to an EM4100 burst.
    if (key.length === 1) {
      if (buf.chars.length > 0 && gap > MAX_INTERKEY_GAP_MS) {
        // Too slow to be part of a scan burst — restart as manual typing.
        bufferRef.current = { chars: key, lastKeyTime: now, fast: false };
        setScanning(false);
      } else {
        bufferRef.current = {
          chars: buf.chars + key,
          lastKeyTime: now,
          fast: buf.chars.length === 0 ? true : buf.fast,
        };
        if (bufferRef.current.fast && bufferRef.current.chars.length >= MIN_SCAN_LENGTH) {
          setScanning(true);
        }
      }
    }
    return false;
  }, []);

  // Window-level listener (used when no input ref is supplied).
  useEffect(() => {
    if (targetRef) return;
    const listener = (e: globalThis.KeyboardEvent) => {
      const consumed = handleKey(e.key, performance.now());
      if (consumed) e.preventDefault();
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [handleKey, targetRef]);

  const onKeyDown = useCallback(
    (e: KeyboardEvent<HTMLInputElement>) => {
      const consumed = handleKey(e.key, performance.now());
      if (consumed) e.preventDefault();
    },
    [handleKey],
  );

  return { onKeyDown, scanning };
}

interface RfidScanFieldProps {
  value: string;
  onChange: (value: string) => void;
  onScan: (code: string) => void;
  label?: string;
  placeholder?: string;
  autoFocus?: boolean;
}

/**
 * Controlled input wired to the RFID reader. Tapping a card fills the field and
 * fires `onScan`; the operator may also type a code by hand and press Enter
 * (which submits via `onScan` as well).
 */
export function RfidScanField({
  value,
  onChange,
  onScan,
  label = 'Приложите карту к считывателю или введите код вручную',
  placeholder = 'UID карты…',
  autoFocus,
}: RfidScanFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [scanned, setScanned] = useState(false);

  const handleScan = useCallback(
    (code: string) => {
      onChange(code);
      setScanned(true);
      onScan(code);
    },
    [onChange, onScan],
  );

  const { onKeyDown, scanning } = useRfidScan(handleScan, inputRef);

  const handleManualKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    onKeyDown(e);
    // If the burst-detector did not consume the Enter, treat it as manual submit.
    if (e.key === 'Enter' && !e.defaultPrevented) {
      e.preventDefault();
      const code = value.trim();
      if (code) {
        setScanned(true);
        onScan(code);
      }
    }
  };

  const status = scanning ? 'считывание…' : scanned ? 'карта считана ✓' : 'ожидание карты…';

  return (
    <div className="form-field">
      <label htmlFor="rfid-scan-field">{label}</label>
      <input
        id="rfid-scan-field"
        ref={inputRef}
        type="text"
        className="mono"
        value={value}
        placeholder={placeholder}
        autoFocus={autoFocus}
        autoComplete="off"
        onChange={(e) => {
          onChange(e.target.value);
          if (scanned) setScanned(false);
        }}
        onKeyDown={handleManualKeyDown}
      />
      <span className="hint" style={{ marginTop: 6, display: 'inline-block' }}>
        {status}
      </span>
    </div>
  );
}

export default RfidScanField;
