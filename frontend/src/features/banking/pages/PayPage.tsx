import { useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { BalanceResponse, CardLookupResponse, CurrencyType, OperationResultResponse } from '../../../shared/types';
import { CURRENCY_TYPES } from '../../../shared/types';
import { extractError, formatMoney, getCurrencyLabel } from '../../../shared/utils';
import { PageSection, FormField, SelectField, ErrorBlock, SuccessMessage, ConfirmDialog } from '../../../shared/ui';
import RfidScanField from '../../../shared/ui/RfidScanField';

/**
 * Player self-service payment: pay another player from your OWN balance.
 * Pick the recipient by typing their name or by tapping their card on the reader.
 */
export default function PayPage() {
  const [balance, setBalance] = useState<BalanceResponse | null>(null);
  const [recipient, setRecipient] = useState('');
  const [scanInfo, setScanInfo] = useState('');
  const [scanUid, setScanUid] = useState('');
  const [currencyType, setCurrencyType] = useState<CurrencyType>('CASHLESS');
  const [amount, setAmount] = useState('');
  const [comment, setComment] = useState('');

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadBalance = () =>
    client.get<BalanceResponse>(ENDPOINTS.myBalance).then((r) => setBalance(r.data)).catch(() => setBalance(null));

  useEffect(() => { loadBalance(); }, []);

  const onScan = async (code: string) => {
    const uid = code.trim();
    if (!uid) return;
    setError('');
    setScanInfo('');
    try {
      const res = await client.get<CardLookupResponse>(ENDPOINTS.payLookup, { params: { cardUid: uid } });
      const c = res.data;
      if (!c || !c.publicName) {
        setError('Карта не найдена или не привязана к игроку.');
        return;
      }
      if (c.cardStatus === 'BLOCKED' || c.cardStatus === 'LOST') {
        setError('Карта получателя недоступна.');
        return;
      }
      setRecipient(c.publicName);
      setScanUid(uid);
      setScanInfo(`Карта игрока ${c.publicName}`);
    } catch (err) {
      setError(extractError(err));
    }
  };

  const amountNumber = Number(amount);
  const myFunds = currencyType === 'CRYPTO' ? balance?.cryptoAmount : balance?.cashlessAmount;
  const canPay =
    recipient.trim() !== '' &&
    Number.isFinite(amountNumber) &&
    amountNumber > 0 &&
    (myFunds === undefined || myFunds === null || amountNumber <= myFunds);

  const doPay = async () => {
    setConfirmOpen(false);
    setPaying(true);
    setError('');
    setSuccess('');
    try {
      const res = await client.post<OperationResultResponse>(ENDPOINTS.pay, {
        toPublicName: recipient.trim(),
        currencyType,
        amount: amountNumber,
        comment: comment || undefined,
      });
      setSuccess(
        `Отправлено ${formatMoney(amountNumber)} (${getCurrencyLabel(currencyType)}) игроку ${recipient.trim()}. ` +
          (typeof res.data.balanceAfter === 'number' ? `Ваш баланс: ${formatMoney(res.data.balanceAfter)}.` : ''),
      );
      setAmount('');
      setComment('');
      setScanInfo('');
      setScanUid('');
      loadBalance();
    } catch (err) {
      setError(extractError(err));
    } finally {
      setPaying(false);
    }
  };

  return (
    <div className="page" style={{ maxWidth: 640 }}>
      <h1 className="page-title">Оплатить / Перевести</h1>
      <p className="subtext">Переведите средства другому игроку со своего счёта — по имени или приложив его карту.</p>

      <div className="stat-grid" style={{ marginTop: 8 }}>
        <div className="stat-box"><span>Мои безналичные</span><strong>{formatMoney(balance?.cashlessAmount)}</strong></div>
        <div className="stat-box"><span>Моя крипта</span><strong>{formatMoney(balance?.cryptoAmount)}</strong></div>
      </div>

      <PageSection title="Получатель">
        <RfidScanField
          value={scanUid}
          onChange={setScanUid}
          onScan={onScan}
          label="Приложите карту получателя к считывателю"
          placeholder="или введите имя ниже"
        />
        {scanInfo && <p className="subtext" style={{ marginTop: 6 }}>✓ {scanInfo}</p>}
        <div style={{ marginTop: 12 }}>
          <FormField label="Имя получателя" id="pay-to" required>
            <input id="pay-to" type="text" value={recipient}
                   onChange={(e) => { setRecipient(e.target.value); setScanInfo(''); }}
                   placeholder="публичное имя игрока" disabled={paying} />
          </FormField>
        </div>
      </PageSection>

      <PageSection title="Сумма">
        <div className="form-grid">
          <FormField label="Валюта" id="pay-cur">
            <SelectField id="pay-cur" value={currencyType} onChange={(v) => setCurrencyType(v as CurrencyType)}
                         options={CURRENCY_TYPES.map((c) => ({ value: c, label: getCurrencyLabel(c) }))} disabled={paying} />
          </FormField>
          <FormField label="Сумма" id="pay-amt" required>
            <input id="pay-amt" type="number" step="0.01" min="0" value={amount}
                   onChange={(e) => setAmount(e.target.value)} disabled={paying} />
          </FormField>
          <FormField label="Комментарий" id="pay-comment">
            <input id="pay-comment" type="text" value={comment} onChange={(e) => setComment(e.target.value)} disabled={paying} />
          </FormField>
          {myFunds !== undefined && myFunds !== null && amountNumber > myFunds && (
            <div className="full"><ErrorBlock message="Недостаточно средств на вашем счёте." /></div>
          )}
          {error && <div className="full"><ErrorBlock message={error} /></div>}
          {success && <div className="full"><SuccessMessage message={success} /></div>}
          <div className="full">
            <button type="button" className="btn btn-primary" disabled={!canPay || paying} onClick={() => setConfirmOpen(true)}>
              {paying ? 'Отправляю...' : 'Оплатить'}
            </button>
          </div>
        </div>
      </PageSection>

      {confirmOpen && (
        <ConfirmDialog
          message={`Перевести ${formatMoney(amountNumber)} (${getCurrencyLabel(currencyType)}) игроку ${recipient.trim()}?`}
          onConfirm={doPay}
          onCancel={() => setConfirmOpen(false)}
        />
      )}
    </div>
  );
}
