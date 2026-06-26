import { useCallback, useEffect, useState } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type {
  BalanceResponse,
  CardLookupResponse,
  CurrencyType,
  OperationResultResponse,
  PagedResponse,
  ProductResponse,
} from '../../../shared/types';
import { CURRENCY_TYPES } from '../../../shared/types';
import {
  extractError,
  formatMoney,
  getCurrencyLabel,
  CARD_STATUS_LABELS,
  ROLE_LABELS,
} from '../../../shared/utils';
import {
  PageSection,
  FormField,
  SelectField,
  ErrorBlock,
  SuccessMessage,
  LoadingBlock,
  ConfirmDialog,
} from '../../../shared/ui';
import RfidScanField from '../../../shared/ui/RfidScanField';

interface Customer {
  lookup: CardLookupResponse;
  balance: BalanceResponse | null;
}

export default function PosTerminalPage() {
  const [uid, setUid] = useState('');
  const [lookupLoading, setLookupLoading] = useState(false);
  const [lookupError, setLookupError] = useState('');
  const [customer, setCustomer] = useState<Customer | null>(null);

  const [currencyType, setCurrencyType] = useState<CurrencyType>('CASHLESS');
  const [amount, setAmount] = useState('');
  const [comment, setComment] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [charging, setCharging] = useState(false);
  const [chargeError, setChargeError] = useState('');
  const [chargeSuccess, setChargeSuccess] = useState('');

  // Чек для игрока (показывается на весь экран после успешной оплаты).
  const [receipt, setReceipt] = useState<{
    title: string;
    player: string;
    lines: { label: string; value: string }[];
  } | null>(null);

  // Product purchase by card
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [productId, setProductId] = useState('');
  const [qty, setQty] = useState('1');
  const [buyConfirm, setBuyConfirm] = useState(false);
  const [buying, setBuying] = useState(false);
  const [buyError, setBuyError] = useState('');
  const [buySuccess, setBuySuccess] = useState('');

  useEffect(() => {
    client
      .get<PagedResponse<ProductResponse>>(ENDPOINTS.products, { params: { status: 'ACTIVE', size: 100 } })
      .then((res) => setProducts(res.data.content || []))
      .catch(() => setProducts([]));
  }, []);

  const selectedProduct = products.find((p) => String(p.id) === productId) || null;
  const qtyNumber = Number(qty);
  const buyTotal = selectedProduct ? selectedProduct.price * (Number.isFinite(qtyNumber) ? qtyNumber : 0) : 0;

  const resetCharge = () => {
    setAmount('');
    setComment('');
    setChargeError('');
    setChargeSuccess('');
    setConfirmOpen(false);
  };

  const loadBalance = async (accountId: number): Promise<BalanceResponse | null> => {
    try {
      const res = await client.get<BalanceResponse>(ENDPOINTS.balance(accountId));
      return res.data;
    } catch {
      return null; // balance is informational; lookup already succeeded
    }
  };

  const handleScan = useCallback(async (code: string) => {
    const trimmed = code.trim();
    if (!trimmed) return;
    setLookupLoading(true);
    setLookupError('');
    setCustomer(null);
    resetCharge();
    try {
      const res = await client.get<CardLookupResponse>(ENDPOINTS.cardLookup, {
        params: { uid: trimmed },
      });
      const lookup = res.data;
      if (!lookup || !lookup.accountId) {
        setLookupError('Карта не найдена или не привязана к игроку.');
        return;
      }
      const balance = await loadBalance(lookup.accountId);
      setCustomer({ lookup, balance });
    } catch (err) {
      setLookupError(extractError(err));
    } finally {
      setLookupLoading(false);
    }
  }, []);

  const cardBlocked = customer?.lookup.cardStatus === 'BLOCKED' || customer?.lookup.cardStatus === 'LOST';
  const accountBlocked = customer?.lookup.accountStatus === 'BLOCKED';

  const amountNumber = Number(amount);
  const canCharge =
    !!customer &&
    !cardBlocked &&
    !accountBlocked &&
    Number.isFinite(amountNumber) &&
    amountNumber > 0;

  const doCharge = async () => {
    if (!customer || !customer.lookup.publicName) return;
    setConfirmOpen(false);
    setCharging(true);
    setChargeError('');
    setChargeSuccess('');
    try {
      const res = await client.post<OperationResultResponse>(ENDPOINTS.withdraw, {
        publicName: customer.lookup.publicName,
        currencyType,
        amount: amountNumber,
        comment: comment || undefined,
      });
      const result = res.data;
      setChargeSuccess(
        `Списано ${formatMoney(amountNumber)} (${getCurrencyLabel(currencyType)}). ` +
          (typeof result.balanceAfter === 'number'
            ? `Новый баланс: ${formatMoney(result.balanceAfter)}.`
            : ''),
      );
      setReceipt({
        title: 'Оплачено',
        player: customer.lookup.publicName || '—',
        lines: [
          { label: 'Списано', value: `${formatMoney(amountNumber)} ${getCurrencyLabel(currencyType)}` },
          ...(typeof result.balanceAfter === 'number'
            ? [{ label: 'Остаток', value: `${formatMoney(result.balanceAfter)} ${getCurrencyLabel(currencyType)}` }]
            : []),
          ...(comment ? [{ label: 'Комментарий', value: comment }] : []),
        ],
      });
      setAmount('');
      setComment('');
      // Refresh balances after a successful charge.
      if (customer.lookup.accountId) {
        const balance = await loadBalance(customer.lookup.accountId);
        setCustomer({ lookup: customer.lookup, balance });
      }
    } catch (err) {
      setChargeError(extractError(err));
    } finally {
      setCharging(false);
    }
  };

  const canBuy =
    !!customer && !cardBlocked && !accountBlocked && !!selectedProduct &&
    Number.isInteger(qtyNumber) && qtyNumber >= 1;

  const doBuy = async () => {
    if (!customer || !selectedProduct) return;
    setBuyConfirm(false);
    setBuying(true);
    setBuyError('');
    setBuySuccess('');
    // Запоминаем детали покупки до сброса полей формы.
    const prodName = selectedProduct.name;
    const q = qtyNumber;
    const total = buyTotal;
    const cur = selectedProduct.currencyType;
    try {
      await client.post(ENDPOINTS.posPurchase, {
        productId: selectedProduct.id,
        quantity: q,
        cardUid: customer.lookup.cardUid || uid,
      });
      setBuySuccess(
        `Куплено: ${prodName} ×${q}. Списано ${formatMoney(total)} (${getCurrencyLabel(cur)}).`,
      );
      setQty('1');
      setProductId('');
      let remainingText: string | undefined;
      if (customer.lookup.accountId) {
        const balance = await loadBalance(customer.lookup.accountId);
        setCustomer({ lookup: customer.lookup, balance });
        const remaining = cur === 'CRYPTO' ? balance?.cryptoAmount : balance?.cashlessAmount;
        if (typeof remaining === 'number') {
          remainingText = `${formatMoney(remaining)} ${getCurrencyLabel(cur)}`;
        }
      }
      setReceipt({
        title: 'Оплачено',
        player: customer.lookup.publicName || '—',
        lines: [
          { label: 'Товар', value: `${prodName} ×${q}` },
          { label: 'Сумма', value: `${formatMoney(total)} ${getCurrencyLabel(cur)}` },
          ...(remainingText ? [{ label: 'Остаток', value: remainingText }] : []),
        ],
      });
    } catch (err) {
      setBuyError(extractError(err));
    } finally {
      setBuying(false);
    }
  };

  return (
    <div className="page">
      <h1 className="page-title">Касса / Терминал</h1>
      <p className="subtext">
        Приложите карту игрока к считывателю, чтобы увидеть владельца и баланс, и при необходимости списать сумму.
      </p>

      <PageSection title="Считать карту">
        <RfidScanField value={uid} onChange={setUid} onScan={handleScan} autoFocus />
        {lookupLoading && <LoadingBlock text="Ищем владельца карты..." />}
        {lookupError && <ErrorBlock message={lookupError} />}
      </PageSection>

      {customer && (
        <>
          <PageSection title="Владелец карты">
            {cardBlocked && (
              <ErrorBlock
                message={`Карта недоступна: ${CARD_STATUS_LABELS[customer.lookup.cardStatus || ''] || customer.lookup.cardStatus}.`}
              />
            )}
            {accountBlocked && <ErrorBlock message="Аккаунт игрока заблокирован." />}
            <div className="stat-grid">
              <div className="stat-box">
                <span>Игрок</span>
                <strong>{customer.lookup.publicName || '—'}</strong>
              </div>
              <div className="stat-box">
                <span>Роль</span>
                <strong>
                  {customer.lookup.accountRole
                    ? ROLE_LABELS[customer.lookup.accountRole] || customer.lookup.accountRole
                    : '—'}
                </strong>
              </div>
              <div className="stat-box">
                <span>UID карты</span>
                <strong className="mono">{customer.lookup.cardUid || uid}</strong>
              </div>
              <div className="stat-box">
                <span>Статус карты</span>
                <strong>
                  {CARD_STATUS_LABELS[customer.lookup.cardStatus || ''] || customer.lookup.cardStatus || '—'}
                </strong>
              </div>
              <div className="stat-box">
                <span>Безналичные</span>
                <strong>{formatMoney(customer.balance?.cashlessAmount)}</strong>
              </div>
              <div className="stat-box">
                <span>Крипта</span>
                <strong>{formatMoney(customer.balance?.cryptoAmount)}</strong>
              </div>
            </div>
          </PageSection>

          <PageSection title="Купить товар по карте">
            <div className="form-grid">
              <FormField label="Товар" id="pos-product">
                <SelectField
                  id="pos-product"
                  value={productId}
                  onChange={setProductId}
                  options={[
                    { value: '', label: '— выберите товар —' },
                    ...products.map((p) => ({
                      value: String(p.id),
                      label: `${p.name} — ${formatMoney(p.price)} ${getCurrencyLabel(p.currencyType)}`,
                    })),
                  ]}
                  disabled={buying}
                />
              </FormField>
              <FormField label="Количество" id="pos-qty">
                <input
                  id="pos-qty"
                  type="number"
                  step="1"
                  min="1"
                  value={qty}
                  onChange={(e) => setQty(e.target.value)}
                  disabled={buying || cardBlocked || accountBlocked}
                />
              </FormField>
              {selectedProduct && (
                <div className="full subtext">
                  К оплате: <strong>{formatMoney(buyTotal)} {getCurrencyLabel(selectedProduct.currencyType)}</strong>
                </div>
              )}
              {buyError && <div className="full"><ErrorBlock message={buyError} /></div>}
              {buySuccess && <div className="full"><SuccessMessage message={buySuccess} /></div>}
              <div className="full">
                <button type="button" className="btn btn-primary" disabled={!canBuy || buying}
                        onClick={() => setBuyConfirm(true)}>
                  {buying ? 'Оплата...' : 'Оплатить картой'}
                </button>
              </div>
            </div>
          </PageSection>

          <PageSection title="Списать средства">
            <div className="form-grid">
              <FormField label="Тип валюты" id="pos-currency">
                <SelectField
                  id="pos-currency"
                  value={currencyType}
                  onChange={(v) => setCurrencyType(v as CurrencyType)}
                  options={CURRENCY_TYPES.map((c) => ({ value: c, label: getCurrencyLabel(c) }))}
                  disabled={charging}
                />
              </FormField>
              <FormField label="Сумма списания" id="pos-amount" required>
                <input
                  id="pos-amount"
                  type="number"
                  step="0.01"
                  min="0"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  disabled={charging || cardBlocked || accountBlocked}
                />
              </FormField>
              <FormField label="Комментарий" id="pos-comment">
                <input
                  id="pos-comment"
                  type="text"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  disabled={charging}
                />
              </FormField>
              {chargeError && (
                <div className="full">
                  <ErrorBlock message={chargeError} />
                </div>
              )}
              {chargeSuccess && (
                <div className="full">
                  <SuccessMessage message={chargeSuccess} />
                </div>
              )}
              <div className="full">
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={!canCharge || charging}
                  onClick={() => setConfirmOpen(true)}
                >
                  {charging ? 'Списываю...' : 'Списать'}
                </button>
              </div>
            </div>
          </PageSection>
        </>
      )}

      {confirmOpen && customer && (
        <ConfirmDialog
          message={`Списать ${formatMoney(amountNumber)} (${getCurrencyLabel(currencyType)}) у игрока ${customer.lookup.publicName}?`}
          onConfirm={doCharge}
          onCancel={() => setConfirmOpen(false)}
        />
      )}

      {buyConfirm && customer && selectedProduct && (
        <ConfirmDialog
          message={`Списать ${formatMoney(buyTotal)} (${getCurrencyLabel(selectedProduct.currencyType)}) с карты игрока ${customer.lookup.publicName} за «${selectedProduct.name}» ×${qtyNumber}?`}
          onConfirm={doBuy}
          onCancel={() => setBuyConfirm(false)}
        />
      )}

      {receipt && (
        <div className="overlay" onClick={() => setReceipt(null)}>
          <div
            className="dialog"
            onClick={(e) => e.stopPropagation()}
            style={{ textAlign: 'center', maxWidth: 560 }}
          >
            <div style={{ fontSize: 64, lineHeight: 1 }}>✅</div>
            <h2 style={{ fontSize: 34, margin: '12px 0 4px' }}>{receipt.title}</h2>
            <div style={{ fontSize: 20, marginBottom: 18 }}>
              Игрок: <strong>{receipt.player}</strong>
            </div>
            <div className="stat-grid">
              {receipt.lines.map((l) => (
                <div className="stat-box" key={l.label}>
                  <span>{l.label}</span>
                  <strong style={{ fontSize: 24 }}>{l.value}</strong>
                </div>
              ))}
            </div>
            <button
              type="button"
              className="btn btn-primary"
              style={{ marginTop: 22, fontSize: 18, padding: '12px 32px' }}
              onClick={() => setReceipt(null)}
              autoFocus
            >
              Готово
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
