# E2E / Integration Scenarios — Cyberpunk LARP backend

All requests go through the **gateway** at `http://localhost:8080/api` (context-path `/api`).
Auth is a Bearer JWT from `/auth/admin/login` or `/auth/player/login`.

**Error mapping (verified in source):**
- Downstream `BadRequestException` → **400** (privileges, insufficient funds, invalid currency,
  "account not active", same-account transfer, …).
- Downstream `NotFoundException` → **404** (account / transaction / balance not found).
- Gateway `@PreAuthorize` denial → **403**. Missing/invalid JWT → **401**.
- Bean-validation on login DTOs (`@Valid`) → **400**.

**Notes:**
- Account creation propagates to banking/access services **asynchronously via Kafka**, so the
  first banking op or player login after creating an account is **retried** until the snapshot
  lands (the runner retries with a timeout).
- `publicName` values are suffixed with a run timestamp so the suite is re-runnable.
- Roles: PLAYER, BANKER, ADMIN, DEVELOPER. Statuses: ACTIVE, BLOCKED, ARCHIVED.
  Currencies: CASHLESS, CRYPTO. (Balance adjustment was removed — deposit/withdraw cover both directions.)

---

## 1. Auth

| ID | Scenario | Request | Expected |
|----|----------|---------|----------|
| A1 | Admin login (valid) | POST /auth/admin/login `{publicName, password}` from `.env` | 200, `token` present, `role=ADMIN` |
| A2 | Admin login wrong password | POST /auth/admin/login `{admin, WRONG}` | 401 |
| A3 | Admin login unknown user | POST /auth/admin/login `{nobody, x}` | 401 |
| A4 | `/auth/me` with valid token | GET /auth/me + Bearer | 200, `role=ADMIN`, `publicName=admin` |
| A5 | `/auth/me` no token | GET /auth/me | 401 |
| A6 | `/auth/me` malformed token | GET /auth/me + `Bearer garbage` | 401 |
| A7 | Player login (valid PIN) | POST /auth/player/login `{pin:<issued>}` | 200, `role=PLAYER` |
| A8 | Player login (invalid PIN) | POST /auth/player/login `{pin:000000}` | 401 |
| A9 | Logout | POST /auth/logout + Bearer | 200, `success=true` |

## 2. Accounts

| ID | Scenario | Expected |
|----|----------|----------|
| AC1 | Create PLAYER account (admin) | 200, returns numeric `id` |
| AC2 | Create BANKER account (admin, with password) | 200 |
| AC3 | Create 2nd PLAYER (for transfer) | 200 |
| AC4 | Create account missing `publicName` | ≥400 |
| AC5 | Create account duplicate `publicName` | ≥400 |
| AC6 | List accounts | 200, `totalElements ≥ 3` |
| AC7 | List accounts `search=<player1 name>` | 200, player1 present in `content` |
| AC8 | Get account by id | 200, id matches |
| AC9 | Get account by missing id (99999999) | 404 |
| AC10 | Get `/accounts/me` (admin) | 200, `publicName=admin` |
| AC11 | PATCH status → BLOCKED | 200, `status=BLOCKED` |
| AC12 | PATCH status → ACTIVE | 200, `status=ACTIVE` |
| AC13 | PATCH status invalid value (`FOO`) | ≥400 |
| AC14 | PATCH role → BANKER then PLAYER | 200 |
| AC15 | Create account as BANKER token | 403 |
| AC16 | List accounts as PLAYER token | 403 |

## 3. PINs

| ID | Scenario | Expected |
|----|----------|----------|
| P1 | Create PIN for player1 (admin) | 200 |
| P2 | Create PIN duration 0 | 400 |
| P3 | Create PIN duration 5000 (>1440) | 400 |
| P4 | Create PIN missing `rawPin` | 400 |
| P5 | List pins by account | 200, created pin present |
| P6 | Revoke pin (issued on player2) | 200, `status=REVOKED` |
| P7 | Player login with revoked pin | 401 |

> **Business rule:** an account may have only **one active PIN** at a time — issuing a second
> returns **400 "Account already has active PIN"**. Revoke the existing PIN before issuing a new one.
> (P6 therefore issues its throwaway PIN on player2, since player1 already holds an active PIN from P1.)
| P8 | Create pin as PLAYER token | 403 |

## 4. Banking (admin/banker context)

| ID | Scenario | Expected |
|----|----------|----------|
| B1 | Deposit CASHLESS 1000 → player1 (retried for propagation) | 200, `balanceAfter=1000` |
| B2 | Deposit CRYPTO 50 → player1 | 200, `balanceAfter=50` |
| B3 | Deposit amount 0 | 400 |
| B4 | Deposit missing `publicName` | ≥400 |
| B5 | Deposit invalid currency `GOLD` | 400 |
| B6 | Withdraw CASHLESS 400 (within balance) | 200, `balanceAfter=600` |
| B7 | Withdraw CASHLESS 1e9 (exceeds balance) | 400 (Insufficient funds) |
| B8 | `POST /banking/adjust` (operation removed) | **404 — endpoint no longer exists** |
| B11 | Transfer player1→player2 CASHLESS 300 (600 → 300) | 200 |
| B12 | Transfer to same account | 400 |
| B13 | Transfer exceeding balance | 400 |
| B14 | Get `/banking/balance/{player1Id}` | 200, `cashlessAmount` = 300 |
| B15 | Get `/banking/balance/{missingId}` | 404 |
| B16 | Get `/banking/transactions/{player1Id}` | 200, non-empty `content` |
| B17 | Reverse a successful deposit tx | 200 |
| B18 | Reverse missing tx (99999999) | 404 |
| B19 | Reverse an already-reversed tx (idempotency) | 400 ("already reversed") |
| B20 | Deposit as PLAYER token (privilege) | 400 (requireAdminOrBanker) |

> **Gap found:** the banking Kafka listener does **not** run bean-validation on request DTOs
> (only explicit checks for amount/currency/direction exist), so `@NotBlank comment` on reversal
> is **not enforced server-side**. The frontend ReversePage marks comment `required` defensively.

## 4b. Cards (NFC card bindings, ADMIN/BANKER)

| ID | Scenario | Expected |
|----|----------|----------|
| C1 | Issue card (uidA) for player1 (retried for propagation) | 200, `status=ISSUED` |
| C2 | Issue 2nd active card for same account | 400 ("already has an active card") |
| C3 | Issue card with duplicate UID (uidA) | 400 ("cardUid is already used") |
| C4 | List cards by account | 200, uidA present |
| C5 | Get card by id | 200 |
| C6 | Lookup `/cards/by-uid?uid=uidA` | 200, owner = player1 |
| C7 | Lookup unknown UID | 404 |
| C8 | Block card | 200, `status=BLOCKED` |
| C9 | Block already-blocked card | 400 (only ISSUED can be blocked) |
| C10 | Issue new card + mark LOST | 200 / 200, `status=LOST` |
| C11 | Replace LOST card (uidC) | 200, new `status=ISSUED` |
| C12 | Issue card as PLAYER token | 403 |
| C13 | Get missing card (99999999) | 404 |

> **Business rules:** one active (ISSUED) card per account; `cardUid` globally unique (reserved even
> after block/replace); only ISSUED → block/lost; only ISSUED or LOST → replace. Access ADMIN/BANKER.

## 4c. Terminals (manage: ADMIN; view: ADMIN/BANKER)

| ID | Scenario | Expected |
|----|----------|----------|
| T1 | Register terminal | 200, `status=ACTIVE` |
| T2 | Register duplicate name | 400 |
| T3 | Register missing name | 400 |
| T4 | List terminals | 200, `totalElements ≥ 1` |
| T5 | List filter `status=ACTIVE` | 200, all rows ACTIVE |
| T6 | Get terminal by id | 200 |
| T7 | Get terminal by name | 200 |
| T8 | Get missing terminal (99999999) | 404 |
| T9 | Change status → MAINTENANCE | 200, `status=MAINTENANCE` |
| T10 | Update fields (location/notes) | 200, location updated |
| T11 | Register as BANKER token | 403 (admin-only) |
| T12 | Register as PLAYER token | 403 |
| T13 | List as PLAYER token | 403 |
| T14 | List as BANKER token | 200 (view allowed) |

> **Access split:** register / update / change-status are **ADMIN-only** (`@PreAuthorize hasRole ADMIN`);
> list / get are ADMIN/BANKER. Terminal `name` is unique and not editable after registration.
> Statuses: ACTIVE, MAINTENANCE, BLOCKED, DECOMMISSIONED.

## 4d. Marketplace (catalog ADMIN/BANKER; buy = any authenticated)

Purchase is a gateway-orchestrated saga: RESERVE_ORDER → PURCHASE_CHARGE (variant B —
the buyer debits their **own** account) → CONFIRM_ORDER, with cancel/refund compensation.
At this point player1 holds CASHLESS 300, CRYPTO 50.

| ID | Scenario | Expected |
|----|----------|----------|
| M1 | Create product (admin; retried until gateway reply consumer ready) | 200, `status=ACTIVE`, numeric `id` |
| M2 | Create product without `sku` | 200, `sku` auto-assigned |
| M3 | Create product duplicate `sku` | 400 |
| M4 | Create product invalid currency (`GOLD`) | 400 |
| M5 | Create product as PLAYER token | 403 |
| M6 | Create product as BANKER (no `stockQuantity` = unlimited) | 200, `stockQuantity=null` |
| M7 | List products (admin) | 200, `totalElements ≥ 2` |
| M8 | Get product by id | 200, id matches |
| M9 | Get missing product (99999999) | 404 |
| M10 | List products as PLAYER | 200, every row `status=ACTIVE` |
| M11 | Adjust stock +10 (5 → 15) | 200, `stockQuantity=15` |
| M12 | Change status → HIDDEN | 200, `status=HIDDEN` |
| M13 | Player gets the hidden product | 404 (players see ACTIVE only) |
| M14 | Player buys the hidden product | 400 (not available) |
| M15 | Player buys ACTIVE product ×2 | 200, `status=PAID`, `totalPrice=200` |
| M16 | Buyer balance after purchase | 200, `cashlessAmount=100` (300 − 200) |
| M17 | Player `/marketplace/orders/me` | 200, contains the PAID order |
| M18 | Player buys beyond remaining funds (×2 = 200 > 100) | 400 (Insufficient funds) |
| M19 | Product stock after failed purchase | 200, `stockQuantity=13` (reserve compensated) |
| M20 | Buy beyond stock (qty 5 of stock-1 product) | 400 (Insufficient stock) |
| M21 | List all orders (admin) | 200, `totalElements ≥ 1` |
| M22 | Get order by id (admin) | 200, id matches |
| M23 | Player cancels an order | 403 (manage = ADMIN/BANKER) |
| M24 | Admin cancels the PAID order | 200, `status=CANCELLED` (charge reversed) |
| M25 | Buyer balance after cancel/refund | 200, `cashlessAmount=300` (refunded) |

> **Notes:** catalog management (`POST/PATCH /marketplace/products`, status, stock) and order
> management (`GET /marketplace/orders`, cancel) require ADMIN/BANKER. Browse (`GET products`),
> buy (`POST /marketplace/orders`) and `orders/me` are open to any authenticated user; players
> only ever see ACTIVE products. `PURCHASE_CHARGE` records a WITHDRAW-type bank tx with
> operation `PURCHASE`; a manual cancel of a PAID order reverses that tx to refund the buyer.

## 4e. News (manage ADMIN/BANKER; read = any authenticated)

Announcements board: draft/publish/archive, pinned posts, categories. Players only see
**live** posts (`status=PUBLISHED` and `publishAt`, if set, has passed); operators see all.
Feed is sorted pinned-first, then newest.

| ID | Scenario | Expected |
|----|----------|----------|
| N1 | Create draft (admin; retried until gateway reply consumer ready) | 200, `status=DRAFT`, numeric `id` |
| N2 | Create missing `title` | ≥400 |
| N3 | Create missing `body` | ≥400 |
| N4 | Create as PLAYER token | 403 |
| N5 | Create with `status=PUBLISHED` (admin) | 200, `status=PUBLISHED`, `publishedAt` set |
| N6 | Create as BANKER | 200 |
| N7 | List all (admin) | 200, `totalElements ≥ 2` |
| N8 | Get post by id (admin) | 200, id matches |
| N9 | Get missing post (99999999) | 404 |
| N10 | Player feed | 200, every row `status=PUBLISHED`, draft absent |
| N11 | Player gets the draft | 404 (not live) |
| N12 | Player gets the published post | 200 |
| N13 | Publish the draft (status → PUBLISHED) | 200, `status=PUBLISHED` |
| N14 | Player feed now contains it | 200, present |
| N15 | Pin a post | 200, `pinned=true`, sorts first in feed |
| N16 | Unpin the post | 200, `pinned=false` |
| N17 | Update post (title/body) | 200, title updated |
| N18 | Archive post → player view | 200 `ARCHIVED`; player get → 404 |
| N19 | Set pinned as PLAYER token | 403 |
| N20 | Admin filter `status=ARCHIVED` | 200, all rows ARCHIVED, includes the archived post |

> **Notes:** management (`POST/PATCH /news`, status, pin) requires ADMIN/BANKER; read
> (`GET /news`, `GET /news/{id}`) is open to any authenticated user. Scheduled publishing
> uses `publishAt` and is enforced at query time — no background scheduler. The list-filter
> query uses the empty-string sentinel (same fix as marketplace) to avoid `lower(bytea)`.

## 5. Player self-service (player token)

| ID | Scenario | Expected |
|----|----------|----------|
| PS1 | Player `/banking/balance/me` | 200 |
| PS2 | Player `/banking/transactions/me` | 200, paged |
| PS3 | Player `/sessions/active` | 403 |

## 6. Sessions

| ID | Scenario | Expected |
|----|----------|----------|
| S1 | `/sessions/me` (admin) | 200 |
| S2 | `/sessions/active` (admin) | 200, `content` array |
| S3 | Terminate a session | 200 |

## 7. Audit (ADMIN/BANKER)

Audit logs are ingested **asynchronously** from every service's event topics (account / session /
banking / auth / card / terminal), so by this point in the run many logs exist (the runner retries
AU1 until logs land). `payloadJson` is null on normal reads (only the hack-token path reveals it).

| ID | Scenario | Expected |
|----|----------|----------|
| AU1 | List audit logs (retried until ingested) | 200, `totalElements > 0` |
| AU2 | Filter logs by `search` | 200 |
| AU3 | Get audit log by id | 200, id matches |
| AU4 | Get missing log (99999999) | 404 |
| AU5 | List audit logs as PLAYER token | 403 |
| AU6 | Filter by `eventType` | 200, all rows match the type |

---

**Runner:** `node e2e/run-scenarios.mjs` (no deps — uses Node 18+ global `fetch`).

Credentials are read from the repository's `.env` (`BOOTSTRAP_ADMIN_NAME` /
`BOOTSTRAP_ADMIN_PASSWORD`) — the same file the stack is started from, so a plain
`node e2e/run-scenarios.mjs` works with nothing to export. Override with `BASE`,
`ADMIN_NAME` or `ADMIN_PASSWORD` when pointing at another host.

Exit codes: `0` all passed · `1` at least one scenario failed · `2` no admin
password could be resolved · `3` the runner itself crashed.
