import { useState } from 'react';
import client from '../../shared/api/client';
import type { OperationResultResponse } from '../../shared/types';
import { extractError, getOperationSuccessMessage } from '../../shared/utils';

/** Shared submit logic for all banking operations (deposit/withdraw/transfer/reverse). */
export function useOperation() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [result, setResult] = useState<OperationResultResponse | null>(null);

  const submit = async (url: string, payload: unknown) => {
    setLoading(true);
    setError('');
    setSuccess('');
    setResult(null);
    try {
      const res = await client.post<OperationResultResponse>(url, payload);
      setResult(res.data);
      setSuccess(getOperationSuccessMessage(res.data.operation));
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  return { loading, error, success, result, submit };
}
