import type { OperationResultResponse } from '../../../shared/types';
import { formatMoney, getCurrencyLabel } from '../../../shared/utils';
import { ErrorBlock, SuccessMessage } from '../../../shared/ui';

interface Props {
  loading: boolean;
  error: string;
  success: string;
  result: OperationResultResponse | null;
  submitLabel?: string;
}

/** Submit button + feedback (error/success) + result card, shared by all banking forms. */
export default function OperationResult({ loading, error, success, result, submitLabel = 'Выполнить' }: Props) {
  return (
    <div className="action-footer">
      <button className="btn btn-primary" type="submit" disabled={loading}>
        {loading ? 'Отправляем...' : submitLabel}
      </button>
      {error && <ErrorBlock message={error} />}
      {success && <SuccessMessage message={success} />}
      {result && (
        <div className="result-card">
          <div><span>Игрок</span><strong>{result.publicName || '—'}</strong></div>
          <div><span>Связанный игрок</span><strong>{result.relatedPublicName || '—'}</strong></div>
          <div><span>Сумма</span><strong>{typeof result.amount === 'number' ? formatMoney(result.amount) : '—'}</strong></div>
          <div><span>Валюта</span><strong>{getCurrencyLabel(result.currencyType)}</strong></div>
          <div><span>Баланс после операции</span><strong>{typeof result.balanceAfter === 'number' ? formatMoney(result.balanceAfter) : '—'}</strong></div>
          <div><span>ID новой операции</span><strong>{result.transactionId || '—'}</strong></div>
          {result.message && <div className="full-row"><span>Сообщение</span><strong>{result.message}</strong></div>}
        </div>
      )}
    </div>
  );
}
