import { useEffect, useState, FormEvent } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { AccountResponse } from '../../../shared/types';
import { ACCOUNT_STATUSES } from '../../../shared/types';
import { STATUS_LABELS, extractError } from '../../../shared/utils';
import { StatusBadge, SelectField, FormField, ErrorBlock, SuccessMessage, WarningBlock, LoadingBlock } from '../../../shared/ui';

export default function ChangeStatusPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [account, setAccount] = useState<AccountResponse | null>(null);
  const [newStatus, setNewStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    if (!id) return;
    client
      .get<AccountResponse>(ENDPOINTS.accountById(id))
      .then((r) => {
        setAccount(r.data);
        setNewStatus(r.data.status);
      })
      .catch((err) => setError(extractError(err)))
      .finally(() => setLoading(false));
  }, [id]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!confirming) {
      setConfirming(true);
      return;
    }
    setSaving(true);
    setError('');
    try {
      await client.patch(ENDPOINTS.updateStatus(id!), { status: newStatus });
      setSuccess(`Статус изменён на "${STATUS_LABELS[newStatus] || newStatus}"`);
      setTimeout(() => navigate(`/accounts/${id}`), 1200);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setSaving(false);
      setConfirming(false);
    }
  };

  if (loading) return <div className="page"><LoadingBlock /></div>;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <Link to={`/accounts/${id}`} className="back-link">← Профиль аккаунта</Link>
          <h1 className="page-title">Изменить статус</h1>
        </div>
      </div>

      {account && (
        <div className="info-row">
          <span>Текущий статус:</span>
          <StatusBadge status={account.status} />
          <span className="text-muted">({account.publicName})</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="form form-wide" style={{ marginTop: '1.5rem' }}>
        <FormField label="Новый статус" id="newStatus" required>
          <SelectField
            id="newStatus"
            value={newStatus}
            onChange={(v) => { setNewStatus(v); setConfirming(false); }}
            options={ACCOUNT_STATUSES.map((s) => ({ value: s, label: STATUS_LABELS[s] }))}
          />
        </FormField>

        {confirming && (
          <WarningBlock>
            Вы уверены, что хотите изменить статус на <strong>{STATUS_LABELS[newStatus] || newStatus}</strong>?
          </WarningBlock>
        )}

        {error && <ErrorBlock message={error} />}
        {success && <SuccessMessage message={success} />}

        <div className="btn-group">
          <button type="submit" className="btn btn-primary" disabled={saving || newStatus === account?.status}>
            {confirming ? 'Подтвердить изменение' : 'Изменить статус'}
          </button>
          {confirming && (
            <button type="button" className="btn btn-ghost" onClick={() => setConfirming(false)}>Отмена</button>
          )}
        </div>
      </form>
    </div>
  );
}
