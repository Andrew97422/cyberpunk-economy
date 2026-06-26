import { ReactNode } from 'react';
import { STATUS_LABELS } from '../utils';

export function PageSection({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="page-section">
      {title && <h2 className="section-title">{title}</h2>}
      {children}
    </section>
  );
}

export function FormField({
  label,
  id,
  required,
  children,
}: {
  label: string;
  id: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="form-field">
      <label htmlFor={id}>
        {label}
        {required && <span className="required">*</span>}
      </label>
      {children}
    </div>
  );
}

interface Option {
  value: string;
  label: string;
}

export function SelectField({
  id,
  value,
  onChange,
  options,
  disabled,
}: {
  id: string;
  value: string;
  onChange: (v: string) => void;
  options: Option[];
  disabled?: boolean;
}) {
  return (
    <select id={id} value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled}>
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

export function PinField({ value, onRegenerate }: { value: string; onRegenerate: () => void }) {
  return (
    <div className="pin-field">
      <input type="text" value={value} readOnly className="mono pin-value" aria-label="PIN-код" />
      <button type="button" className="btn btn-secondary btn-sm" onClick={onRegenerate}>
        Сгенерировать заново
      </button>
    </div>
  );
}

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABELS[status] || status}</span>;
}

export function ErrorBlock({ message }: { message: string }) {
  return <div className="error-block">{message}</div>;
}

export function SuccessMessage({ message }: { message: string }) {
  return <div className="success-block">{message}</div>;
}

export function WarningBlock({ children }: { children: ReactNode }) {
  return <div className="warning-block">{children}</div>;
}

export function LoadingBlock({ text = 'Загрузка...' }: { text?: string }) {
  return <div className="loading-block">{text}</div>;
}

export function ConfirmDialog({
  message,
  onConfirm,
  onCancel,
}: {
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="overlay">
      <div className="dialog">
        <p>{message}</p>
        <div className="dialog-actions">
          <button className="btn btn-danger" onClick={onConfirm}>
            Подтвердить
          </button>
          <button className="btn btn-secondary" onClick={onCancel}>
            Отмена
          </button>
        </div>
      </div>
    </div>
  );
}
