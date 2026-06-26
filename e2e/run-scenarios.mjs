#!/usr/bin/env node
// E2E / integration runner for the Cyberpunk LARP backend gateway.
// No dependencies: uses Node 18+ global fetch. See SCENARIOS.md for the catalog.
//
//   node e2e/run-scenarios.mjs
//   BASE=http://localhost:8080/api ADMIN_NAME=admin ADMIN_PASSWORD=admin12345 node e2e/run-scenarios.mjs

const BASE = process.env.BASE || 'http://localhost:8080/api';
const ADMIN_NAME = process.env.ADMIN_NAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin12345';
const TS = Date.now();
const NAME = (s) => `e2e_${s}_${TS}`;

const results = [];
let lastGroup = '';

function group(g) {
  lastGroup = g;
  console.log(`\n=== ${g} ===`);
}

function record(id, desc, ok, detail) {
  results.push({ id, group: lastGroup, desc, ok });
  const tag = ok ? '\x1b[32mPASS\x1b[0m' : '\x1b[31mFAIL\x1b[0m';
  console.log(`  [${tag}] ${id.padEnd(5)} ${desc}${detail ? `  — ${detail}` : ''}`);
}

async function req(method, path, { token, body, headers } = {}) {
  const h = { 'Content-Type': 'application/json', ...(headers || {}) };
  if (token) h.Authorization = `Bearer ${token}`;
  let res, text;
  try {
    res = await fetch(BASE + path, {
      method,
      headers: h,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    text = await res.text();
  } catch (e) {
    return { status: 0, json: null, text: String(e), netErr: true };
  }
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-json */ }
  return { status: res.status, json, text };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Retry an async producer until predicate(res) is true or timeout.
async function until(fn, predicate, { timeoutMs = 60000, intervalMs = 2000, label = '' } = {}) {
  const start = Date.now();
  let last;
  while (Date.now() - start < timeoutMs) {
    last = await fn();
    if (predicate(last)) return last;
    await sleep(intervalMs);
  }
  if (label) console.log(`    (gave up waiting for ${label} after ${timeoutMs}ms; last status ${last?.status})`);
  return last;
}

const is = (res, code) => res.status === code;
const ge400 = (res) => res.status >= 400 && res.status < 600;
const msg = (res) => res.json?.error || res.json?.message || res.text?.slice(0, 80) || '';

async function waitForGateway() {
  group('Health');
  const res = await until(
    () => req('POST', '/auth/admin/login', { body: { publicName: ADMIN_NAME, password: ADMIN_PASSWORD } }),
    (r) => r.status === 200 || r.status === 401, // reachable + responding
    { timeoutMs: 180000, intervalMs: 3000, label: 'gateway' },
  );
  const up = res.status === 200 || res.status === 401;
  record('H1', 'Gateway reachable & responding', up, `status ${res.status}`);
  if (!up) {
    console.error('\nGateway never came up — aborting.');
    printSummary();
    process.exit(2);
  }
}

async function main() {
  await waitForGateway();

  // ---------- shared state ----------
  let adminToken, bankerToken, playerToken;
  const player1 = NAME('player1');
  const player2 = NAME('player2');
  const bankerName = NAME('banker');
  let player1Id, player2Id, bankerId;

  // ========== 1. AUTH ==========
  group('Auth');
  {
    const r = await req('POST', '/auth/admin/login', { body: { publicName: ADMIN_NAME, password: ADMIN_PASSWORD } });
    adminToken = r.json?.token;
    record('A1', 'Admin login (valid)', is(r, 200) && !!r.json?.token && r.json?.role === 'ADMIN', `status ${r.status}, role ${r.json?.role}`);
  }
  record('A2', 'Admin login wrong password',
    is(await req('POST', '/auth/admin/login', { body: { publicName: ADMIN_NAME, password: 'WRONG' } }), 401), '');
  record('A3', 'Admin login unknown user',
    is(await req('POST', '/auth/admin/login', { body: { publicName: 'nobody_xyz', password: 'x' } }), 401), '');
  {
    const r = await req('GET', '/auth/me', { token: adminToken });
    record('A4', '/auth/me with token', is(r, 200) && r.json?.role === 'ADMIN' && r.json?.publicName === ADMIN_NAME, `status ${r.status}`);
  }
  record('A5', '/auth/me no token', is(await req('GET', '/auth/me'), 401), '');
  record('A6', '/auth/me malformed token', is(await req('GET', '/auth/me', { token: 'garbage.token.xx' }), 401), '');
  record('A8', 'Player login invalid PIN', is(await req('POST', '/auth/player/login', { body: { pin: '000000' } }), 401), '');
  // A7 (valid player login) + A9 (logout) run later once a PIN is issued.

  // ========== 2. ACCOUNTS ==========
  group('Accounts');
  {
    const r = await req('POST', '/accounts', { token: adminToken, body: { publicName: player1, role: 'PLAYER', status: 'ACTIVE', characterName: 'Neo' } });
    player1Id = r.json?.id;
    record('AC1', 'Create PLAYER account', is(r, 200) && typeof r.json?.id === 'number', `status ${r.status}, id ${player1Id}`);
  }
  {
    const r = await req('POST', '/accounts', { token: adminToken, body: { publicName: bankerName, role: 'BANKER', status: 'ACTIVE', password: 'banker12345' } });
    bankerId = r.json?.id;
    record('AC2', 'Create BANKER account', is(r, 200) && typeof r.json?.id === 'number', `status ${r.status}`);
  }
  {
    const r = await req('POST', '/accounts', { token: adminToken, body: { publicName: player2, role: 'PLAYER', status: 'ACTIVE' } });
    player2Id = r.json?.id;
    record('AC3', 'Create 2nd PLAYER account', is(r, 200) && typeof r.json?.id === 'number', `status ${r.status}`);
  }
  record('AC4', 'Create account missing publicName',
    ge400(await req('POST', '/accounts', { token: adminToken, body: { role: 'PLAYER', status: 'ACTIVE' } })), '(≥400)');
  record('AC5', 'Create account duplicate publicName',
    ge400(await req('POST', '/accounts', { token: adminToken, body: { publicName: player1, role: 'PLAYER', status: 'ACTIVE' } })), '(≥400)');
  {
    const r = await req('GET', '/accounts?page=0&size=50', { token: adminToken });
    record('AC6', 'List accounts', is(r, 200) && (r.json?.totalElements ?? 0) >= 3, `total ${r.json?.totalElements}`);
  }
  {
    const r = await req('GET', `/accounts?search=${encodeURIComponent(player1)}`, { token: adminToken });
    const found = Array.isArray(r.json?.content) && r.json.content.some((a) => a.publicName === player1);
    record('AC7', 'List accounts with search', is(r, 200) && found, `status ${r.status}`);
  }
  {
    const r = await req('GET', `/accounts/${player1Id}`, { token: adminToken });
    record('AC8', 'Get account by id', is(r, 200) && r.json?.id === player1Id, `status ${r.status}`);
  }
  record('AC9', 'Get account missing id', is(await req('GET', '/accounts/99999999', { token: adminToken }), 404), '');
  {
    const r = await req('GET', '/accounts/me', { token: adminToken });
    record('AC10', 'Get /accounts/me (admin)', is(r, 200) && r.json?.publicName === ADMIN_NAME, `status ${r.status}`);
  }
  {
    const r = await req('PATCH', `/accounts/${player1Id}/status`, { token: adminToken, body: { status: 'BLOCKED' } });
    record('AC11', 'PATCH status → BLOCKED', is(r, 200) && r.json?.status === 'BLOCKED', `status ${r.status}, ${r.json?.status}`);
  }
  {
    const r = await req('PATCH', `/accounts/${player1Id}/status`, { token: adminToken, body: { status: 'ACTIVE' } });
    record('AC12', 'PATCH status → ACTIVE', is(r, 200) && r.json?.status === 'ACTIVE', `status ${r.status}, ${r.json?.status}`);
  }
  record('AC13', 'PATCH status invalid value',
    ge400(await req('PATCH', `/accounts/${player1Id}/status`, { token: adminToken, body: { status: 'FOO' } })), '(≥400)');
  {
    const r1 = await req('PATCH', `/accounts/${player2Id}/role`, { token: adminToken, body: { role: 'BANKER' } });
    const r2 = await req('PATCH', `/accounts/${player2Id}/role`, { token: adminToken, body: { role: 'PLAYER' } });
    record('AC14', 'PATCH role BANKER then PLAYER', is(r1, 200) && is(r2, 200), `status ${r1.status}/${r2.status}`);
  }

  // banker token (banker logs in via /auth/admin/login with its password)
  {
    const r = await until(
      () => req('POST', '/auth/admin/login', { body: { publicName: bankerName, password: 'banker12345' } }),
      (x) => x.status === 200, { timeoutMs: 60000, intervalMs: 2500, label: 'banker login propagation' },
    );
    bankerToken = r.json?.token;
    record('AC2b', 'Banker can log in (admin/login)', is(r, 200) && r.json?.role === 'BANKER', `status ${r.status}, role ${r.json?.role}`);
  }
  record('AC15', 'Create account as BANKER → forbidden',
    is(await req('POST', '/accounts', { token: bankerToken, body: { publicName: NAME('x'), role: 'PLAYER', status: 'ACTIVE' } }), 403), '');

  // ========== 3. PINS ==========
  group('Pins');
  let issuedPin = String(100000 + (TS % 800000)); // 6-digit-ish
  {
    const r = await req('POST', '/pins', { token: adminToken, body: { publicName: player1, rawPin: issuedPin, durationMinutes: 120, comment: 'e2e' } });
    record('P1', 'Create PIN for player1', is(r, 200), `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  record('P2', 'Create PIN duration 0',
    is(await req('POST', '/pins', { token: adminToken, body: { publicName: player1, rawPin: '111111', durationMinutes: 0 } }), 400), '');
  record('P3', 'Create PIN duration 5000',
    is(await req('POST', '/pins', { token: adminToken, body: { publicName: player1, rawPin: '111111', durationMinutes: 5000 } }), 400), '');
  record('P4', 'Create PIN missing rawPin',
    is(await req('POST', '/pins', { token: adminToken, body: { publicName: player1, durationMinutes: 60 } }), 400), '');
  {
    const r = await req('GET', `/pins/by-account/${player1Id}`, { token: adminToken });
    const arr = Array.isArray(r.json) ? r.json : r.json?.content;
    record('P5', 'List pins by account', is(r, 200) && Array.isArray(arr) && arr.length >= 1, `status ${r.status}, count ${arr?.length}`);
  }
  // P6/P7: issue a throwaway pin (on player2 — player1 already has an active pin and the
  // backend allows only ONE active pin per account), revoke it, login should then fail.
  {
    const throwPin = String(200000 + (TS % 700000));
    const created = await until(
      () => req('POST', '/pins', { token: adminToken, body: { publicName: player2, rawPin: throwPin, durationMinutes: 60 } }),
      (x) => x.status === 200 && typeof x.json?.id === 'number',
      { timeoutMs: 45000, intervalMs: 2500, label: 'pin create for player2 (propagation)' },
    );
    const pinId = created.json?.id;
    const rev = await req('POST', `/pins/${pinId}/revoke`, { token: adminToken });
    record('P6', 'Revoke pin', is(rev, 200) && rev.json?.status === 'REVOKED', `status ${rev.status}, ${rev.json?.status}`);
    const login = await req('POST', '/auth/player/login', { body: { pin: throwPin } });
    record('P7', 'Player login with revoked pin', is(login, 401), `status ${login.status}`);
  }
  record('P8', 'Create pin as PLAYER → forbidden later (checked in player section)', true, 'deferred → PS section');

  // ---- A7: player login with the valid issued PIN (retry for propagation) ----
  group('Auth (player)');
  {
    const r = await until(
      () => req('POST', '/auth/player/login', { body: { pin: issuedPin } }),
      (x) => x.status === 200, { timeoutMs: 60000, intervalMs: 2500, label: 'player login propagation' },
    );
    playerToken = r.json?.token;
    record('A7', 'Player login (valid PIN)', is(r, 200) && r.json?.role === 'PLAYER', `status ${r.status}, role ${r.json?.role}`);
  }

  // ========== 4. BANKING (admin/banker context) ==========
  group('Banking');
  // B1 deposit — retried until the account snapshot propagates to banking-service.
  {
    const r = await until(
      () => req('POST', '/banking/deposit', { token: adminToken, body: { publicName: player1, currencyType: 'CASHLESS', amount: 1000, comment: 'init' } }),
      (x) => x.status === 200 || x.status === 400 && /not active/i.test(msg(x)) === false && /not found/i.test(msg(x)) === false,
      { timeoutMs: 90000, intervalMs: 3000, label: 'banking snapshot for player1' },
    );
    record('B1', 'Deposit CASHLESS 1000 → player1', is(r, 200) && Number(r.json?.balanceAfter) === 1000, `status ${r.status}, after ${r.json?.balanceAfter} ${is(r,200)?'':msg(r)}`);
  }
  {
    const r = await req('POST', '/banking/deposit', { token: adminToken, body: { publicName: player1, currencyType: 'CRYPTO', amount: 50, comment: '' } });
    record('B2', 'Deposit CRYPTO 50 → player1', is(r, 200) && Number(r.json?.balanceAfter) === 50, `status ${r.status}, after ${r.json?.balanceAfter}`);
  }
  record('B3', 'Deposit amount 0',
    is(await req('POST', '/banking/deposit', { token: adminToken, body: { publicName: player1, currencyType: 'CASHLESS', amount: 0 } }), 400), '');
  record('B4', 'Deposit missing publicName',
    ge400(await req('POST', '/banking/deposit', { token: adminToken, body: { currencyType: 'CASHLESS', amount: 10 } })), '(≥400)');
  record('B5', 'Deposit invalid currency',
    is(await req('POST', '/banking/deposit', { token: adminToken, body: { publicName: player1, currencyType: 'GOLD', amount: 10 } }), 400), '');
  {
    const r = await req('POST', '/banking/withdraw', { token: adminToken, body: { publicName: player1, currencyType: 'CASHLESS', amount: 400, comment: '' } });
    record('B6', 'Withdraw 400 (within balance)', is(r, 200) && Number(r.json?.balanceAfter) === 600, `status ${r.status}, after ${r.json?.balanceAfter}`);
  }
  record('B7', 'Withdraw exceeding balance',
    is(await req('POST', '/banking/withdraw', { token: adminToken, body: { publicName: player1, currencyType: 'CASHLESS', amount: 1000000000 } }), 400), '');
  {
    // Adjustment was removed (deposit/withdraw cover increase/decrease) — endpoint no longer exists.
    const r = await req('POST', '/banking/adjust', { token: adminToken, body: { publicName: player1, currencyType: 'CASHLESS', amount: 100, direction: 'PLUS' } });
    record('B8', 'Adjust endpoint removed → 404', is(r, 404), `status ${r.status}`);
  }
  {
    // player1 CASHLESS: 1000 deposit − 400 withdraw = 600; this transfer of 300 leaves 300.
    const r = await req('POST', '/banking/transfer', { token: adminToken, body: { fromPublicName: player1, toPublicName: player2, currencyType: 'CASHLESS', amount: 300, comment: '' } });
    record('B11', 'Transfer player1→player2 300', is(r, 200), `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  record('B12', 'Transfer to same account',
    is(await req('POST', '/banking/transfer', { token: adminToken, body: { fromPublicName: player1, toPublicName: player1, currencyType: 'CASHLESS', amount: 10 } }), 400), '');
  record('B13', 'Transfer exceeding balance',
    is(await req('POST', '/banking/transfer', { token: adminToken, body: { fromPublicName: player1, toPublicName: player2, currencyType: 'CASHLESS', amount: 1000000000 } }), 400), '');
  {
    const r = await req('GET', `/banking/balance/${player1Id}`, { token: adminToken });
    record('B14', 'Get balance/{player1Id}', is(r, 200) && Number(r.json?.cashlessAmount) === 300, `status ${r.status}, cashless ${r.json?.cashlessAmount}`);
  }
  record('B15', 'Get balance/{missingId}', is(await req('GET', '/banking/balance/99999999', { token: adminToken }), 404), '');
  {
    const r = await req('GET', `/banking/transactions/${player1Id}`, { token: adminToken });
    const arr = r.json?.content;
    record('B16', 'Get transactions/{player1Id}', is(r, 200) && Array.isArray(arr) && arr.length > 0, `status ${r.status}, count ${arr?.length}`);
  }
  // B17/B19: deposit to player2 then reverse it; reversing again → already-reversed.
  {
    const dep = await req('POST', '/banking/deposit', { token: adminToken, body: { publicName: player2, currencyType: 'CASHLESS', amount: 100, comment: 'rev-target' } });
    const txId = dep.json?.transactionId;
    const r = await req('POST', '/banking/reverse', { token: adminToken, body: { transactionId: txId, comment: 'e2e reverse' } });
    record('B17', 'Reverse a deposit tx', is(r, 200), `status ${r.status} ${is(r,200)?'':msg(r)}`);
    const again = await req('POST', '/banking/reverse', { token: adminToken, body: { transactionId: txId, comment: 'again' } });
    record('B19', 'Reverse already-reversed tx → 400', is(again, 400), `status ${again.status} ${msg(again)}`);
  }
  record('B18', 'Reverse missing tx',
    is(await req('POST', '/banking/reverse', { token: adminToken, body: { transactionId: 99999999, comment: 'x' } }), 404), '');
  record('B20', 'Deposit as PLAYER token → 400 (privilege)',
    is(await req('POST', '/banking/deposit', { token: playerToken, body: { publicName: player2, currencyType: 'CASHLESS', amount: 10 } }), 400), '');

  // ========== 4b. CARDS ==========
  group('Cards');
  const uidA = `E2EUID-A-${TS}`;
  const uidB = `E2EUID-B-${TS}`;
  const uidC = `E2EUID-C-${TS}`;
  let cardId1;
  {
    const r = await until(
      () => req('POST', '/cards', { token: adminToken, body: { publicName: player1, cardUid: uidA, notes: 'e2e' } }),
      (x) => x.status === 200, { timeoutMs: 60000, intervalMs: 3000, label: 'card snapshot for player1' },
    );
    cardId1 = r.json?.id;
    record('C1', 'Issue card for player1', is(r, 200) && r.json?.status === 'ISSUED' && r.json?.cardUid === uidA, `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  record('C2', 'Issue 2nd active card same account → 400',
    is(await req('POST', '/cards', { token: adminToken, body: { publicName: player1, cardUid: uidB } }), 400), '');
  record('C3', 'Issue card with duplicate UID → 400',
    is(await req('POST', '/cards', { token: adminToken, body: { publicName: player2, cardUid: uidA } }), 400), '');
  {
    const r = await req('GET', `/cards/by-account/${player1Id}`, { token: adminToken });
    const arr = Array.isArray(r.json) ? r.json : r.json?.content;
    record('C4', 'List cards by account', is(r, 200) && Array.isArray(arr) && arr.some((c) => c.cardUid === uidA), `status ${r.status}, count ${arr?.length}`);
  }
  {
    const r = await req('GET', `/cards/${cardId1}`, { token: adminToken });
    record('C5', 'Get card by id', is(r, 200) && r.json?.id === cardId1, `status ${r.status}`);
  }
  {
    const r = await req('GET', `/cards/by-uid?uid=${encodeURIComponent(uidA)}`, { token: adminToken });
    record('C6', 'Lookup by UID → owner', is(r, 200) && r.json?.publicName === player1, `status ${r.status}, owner ${r.json?.publicName}`);
  }
  record('C7', 'Lookup unknown UID → 404',
    is(await req('GET', `/cards/by-uid?uid=${encodeURIComponent('nope-' + TS)}`, { token: adminToken }), 404), '');
  {
    const r = await req('POST', `/cards/${cardId1}/block`, { token: adminToken, body: { reason: 'e2e block' } });
    record('C8', 'Block card', is(r, 200) && r.json?.status === 'BLOCKED', `status ${r.status}, ${r.json?.status}`);
  }
  record('C9', 'Block already-blocked card → 400',
    is(await req('POST', `/cards/${cardId1}/block`, { token: adminToken, body: {} }), 400), '');
  {
    const issue = await req('POST', '/cards', { token: adminToken, body: { publicName: player1, cardUid: uidB } });
    const cardId2 = issue.json?.id;
    const lost = await req('POST', `/cards/${cardId2}/lost`, { token: adminToken, body: { reason: 'dropped' } });
    record('C10', 'Issue new + mark LOST', is(issue, 200) && is(lost, 200) && lost.json?.status === 'LOST', `status ${issue.status}/${lost.status}`);
    const rep = await req('POST', `/cards/${cardId2}/replace`, { token: adminToken, body: { newCardUid: uidC } });
    record('C11', 'Replace LOST card → new ISSUED', is(rep, 200) && rep.json?.status === 'ISSUED' && rep.json?.cardUid === uidC, `status ${rep.status} ${is(rep,200)?'':msg(rep)}`);
  }
  record('C12', 'Issue card as PLAYER token → 403',
    is(await req('POST', '/cards', { token: playerToken, body: { publicName: player2, cardUid: `x-${TS}` } }), 403), '');
  record('C13', 'Get missing card → 404',
    is(await req('GET', '/cards/99999999', { token: adminToken }), 404), '');

  // ========== 4c. TERMINALS ==========
  group('Terminals');
  const termName = `E2E-TERM-${TS}`;
  let termId;
  {
    const r = await req('POST', '/terminals', { token: adminToken, body: { name: termName, terminalType: 'POS', location: 'Зона A', ipAddress: '10.0.0.5' } });
    termId = r.json?.id;
    record('T1', 'Register terminal', is(r, 200) && r.json?.status === 'ACTIVE' && r.json?.name === termName, `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  record('T2', 'Register duplicate name → 400',
    is(await req('POST', '/terminals', { token: adminToken, body: { name: termName } }), 400), '');
  record('T3', 'Register missing name → 400',
    is(await req('POST', '/terminals', { token: adminToken, body: { location: 'x' } }), 400), '');
  {
    const r = await req('GET', '/terminals?page=0&size=50', { token: adminToken });
    record('T4', 'List terminals', is(r, 200) && (r.json?.totalElements ?? 0) >= 1, `status ${r.status}, total ${r.json?.totalElements}`);
  }
  {
    const r = await req('GET', '/terminals?status=ACTIVE&size=50', { token: adminToken });
    const arr = r.json?.content || [];
    record('T5', 'List filter by status=ACTIVE', is(r, 200) && arr.every((t) => t.status === 'ACTIVE'), `status ${r.status}, count ${arr.length}`);
  }
  {
    const r = await req('GET', `/terminals/${termId}`, { token: adminToken });
    record('T6', 'Get terminal by id', is(r, 200) && r.json?.id === termId, `status ${r.status}`);
  }
  {
    const r = await req('GET', `/terminals/by-name/${encodeURIComponent(termName)}`, { token: adminToken });
    record('T7', 'Get terminal by name', is(r, 200) && r.json?.name === termName, `status ${r.status}`);
  }
  record('T8', 'Get missing terminal → 404', is(await req('GET', '/terminals/99999999', { token: adminToken }), 404), '');
  {
    const r = await req('POST', `/terminals/${termId}/status`, { token: adminToken, body: { status: 'MAINTENANCE' } });
    record('T9', 'Change status → MAINTENANCE', is(r, 200) && r.json?.status === 'MAINTENANCE', `status ${r.status}, ${r.json?.status}`);
  }
  {
    const r = await req('PATCH', `/terminals/${termId}`, { token: adminToken, body: { location: 'Зона B', notes: 'moved' } });
    record('T10', 'Update terminal fields', is(r, 200) && r.json?.location === 'Зона B', `status ${r.status}, loc ${r.json?.location}`);
  }
  record('T11', 'Register as BANKER → 403 (admin-only)',
    is(await req('POST', '/terminals', { token: bankerToken, body: { name: `x-${TS}` } }), 403), '');
  record('T12', 'Register as PLAYER → 403',
    is(await req('POST', '/terminals', { token: playerToken, body: { name: `y-${TS}` } }), 403), '');
  record('T13', 'List as PLAYER → 403', is(await req('GET', '/terminals', { token: playerToken }), 403), '');
  record('T14', 'List as BANKER → 200 (view allowed)', is(await req('GET', '/terminals', { token: bankerToken }), 200), '');

  // ========== 4d. MARKETPLACE ==========
  // Runs while the player session is still alive (before Sessions/S3 terminates it).
  // player1 funds at this point: CASHLESS 300, CRYPTO 50.
  group('Marketplace');
  const skuA = `SKU-A-${TS}`;
  const skuB = `SKU-B-${TS}`;
  const skuC = `SKU-C-${TS}`;
  let prodAId, prodBId, prodCId, paidOrderId;
  {
    // First marketplace command — retry until the gateway reply consumer group is ready.
    const r = await until(
      () => req('POST', '/marketplace/products', { token: adminToken, body: { sku: skuA, name: 'Стимпак', price: 100, currencyType: 'CASHLESS', stockQuantity: 5, category: 'Медицина' } }),
      (x) => x.status === 200, { timeoutMs: 60000, intervalMs: 3000, label: 'marketplace ready' },
    );
    prodAId = r.json?.id;
    record('M1', 'Create product (admin)', is(r, 200) && r.json?.status === 'ACTIVE' && typeof r.json?.id === 'number', `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  {
    const r = await req('POST', '/marketplace/products', { token: adminToken, body: { name: 'Auto SKU item', price: 10, currencyType: 'CASHLESS' } });
    record('M2', 'Create product without sku → auto-assigned', is(r, 200) && typeof r.json?.sku === 'string' && r.json.sku.length > 0, `status ${r.status}, sku ${r.json?.sku}`);
  }
  record('M3', 'Create product duplicate sku → 400',
    is(await req('POST', '/marketplace/products', { token: adminToken, body: { sku: skuA, name: 'dup', price: 10, currencyType: 'CASHLESS' } }), 400), '');
  record('M4', 'Create product invalid currency → 400',
    is(await req('POST', '/marketplace/products', { token: adminToken, body: { sku: `${skuA}-x`, name: 'x', price: 10, currencyType: 'GOLD' } }), 400), '');
  record('M5', 'Create product as PLAYER → 403',
    is(await req('POST', '/marketplace/products', { token: playerToken, body: { sku: `${skuA}-p`, name: 'x', price: 10, currencyType: 'CASHLESS' } }), 403), '');
  {
    const r = await req('POST', '/marketplace/products', { token: bankerToken, body: { sku: skuB, name: 'Крипточип', price: 25, currencyType: 'CRYPTO', category: 'Импланты' } });
    prodBId = r.json?.id;
    record('M6', 'Create product as BANKER (unlimited stock)', is(r, 200) && r.json?.stockQuantity == null, `status ${r.status}, stock ${r.json?.stockQuantity} ${is(r,200)?'':msg(r)}`);
  }
  {
    const r = await req('GET', '/marketplace/products?page=0&size=50', { token: adminToken });
    record('M7', 'List products (admin)', is(r, 200) && (r.json?.totalElements ?? 0) >= 2, `total ${r.json?.totalElements}`);
  }
  {
    const r = await req('GET', `/marketplace/products/${prodAId}`, { token: adminToken });
    record('M8', 'Get product by id', is(r, 200) && r.json?.id === prodAId, `status ${r.status}`);
  }
  record('M9', 'Get missing product → 404', is(await req('GET', '/marketplace/products/99999999', { token: adminToken }), 404), '');
  {
    const r = await req('GET', '/marketplace/products?size=50', { token: playerToken });
    const arr = r.json?.content || [];
    record('M10', 'Player list shows only ACTIVE', is(r, 200) && arr.length > 0 && arr.every((p) => p.status === 'ACTIVE'), `status ${r.status}, count ${arr.length}`);
  }
  {
    const r = await req('POST', `/marketplace/products/${prodAId}/stock`, { token: adminToken, body: { delta: 10 } });
    record('M11', 'Adjust stock +10 (5→15)', is(r, 200) && r.json?.stockQuantity === 15, `status ${r.status}, stock ${r.json?.stockQuantity}`);
  }
  {
    const r = await req('POST', `/marketplace/products/${prodBId}/status`, { token: adminToken, body: { status: 'HIDDEN' } });
    record('M12', 'Hide product (→ HIDDEN)', is(r, 200) && r.json?.status === 'HIDDEN', `status ${r.status}, ${r.json?.status}`);
  }
  record('M13', 'Player get hidden product → 404',
    is(await req('GET', `/marketplace/products/${prodBId}`, { token: playerToken }), 404), '');
  record('M14', 'Player buy hidden product → 400',
    is(await req('POST', '/marketplace/orders', { token: playerToken, body: { productId: prodBId, quantity: 1 } }), 400), '');
  {
    const r = await req('POST', '/marketplace/orders', { token: playerToken, body: { productId: prodAId, quantity: 2 } });
    paidOrderId = r.json?.id;
    record('M15', 'Player buys ×2 → PAID (total 200)', is(r, 200) && r.json?.status === 'PAID' && Number(r.json?.totalPrice) === 200, `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  {
    const r = await req('GET', '/banking/balance/me', { token: playerToken });
    record('M16', 'Buyer CASHLESS debited (300→100)', is(r, 200) && Number(r.json?.cashlessAmount) === 100, `cashless ${r.json?.cashlessAmount}`);
  }
  {
    const r = await req('GET', '/marketplace/orders/me', { token: playerToken });
    const arr = r.json?.content || [];
    record('M17', 'Player my-orders contains the order', is(r, 200) && arr.some((o) => o.id === paidOrderId && o.status === 'PAID'), `status ${r.status}, count ${arr.length}`);
  }
  record('M18', 'Player buy beyond funds → 400 (insufficient funds)',
    is(await req('POST', '/marketplace/orders', { token: playerToken, body: { productId: prodAId, quantity: 2 } }), 400), '');
  {
    // M15 took 2 (15→13); M18 reserved 2 then compensated (cancel restocks) → still 13.
    const r = await req('GET', `/marketplace/products/${prodAId}`, { token: adminToken });
    record('M19', 'Stock restored after failed purchase (=13)', is(r, 200) && r.json?.stockQuantity === 13, `stock ${r.json?.stockQuantity}`);
  }
  {
    const create = await req('POST', '/marketplace/products', { token: adminToken, body: { sku: skuC, name: 'Лимитка', price: 10, currencyType: 'CASHLESS', stockQuantity: 1 } });
    prodCId = create.json?.id;
    const r = await req('POST', '/marketplace/orders', { token: playerToken, body: { productId: prodCId, quantity: 5 } });
    record('M20', 'Buy beyond stock → 400 (insufficient stock)', is(r, 400), `status ${r.status}`);
  }
  {
    const r = await req('GET', '/marketplace/orders?page=0&size=50', { token: adminToken });
    record('M21', 'List all orders (admin)', is(r, 200) && (r.json?.totalElements ?? 0) >= 1, `total ${r.json?.totalElements}`);
  }
  {
    const r = await req('GET', `/marketplace/orders/${paidOrderId}`, { token: adminToken });
    record('M22', 'Get order by id (admin)', is(r, 200) && r.json?.id === paidOrderId, `status ${r.status}`);
  }
  record('M23', 'Player cancel order → 403',
    is(await req('POST', `/marketplace/orders/${paidOrderId}/cancel`, { token: playerToken }), 403), '');
  {
    const r = await req('POST', `/marketplace/orders/${paidOrderId}/cancel`, { token: adminToken });
    record('M24', 'Admin cancels PAID order (refund) → CANCELLED', is(r, 200) && r.json?.status === 'CANCELLED', `status ${r.status}, ${r.json?.status} ${is(r,200)?'':msg(r)}`);
  }
  {
    const r = await req('GET', '/banking/balance/me', { token: playerToken });
    record('M25', 'Buyer refunded (100→300)', is(r, 200) && Number(r.json?.cashlessAmount) === 300, `cashless ${r.json?.cashlessAmount}`);
  }

  // ========== 4e. NEWS ==========
  // Runs while the player session is still alive.
  group('News');
  let draftId, pubId;
  {
    const r = await until(
      () => req('POST', '/news', { token: adminToken, body: { title: NAME('news_draft'), body: 'Тело черновика', category: 'e2e' } }),
      (x) => x.status === 200, { timeoutMs: 60000, intervalMs: 3000, label: 'news ready' },
    );
    draftId = r.json?.id;
    record('N1', 'Create draft (admin)', is(r, 200) && r.json?.status === 'DRAFT' && typeof r.json?.id === 'number', `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  record('N2', 'Create missing title → ≥400',
    ge400(await req('POST', '/news', { token: adminToken, body: { body: 'x' } })), '(≥400)');
  record('N3', 'Create missing body → ≥400',
    ge400(await req('POST', '/news', { token: adminToken, body: { title: 'x' } })), '(≥400)');
  record('N4', 'Create as PLAYER → 403',
    is(await req('POST', '/news', { token: playerToken, body: { title: 'x', body: 'y' } }), 403), '');
  {
    const r = await req('POST', '/news', { token: adminToken, body: { title: NAME('news_pub'), body: 'Опубликованный текст', category: 'e2e', status: 'PUBLISHED' } });
    pubId = r.json?.id;
    record('N5', 'Create published (admin)', is(r, 200) && r.json?.status === 'PUBLISHED' && !!r.json?.publishedAt, `status ${r.status} ${is(r,200)?'':msg(r)}`);
  }
  record('N6', 'Create as BANKER → 200',
    is(await req('POST', '/news', { token: bankerToken, body: { title: NAME('news_bk'), body: 'b' } }), 200), '');
  {
    const r = await req('GET', '/news?page=0&size=50', { token: adminToken });
    record('N7', 'List all (admin)', is(r, 200) && (r.json?.totalElements ?? 0) >= 2, `total ${r.json?.totalElements}`);
  }
  {
    const r = await req('GET', `/news/${pubId}`, { token: adminToken });
    record('N8', 'Get post by id (admin)', is(r, 200) && r.json?.id === pubId, `status ${r.status}`);
  }
  record('N9', 'Get missing post → 404', is(await req('GET', '/news/99999999', { token: adminToken }), 404), '');
  {
    const r = await req('GET', '/news?size=50', { token: playerToken });
    const arr = r.json?.content || [];
    const noDraft = !arr.some((n) => n.id === draftId);
    const allPub = arr.every((n) => n.status === 'PUBLISHED');
    record('N10', 'Player feed = published only (no draft)', is(r, 200) && allPub && noDraft, `status ${r.status}, count ${arr.length}`);
  }
  record('N11', 'Player get draft → 404', is(await req('GET', `/news/${draftId}`, { token: playerToken }), 404), '');
  record('N12', 'Player get published → 200', is(await req('GET', `/news/${pubId}`, { token: playerToken }), 200), '');
  {
    const r = await req('POST', `/news/${draftId}/status`, { token: adminToken, body: { status: 'PUBLISHED' } });
    record('N13', 'Publish the draft', is(r, 200) && r.json?.status === 'PUBLISHED', `status ${r.status}, ${r.json?.status}`);
  }
  {
    const r = await req('GET', '/news?size=50', { token: playerToken });
    const arr = r.json?.content || [];
    record('N14', 'Player feed now contains it', is(r, 200) && arr.some((n) => n.id === draftId), `count ${arr.length}`);
  }
  {
    const pin = await req('POST', `/news/${pubId}/pin`, { token: adminToken, body: { pinned: true } });
    const feed = await req('GET', '/news?size=50', { token: playerToken });
    const arr = feed.json?.content || [];
    const target = arr.find((n) => n.id === pubId);
    record('N15', 'Pin post → pinned & sorts first', is(pin, 200) && pin.json?.pinned === true && target?.pinned === true && arr[0]?.pinned === true, `pinned ${pin.json?.pinned}, first ${arr[0]?.pinned}`);
  }
  {
    const r = await req('POST', `/news/${pubId}/pin`, { token: adminToken, body: { pinned: false } });
    record('N16', 'Unpin post', is(r, 200) && r.json?.pinned === false, `pinned ${r.json?.pinned}`);
  }
  {
    const newTitle = NAME('news_upd');
    const r = await req('PATCH', `/news/${pubId}`, { token: adminToken, body: { title: newTitle, body: 'updated' } });
    record('N17', 'Update post', is(r, 200) && r.json?.title === newTitle, `status ${r.status}`);
  }
  {
    const arch = await req('POST', `/news/${pubId}/status`, { token: adminToken, body: { status: 'ARCHIVED' } });
    const pget = await req('GET', `/news/${pubId}`, { token: playerToken });
    record('N18', 'Archive → player no longer sees it', is(arch, 200) && arch.json?.status === 'ARCHIVED' && is(pget, 404), `arch ${arch.json?.status}, playerGet ${pget.status}`);
  }
  record('N19', 'Set pinned as PLAYER → 403',
    is(await req('POST', `/news/${draftId}/pin`, { token: playerToken, body: { pinned: true } }), 403), '');
  {
    const r = await req('GET', '/news?status=ARCHIVED&size=50', { token: adminToken });
    const arr = r.json?.content || [];
    record('N20', 'Admin filter status=ARCHIVED', is(r, 200) && arr.length > 0 && arr.every((n) => n.status === 'ARCHIVED') && arr.some((n) => n.id === pubId), `count ${arr.length}`);
  }

  // ========== 5. PLAYER SELF-SERVICE ==========
  group('Player self-service');
  record('PS1', 'Player /banking/balance/me', is(await req('GET', '/banking/balance/me', { token: playerToken }), 200), '');
  {
    const r = await req('GET', '/banking/transactions/me', { token: playerToken });
    record('PS2', 'Player /banking/transactions/me', is(r, 200), `status ${r.status}`);
  }
  record('PS3', 'Player /sessions/active → 403', is(await req('GET', '/sessions/active', { token: playerToken }), 403), '');
  record('P8b', 'Create pin as PLAYER → 403',
    is(await req('POST', '/pins', { token: playerToken, body: { publicName: player1, rawPin: '999999', durationMinutes: 60 } }), 403), '');
  record('AC16', 'List accounts as PLAYER → 403', is(await req('GET', '/accounts', { token: playerToken }), 403), '');

  // ========== 6. AUDIT ==========
  // Runs before Sessions: S3 terminates player1's session, which would 401 the player-token check.
  group('Audit');
  let firstLog;
  {
    // Logs are ingested asynchronously from every service's event topics — retry until some land.
    const r = await until(
      () => req('GET', '/audit/logs?page=0&size=25', { token: adminToken }),
      (x) => x.status === 200 && (x.json?.totalElements ?? 0) > 0,
      { timeoutMs: 60000, intervalMs: 3000, label: 'audit log ingestion' },
    );
    firstLog = r.json?.content?.[0];
    record('AU1', 'List audit logs', is(r, 200) && (r.json?.totalElements ?? 0) > 0, `status ${r.status}, total ${r.json?.totalElements}`);
  }
  record('AU2', 'Filter logs by search', is(await req('GET', `/audit/logs?search=${encodeURIComponent(ADMIN_NAME)}&size=10`, { token: adminToken }), 200), '');
  if (firstLog?.id) {
    const r = await req('GET', `/audit/logs/${firstLog.id}`, { token: adminToken });
    record('AU3', 'Get audit log by id', is(r, 200) && r.json?.id === firstLog.id, `status ${r.status}`);
  } else {
    record('AU3', 'Get audit log by id', false, 'no log id available');
  }
  record('AU4', 'Get missing audit log → 404', is(await req('GET', '/audit/logs/99999999', { token: adminToken }), 404), '');
  record('AU5', 'List audit logs as PLAYER → 403', is(await req('GET', '/audit/logs', { token: playerToken }), 403), '');
  {
    const et = firstLog?.eventType;
    if (et) {
      const r = await req('GET', `/audit/logs?eventType=${encodeURIComponent(et)}&size=25`, { token: adminToken });
      const arr = r.json?.content || [];
      record('AU6', 'Filter logs by eventType', is(r, 200) && arr.every((l) => l.eventType === et), `status ${r.status}, type ${et}, count ${arr.length}`);
    } else {
      record('AU6', 'Filter logs by eventType', is(await req('GET', '/audit/logs?eventType=admin.login_success&size=25', { token: adminToken }), 200), '');
    }
  }

  // ========== 6b. SESSIONS ==========
  group('Sessions');
  record('S1', '/sessions/me (admin)', is(await req('GET', '/sessions/me', { token: adminToken }), 200), '');
  let aSessionId;
  {
    const r = await req('GET', '/sessions/active', { token: adminToken });
    const arr = r.json?.content;
    aSessionId = Array.isArray(arr) && arr.find((s) => String(s.accountId) === String(player1Id))?.id;
    record('S2', '/sessions/active (admin)', is(r, 200) && Array.isArray(arr), `status ${r.status}, count ${arr?.length}`);
  }
  if (aSessionId) {
    record('S3', 'Terminate a session', is(await req('POST', `/sessions/${aSessionId}/terminate`, { token: adminToken }), 200), `sessionId ${aSessionId}`);
  } else {
    record('S3', 'Terminate a session', false, 'no player session found to terminate');
  }

  // ---- A9: logout admin (do last) ----
  group('Auth (logout)');
  record('A9', 'Logout (admin)', is(await req('POST', '/auth/logout', { token: adminToken }), 200), '');

  printSummary();
  process.exit(results.some((r) => !r.ok) ? 1 : 0);
}

function printSummary() {
  const pass = results.filter((r) => r.ok).length;
  const fail = results.length - pass;
  console.log('\n========================================');
  console.log(`  TOTAL ${results.length}   \x1b[32mPASS ${pass}\x1b[0m   ${fail ? '\x1b[31m' : ''}FAIL ${fail}\x1b[0m`);
  if (fail) {
    console.log('  Failed:');
    for (const r of results.filter((x) => !x.ok)) console.log(`    - ${r.id} (${r.group}) ${r.desc}`);
  }
  console.log('========================================');
}

main().catch((e) => {
  console.error('Runner crashed:', e);
  printSummary();
  process.exit(3);
});
