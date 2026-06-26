#!/usr/bin/env node
// Cyberpunk demo seed for the LARP stack (Night City flavour).
// Run against a FRESH stack (docker compose down -v && up -d) for a clean dataset.
//   node e2e/seed.mjs
// Env: BASE (default http://localhost:8080/api), ADMIN_NAME/ADMIN_PASSWORD.

const BASE = process.env.BASE || 'http://localhost:8080/api';
const ADMIN_NAME = process.env.ADMIN_NAME || 'admin';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin12345';

async function req(method, path, { token, body } = {}) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = `Bearer ${token}`;
  let res, text;
  try {
    res = await fetch(BASE + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
    text = await res.text();
  } catch (e) { return { status: 0, json: null, text: String(e) }; }
  let json = null; try { json = text ? JSON.parse(text) : null; } catch { /* */ }
  return { status: res.status, json, text };
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function until(fn, ok, { tries = 30, gap = 2000, label = '' } = {}) {
  let last;
  for (let i = 0; i < tries; i++) { last = await fn(); if (ok(last)) return last; await sleep(gap); }
  console.warn(`  ! gave up: ${label} (last ${last?.status})`);
  return last;
}
const pin6 = () => String(Math.floor(100000 + Math.random() * 899999));
const rnd = (a, b) => Math.floor(a + Math.random() * (b - a));

const PLAYERS = [
  ['V', 'Ви'], ['Johnny', 'Джонни Сильверхенд'], ['Judy', 'Джуди Альварес'],
  ['Panam', 'Панам Палмер'], ['Jackie', 'Джеки Уэллс'], ['Takemura', 'Горо Такемура'],
  ['Kerry', 'Керри Евродин'], ['Misty', 'Мисти Ольшевски'], ['River', 'Ривер Уорд'],
  ['Lucy', 'Люси Кушинада'],
];

const TERMINALS = [
  ['Банкомат — Кабуки', 'Банкомат', 'Уотсон, Кабуки'],
  ['POS — Afterlife', 'Касса (POS)', 'Уотсон, бар «Афтерлайф»'],
  ['Турникет — Корпо-плаза', 'Турникет / Ворота', 'Сити-центр, Корпо-плаза'],
  ['Киоск — Меган-билдинг', 'Платёжный киоск', 'Уотсон, Меган-билдинг'],
  ['Сканер — клиника Виктора', 'Сканер карт', 'Уотсон, клиника рипердока'],
];

const PRODUCTS = [
  ['Мантис-клинки', 'Выдвижные моноклинки из предплечий.', 25000, 'CASHLESS', 3, 'Импланты'],
  ['Гориллские руки', 'Усиленные кибер-конечности для ближнего боя.', 18000, 'CASHLESS', 4, 'Импланты'],
  ['Сандевистан Mk.4', 'Замедление времени на несколько секунд.', 45000, 'CASHLESS', 2, 'Импланты'],
  ['Кибердека «Тайгер»', 'Дека для нетраннеров, +RAM, +демоны.', 30000, 'CASHLESS', 5, 'Импланты'],
  ['Kiroshi Optics', 'Оптические импланты с подсветкой целей.', 12000, 'CASHLESS', 8, 'Импланты'],
  ['MaxDoc Mk.3', 'Боевая аптечка мгновенного действия.', 500, 'CASHLESS', null, 'Медицина'],
  ['Bounce Back', 'Стимулятор восстановления.', 350, 'CASHLESS', null, 'Медицина'],
  ['Нанокровь', 'Редкий биостим. Оплата криптой.', 1200, 'CRYPTO', 20, 'Медицина'],
  ['Nicola', 'Холодный энергетик Найт-Сити.', 10, 'CASHLESS', null, 'Еда и напитки'],
  ['Синтетический виски', 'Бар «Афтерлайф», фирменный.', 80, 'CASHLESS', 50, 'Еда и напитки'],
  ['Поддельные документы', 'Чистая легенда под ключ.', 5000, 'CRYPTO', 6, 'Услуги'],
  ['Визит к рипердоку', 'Установка/настройка имплантов.', 2000, 'CASHLESS', null, 'Услуги'],
];

const NEWS = [
  {
    title: '⚠ Комендантский час в Уотсоне',
    summary: 'НКПД вводит ночной режим в районе после серии нападений.',
    body: '**НКПД** объявляет комендантский час в Уотсоне с 23:00 до 05:00.\n\nПатрули усилены. Имейте при себе документы. Подробности — на терминалах района.',
    category: 'Безопасность', pinned: true, status: 'PUBLISHED',
  },
  {
    title: 'Новый рипердок открылся в Кабуки',
    summary: 'Свежий кабинет, честные цены, без вопросов.',
    body: 'В Кабуки открылась новая клиника. Импланты, чистка прошивок, апгрейды.\n\n> «Железо — это свобода.»',
    category: 'Город', pinned: false, status: 'PUBLISHED',
  },
  {
    title: 'Афтерлайф: вечер живой музыки',
    summary: 'Керри Евродин даёт закрытый сет.',
    body: 'Сегодня в баре **«Афтерлайф»** — живой звук. Вход по картам. Столы бронируйте у бармена.',
    category: 'События', pinned: false, status: 'PUBLISHED',
  },
  {
    title: 'Сбой в сети Арасаки — осторожнее с переводами',
    summary: 'Возможны задержки транзакций.',
    body: 'Зафиксированы перебои в платёжной сети. Проверяйте баланс после операций и сохраняйте чеки.',
    category: 'Банк', pinned: false, status: 'PUBLISHED',
  },
  {
    title: 'Слухи о рейде на Корпо-плазу',
    summary: 'Черновик — на проверке у редакции.',
    body: 'По слухам, готовится крупная операция. Источники не подтверждены.',
    category: 'Слухи', pinned: false, status: 'DRAFT',
  },
];

async function main() {
  console.log(`Seeding Night City -> ${BASE}`);
  const login = await until(
    () => req('POST', '/auth/admin/login', { body: { publicName: ADMIN_NAME, password: ADMIN_PASSWORD } }),
    (r) => r.status === 200, { tries: 60, gap: 3000, label: 'admin login' },
  );
  const admin = login.json?.token;
  if (!admin) { console.error('No admin token, aborting.'); process.exit(2); }

  // Banker (Afterlife fixer) — can log in with password.
  const bankerName = 'Rogue';
  const bankerPass = 'afterlife2077';
  await req('POST', '/accounts', { token: admin, body: { publicName: bankerName, characterName: 'Роуг Амендиарес', role: 'BANKER', status: 'ACTIVE', password: bankerPass } });

  // Players
  const created = [];
  for (const [handle, character] of PLAYERS) {
    const r = await req('POST', '/accounts', { token: admin, body: { publicName: handle, characterName: character, role: 'PLAYER', status: 'ACTIVE' } });
    if (r.json?.id) created.push({ handle, id: r.json.id });
  }
  console.log(`Accounts: ${created.length} players + banker ${bankerName}/${bankerPass}`);

  // Fund + PIN each player (retry for async propagation)
  const pins = [];
  for (const p of created) {
    await until(
      () => req('POST', '/banking/deposit', { token: admin, body: { publicName: p.handle, currencyType: 'CASHLESS', amount: rnd(3, 60) * 1000, comment: 'Стартовый баланс (эдди)' } }),
      (r) => r.status === 200, { tries: 30, gap: 2500, label: `deposit ${p.handle}` },
    );
    await req('POST', '/banking/deposit', { token: admin, body: { publicName: p.handle, currencyType: 'CRYPTO', amount: rnd(1, 40) * 100, comment: 'Крипто-кошелёк' } });
    const pin = pin6();
    const pr = await until(
      () => req('POST', '/pins', { token: admin, body: { publicName: p.handle, rawPin: pin, durationMinutes: 600, comment: 'demo' } }),
      (r) => r.status === 200, { tries: 20, gap: 2500, label: `pin ${p.handle}` },
    );
    if (pr.status === 200) pins.push({ handle: p.handle, pin });
  }

  // Rich banking activity for history/audit
  const ops = [
    ['transfer', 'V', 'Jackie', 'CASHLESS', 1500, 'За работу'],
    ['transfer', 'Panam', 'Judy', 'CASHLESS', 800, 'Долг'],
    ['transfer', 'Jackie', 'Misty', 'CASHLESS', 600, 'За помощь'],
    ['transfer', 'Takemura', 'V', 'CASHLESS', 2500, 'Задаток'],
    ['transfer', 'River', 'Panam', 'CASHLESS', 400, 'Бензин'],
    ['withdraw', 'V', 'CASHLESS', 1200, 'Покупка в Кабуки'],
    ['withdraw', 'Judy', 'CASHLESS', 800, 'Аренда студии'],
    ['withdraw', 'Kerry', 'CRYPTO', 100, 'Студийное время'],
    ['deposit', 'Johnny', 'CASHLESS', 5000, 'Гонорар за концерт'],
    ['deposit', 'Lucy', 'CRYPTO', 500, 'Контракт нетраннера'],
  ];
  for (const o of ops) {
    if (o[0] === 'transfer') await req('POST', '/banking/transfer', { token: admin, body: { fromPublicName: o[1], toPublicName: o[2], currencyType: o[3], amount: o[4], comment: o[5] } });
    else await req('POST', `/banking/${o[0]}`, { token: admin, body: { publicName: o[1], currencyType: o[2], amount: o[3], comment: o[4] } });
  }
  // A reversal so audit/history shows the "operation cancelled" event
  const dep = await req('POST', '/banking/deposit', { token: admin, body: { publicName: 'Misty', currencyType: 'CASHLESS', amount: 999, comment: 'Ошибочное зачисление' } });
  if (dep.json?.transactionId) {
    await req('POST', '/banking/reverse', { token: admin, body: { transactionId: dep.json.transactionId, comment: 'Откат ошибочного зачисления' } });
  }

  // The admin/treasury also gets banking activity (so its own history isn't empty)
  await until(
    () => req('POST', '/banking/deposit', { token: admin, body: { publicName: ADMIN_NAME, currencyType: 'CASHLESS', amount: 100000, comment: 'Операционный бюджет' } }),
    (r) => r.status === 200, { tries: 20, gap: 2500, label: 'admin deposit' },
  );
  await req('POST', '/banking/deposit', { token: admin, body: { publicName: ADMIN_NAME, currencyType: 'CRYPTO', amount: 5000, comment: 'Резерв' } });
  await req('POST', '/banking/withdraw', { token: admin, body: { publicName: ADMIN_NAME, currencyType: 'CASHLESS', amount: 2500, comment: 'Операционные расходы' } });
  await req('POST', '/banking/transfer', { token: admin, body: { fromPublicName: ADMIN_NAME, toPublicName: 'V', currencyType: 'CASHLESS', amount: 3000, comment: 'Аванс на задание' } });

  // Terminals
  let terms = 0;
  for (const [name, type, location] of TERMINALS) {
    const r = await req('POST', '/terminals', { token: admin, body: { name, terminalType: type, location } });
    if (r.status === 200) terms++;
  }

  // Cards for a few players (retry for snapshot)
  let cards = 0;
  for (const p of created.slice(0, 5)) {
    const r = await until(
      () => req('POST', '/cards', { token: admin, body: { publicName: p.handle, cardUid: `ARA-${p.handle.toUpperCase()}-${rnd(1000, 9999)}` } }),
      (r) => r.status === 200 || r.status === 400, { tries: 20, gap: 2500, label: `card ${p.handle}` },
    );
    if (r.status === 200) cards++;
  }

  // Marketplace catalogue
  let prods = 0;
  const productIds = {};
  for (const [name, description, price, currencyType, stockQuantity, category] of PRODUCTS) {
    const r = await until(
      () => req('POST', '/marketplace/products', { token: admin, body: { name, description, price, currencyType, stockQuantity, category } }),
      (r) => r.status === 200, { tries: 20, gap: 2500, label: `product ${name}` },
    );
    if (r.status === 200) { prods++; productIds[name] = r.json.id; }
  }

  // News
  let news = 0;
  for (const n of NEWS) {
    const r = await until(
      () => req('POST', '/news', { token: admin, body: n }),
      (r) => r.status === 200, { tries: 20, gap: 2500, label: `news ${n.title}` },
    );
    if (r.status === 200) news++;
  }

  // Players buy from the marketplace (logical: order + balance debit + audit)
  const buys = [
    ['V', 'MaxDoc Mk.3', 2],
    ['V', 'Kiroshi Optics', 1],
    ['Johnny', 'Синтетический виски', 4],
    ['Johnny', 'Nicola', 3],
    ['Judy', 'Bounce Back', 2],
    ['Judy', 'Нанокровь', 1],
    ['Panam', 'Nicola', 5],
    ['Panam', 'Визит к рипердоку', 1],
    ['Jackie', 'Визит к рипердоку', 1],
    ['Takemura', 'MaxDoc Mk.3', 1],
    ['Kerry', 'Синтетический виски', 2],
    ['River', 'MaxDoc Mk.3', 1],
    ['Lucy', 'Bounce Back', 3],
    ['Misty', 'Nicola', 2],
  ];
  // Cache one token per player (login once)
  const tokens = {};
  let purchases = 0;
  const orderIds = [];
  for (const [handle, prodName, qty] of buys) {
    const pid = productIds[prodName];
    if (!pid) continue;
    if (!tokens[handle]) {
      const pin = pins.find((p) => p.handle === handle)?.pin;
      if (!pin) continue;
      const lr = await until(() => req('POST', '/auth/player/login', { body: { pin } }), (r) => r.status === 200,
        { tries: 15, gap: 2000, label: `login ${handle}` });
      tokens[handle] = lr.json?.token;
    }
    const ptoken = tokens[handle];
    if (!ptoken) continue;
    const r = await until(() => req('POST', '/marketplace/orders', { token: ptoken, body: { productId: pid, quantity: qty } }),
      (r) => r.status === 200 || r.status === 400, { tries: 15, gap: 2000, label: `buy ${handle}` });
    if (r.status === 200) { purchases++; if (r.json?.id) orderIds.push(r.json.id); }
  }
  // Cancel one purchase (refund) for variety in order statuses
  if (orderIds.length) {
    await req('POST', `/marketplace/orders/${orderIds[0]}/cancel`, { token: admin });
  }

  // The admin/treasury also buys a couple of items (so its "Мои покупки" isn't empty)
  for (const [prodName, qty] of [['MaxDoc Mk.3', 1], ['Синтетический виски', 2]]) {
    const pid = productIds[prodName];
    if (pid) {
      const r = await until(() => req('POST', '/marketplace/orders', { token: admin, body: { productId: pid, quantity: qty } }),
        (r) => r.status === 200 || r.status === 400, { tries: 10, gap: 2000, label: `admin buy ${prodName}` });
      if (r.status === 200) purchases++;
    }
  }

  console.log('\n=== Seed complete ===');
  console.log(`Players: ${created.length}, Terminals: ${terms}, Cards: ${cards}, Products: ${prods}, News: ${news}, Purchases: ${purchases}`);
  console.log(`Banker login: ${bankerName} / ${bankerPass}`);
  console.log('Player PINs (login via "Игрок (PIN)"):');
  for (const x of pins) console.log(`  ${x.handle.padEnd(10)} ${x.pin}`);
}

main().catch((e) => { console.error('Seed crashed:', e); process.exit(3); });
