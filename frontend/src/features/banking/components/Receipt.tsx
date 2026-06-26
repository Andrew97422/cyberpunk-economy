import type { OrderResponse, TransactionResponse } from '../../../shared/types';
import {
  formatDate,
  formatMoney,
  getCurrencyLabel,
  getTransactionTypeLabel,
  ORDER_STATUS_LABELS,
} from '../../../shared/utils';
import './receipt.css';

type ReceiptProps =
  | { kind: 'transaction'; data: TransactionResponse; onClose: () => void }
  | { kind: 'order'; data: OrderResponse; onClose: () => void };

interface Row {
  label: string;
  value: string;
}

/** Compact YYYYMMDD stamp from an ISO date (falls back to "now"). */
function dateStamp(iso?: string): string {
  const d = iso ? new Date(iso) : new Date();
  const date = Number.isNaN(d.getTime()) ? new Date() : d;
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${y}${m}${day}`;
}

/** Deterministic pseudo-barcode line built from the receipt number. */
function pseudoBarcode(seed: string): string {
  const glyphs = ['│', '┃', '║', '╎'];
  let acc = 0;
  for (let i = 0; i < seed.length; i += 1) acc += seed.charCodeAt(i);
  const len = 40;
  let out = '';
  for (let i = 0; i < len; i += 1) {
    acc = (acc * 31 + i * 7) % 9973;
    out += glyphs[acc % glyphs.length];
  }
  return out;
}

function buildRows(props: ReceiptProps): { rows: Row[]; comment?: string; receiptNo: string; createdAt?: string } {
  if (props.kind === 'transaction') {
    const t = props.data;
    const receiptNo = `NC-${t.id ?? '0'}-${dateStamp(t.createdAt)}`;
    const rows: Row[] = [
      { label: 'Тип операции', value: getTransactionTypeLabel(t.type) },
      { label: 'Сумма', value: formatMoney(t.amount) },
      { label: 'Валюта', value: getCurrencyLabel(t.currencyType) },
      { label: 'Баланс после', value: formatMoney(t.balanceAfter) },
      { label: 'Статус', value: t.status || '—' },
      { label: 'Дата', value: formatDate(t.createdAt) },
      { label: 'ID операции', value: t.id != null ? String(t.id) : '—' },
    ];
    return { rows, comment: t.comment, receiptNo, createdAt: t.createdAt };
  }

  const o = props.data;
  const receiptNo = `NC-${o.id}-${dateStamp(o.createdAt)}`;
  const rows: Row[] = [
    { label: 'Товар', value: o.productName },
    { label: 'Кол-во', value: String(o.quantity) },
    { label: 'Сумма', value: formatMoney(o.totalPrice) },
    { label: 'Валюта', value: getCurrencyLabel(o.currencyType) },
    { label: 'Статус', value: ORDER_STATUS_LABELS[o.status] || o.status },
    { label: 'Дата', value: formatDate(o.createdAt) },
    { label: '№ заказа', value: String(o.id) },
  ];
  return { rows, receiptNo, createdAt: o.createdAt };
}

export default function Receipt(props: ReceiptProps) {
  const { onClose } = props;
  const { rows, comment, receiptNo, createdAt } = buildRows(props);
  const heading = props.kind === 'transaction' ? 'ЧЕК ОПЕРАЦИИ' : 'ЧЕК ЗАКАЗА';

  return (
    <div className="overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="receipt-print">
          <div className="receipt-header">
            <div className="receipt-brand">★ NIGHT CITY ★</div>
            <div className="receipt-title">{heading}</div>
          </div>

          <hr className="receipt-divider" />

          <div className="receipt-rows">
            {rows.map((r) => (
              <div className="receipt-row" key={r.label}>
                <span className="receipt-label">{r.label}</span>
                <span className="receipt-value">{r.value}</span>
              </div>
            ))}
          </div>

          {comment && <div className="receipt-comment">Комментарий: {comment}</div>}

          <hr className="receipt-divider" />

          <div className="receipt-footer">
            <div className="receipt-thanks">СПАСИБО ЗА ОПЕРАЦИЮ</div>
            <div className="receipt-barcode" aria-hidden="true">{pseudoBarcode(receiptNo)}</div>
            <div className="receipt-number">{receiptNo}</div>
            <div className="receipt-timestamp">Сформировано: {formatDate(createdAt ?? new Date().toISOString())}</div>
          </div>
        </div>

        <div className="receipt-actions">
          <button className="btn btn-primary btn-sm" onClick={() => window.print()}>Печать</button>
          <button className="btn btn-secondary btn-sm" onClick={onClose}>Закрыть</button>
        </div>
      </div>
    </div>
  );
}
