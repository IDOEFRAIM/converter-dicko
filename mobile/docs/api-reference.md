# Converter API Reference — Supplementary Sections (for Flutter port)

Scope: Quote, Order creation/history, Supplier, Settlement, Treasury/Wallet, PreferredRate,
Notifications, Business, Settings, Idempotency, Auth interceptors/error handling, global
conventions. Auth, Order/OrderDetail/OrderStatus, Payment, Tracking, Rate History, Rate Alert,
Receipt are already documented elsewhere and are **not** repeated here.

All backend source under `backend/src/main/java/com/converter/**`. All Angular source under
`frontend/src/app/**`. Backend comments are in French; short quotes are kept where they clarify
an invariant the mobile client must respect.

## Conventions used throughout this document

- **Envelope**: every success response is `{ "data": T, "message": string }` (`ApiResponse<T>`).
  Every error response is `ErrorResponse` (see Section 12).
- **Money/rate fields (`BigDecimal` in Java)**: the Angular models type these fields as `string`
  (e.g. `amountXof: string`), and the codebase is very deliberate about it — see the explicit
  caveat in **Section 12 -> Decimal wire format**. Treat every such field as a decimal string on
  the wire; **never parse into `double`/`float`** in Flutter (use a fixed-point/Decimal type).
- **Ownership**: a resource belonging to another user always yields `404`, never `403`.
- **No client-side financial computation**: rates/fees/totals are always computed backend-side
  and returned as-is; the mobile client must only display them.

---

## 1. Quote

Backend: `com/converter/quote/**`. Angular: `core/models/quote.model.ts`,
`core/services/quote.service.ts`.

Base path: `/api/v1/quotes` (JWT required).

### Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/quotes` | Create a quote from a single intent (`SEND_XOF` + `amountXof`, or `RECEIVE_CNY` + `amountCny`). Returns `201`. |
| GET | `/api/v1/quotes/{id}` | Get a quote. `404` if not found or not owned. |
| POST | `/api/v1/quotes/{id}/accept` | `ACTIVE -> ACCEPTED`. `409 QUOTE_EXPIRED` if the 30-minute window elapsed; `409 INVALID_QUOTE_STATE` if already accepted/cancelled. |
| POST | `/api/v1/quotes/{id}/cancel` | `ACTIVE -> CANCELLED`. Same expiry/state errors as accept. |

### `CreateQuoteRequest` (request body)

```ts
{
  direction: 'SEND_XOF' | 'RECEIVE_CNY';   // required
  amountXof: string | null;                // required iff direction = SEND_XOF
  amountCny: string | null;                // required iff direction = RECEIVE_CNY
}
```
Exactly one of `amountXof`/`amountCny` must be provided — enforced service-side (409), not by a
bean-validation annotation. Amounts: positive, max 17 integer digits, exactly 2 fraction digits.

### `QuoteResponse` (`Quote` in Angular)

```ts
{
  id: string;                // UUID
  direction: 'SEND_XOF' | 'RECEIVE_CNY';
  amountXof: string;
  amountCny: string;
  customerRate: string;      // rate actually offered to the client (6 decimal places server-side)
  feeXof: string;
  netAmountXof: string;
  status: 'ACTIVE' | 'ACCEPTED' | 'EXPIRED' | 'CANCELLED';
  createdAt: string;         // Instant, ISO-8601 UTC
  expiresAt: string;         // createdAt + 30 minutes
}
```

Deliberately **never** exposes `breakEvenRate` or `marginPercentage` (internal cost/margin data;
enforced by a backend test, `QuoteResponseTest`, that fails the build if such a field is ever
added). Do not attempt to reconstruct/display these in the mobile app either.

`QuoteStatus` lifecycle: `ACTIVE -> ACCEPTED` (terminal, consumed by an Order) / `ACTIVE -> EXPIRED`
(lazy: `expiresAt` is the source of truth, `status` is only corrected to `EXPIRED` on the next
interaction) / `ACTIVE -> CANCELLED`.

---

## 2. Order creation & history

Backend: `com/converter/order/**`. Angular: `core/models/order.model.ts`,
`core/services/order.service.ts`, `features/order/order-create/order-create.page.ts`.

Base path: `/api/v1/orders` (JWT required).

### Endpoints (not already documented)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/orders` | Creates an order from an **already-accepted** quote. Optional `Idempotency-Key` header — a replay with the same key+body never creates a second order. |
| GET | `/api/v1/orders` | `?page=&size=` -> `PageResponse<OrderSummaryResponse>`. |
| GET | `/api/v1/orders/history` | Enriched history. Filters (all optional, server-side): `status`, `purpose`, `supplierId`, `from`, `to` (Instant, `from <= createdAt < to`). Fixed sort: `createdAt DESC, id DESC`. A `supplierId` belonging to another user returns an empty page, never someone else's data. |
| GET | `/api/v1/orders/feasibility?quoteId=` | Call **before** entering the beneficiary. See below. |
| GET | `/api/v1/orders/{id}` | Full detail. |
| POST | `/api/v1/orders/{id}/cancel` | Only from `AWAITING_PAYMENT`. Body: `{ reason: string }` (required, non-blank, max 500). Releases the treasury reservation. |
| GET | `/api/v1/orders/{id}/tracking` | Already documented (Tracking). |
| GET | `/api/v1/orders/{id}/receipt` | Already documented (Receipt). |

### `CreateOrderRequest`

```ts
{
  quoteId: string;                        // required, must be ACCEPTED and owned by caller
  beneficiary: BeneficiaryRequest | null; // exactly one of beneficiary/supplierId (service-checked, 409/400 otherwise)
  note: string | null;                    // max 500
  supplierId?: string | null;
  purpose?: Purpose | null;
  purposeDetails?: string | null;         // max 500
}
```
`supplierId` is purely a traceability reference — its beneficiary fields are copied into an
immutable `Beneficiary` snapshot on the order at creation time; the supplier is never re-read to
reconstruct that snapshot later.

`BeneficiaryRequest`:
```ts
{
  type: 'ALIPAY' | 'WECHAT_PAY' | 'CHINESE_BANK_ACCOUNT'; // required
  fullName: string;    // required, max 120
  identifier: string;  // required, max 120 -- Alipay/WeChat account or bank account number
  bankName: string | null;   // required (service-checked) if type = CHINESE_BANK_ACCOUNT, max 120
  bankBranch: string | null; // max 120
}
```

`Purpose` enum (shared with Supplier): `PERSONAL, EDUCATION, FAMILY_SUPPORT, IMPORT_GOODS,
SERVICES, BUSINESS, OTHER`.

### `OrderFeasibilityResponse`

```ts
{
  quoteId: string;
  amountCny: string;                       // copied from the quote
  settlementReservationEnabled: boolean;   // true if setting TREASURY_RESERVE_ON_ORDER is on
  sufficientLiquidity: boolean;            // display hint only -- actual truth is the reservation made at order creation
}
```
Exposes **no treasury balance**, only these two booleans/amount.

### `OrderHistoryResponse` (list item, `/history`)

```ts
{
  id: string; reference: string; status: OrderStatus;
  amountXof: string; amountCny: string; feeXof: string; customerRate: string;
  purpose: Purpose | null; supplierId: string | null;
  createdAt: string; completedAt: string | null;
}
```

### `OrderSummaryResponse` (list item, plain `GET /orders`)
```ts
{ id: string; reference: string; status: OrderStatus; amountXof: string; amountCny: string; createdAt: string; }
```

### `OrderDetailResponse` fields not previously covered
`beneficiary: BeneficiaryResponse` (`{ type, fullName, identifier, bankName, bankBranch }`),
`statusHistory: OrderStatusHistoryResponse[]` (`{ fromStatus: OrderStatus|null, toStatus:
OrderStatus, changedBy: UUID|null, reason: string|null, createdAt }`), `supplierId: string |
null`, `purpose: Purpose | null`, `purposeDetails: string | null`, `paymentDeadlineAt: Instant`
(payment window deadline; past it the order auto-expires and its reservation is released),
`cancellationReason`, `rejectionReason`.

### Order-create page UX (Angular)

- Feasibility is checked immediately on load (before the beneficiary form is shown); a `false`
  `sufficientLiquidity` is a display warning only.
- If the user already has suppliers, the "use a registered supplier" source is pre-selected by
  default (`SUPPLIER` over manual entry).
- An `IdempotencyAttempt` (see Section 10) generates a key per submit attempt; the same key is
  reused across retries of an identical payload and dropped after success.
- Client-side validation before submit: manual beneficiary requires `fullName`, `identifier`, and
  `bankName` when type is `CHINESE_BANK_ACCOUNT`; supplier mode requires `supplierId`.
- Generic fallback empty state: `"Aucun devis a associer. Recommencez depuis un nouveau devis."`

---

## 3. Supplier

Backend: `com/converter/supplier/**`. Angular: `core/models/supplier.model.ts`,
`core/services/supplier.service.ts`, `features/supplier/*`.

Base path: `/api/v1/suppliers` (JWT required).

### Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/suppliers` | Create. `201`. |
| GET | `/api/v1/suppliers?status=&page=&size=` | `status` optional (`ACTIVE`/`INACTIVE`). |
| GET | `/api/v1/suppliers/favorites?page=&size=` | Favorites only. |
| GET | `/api/v1/suppliers/{id}` | Full detail, incl. plaintext account number. |
| PUT | `/api/v1/suppliers/{id}` | Update. Never affects orders already created from this supplier (immutable snapshot). |
| POST | `/api/v1/suppliers/{id}/favorite` | Mark favorite. |
| POST | `/api/v1/suppliers/{id}/unfavorite` | Unmark. |
| POST | `/api/v1/suppliers/{id}/deactivate` | Logical deactivation only -- never a real delete; past orders unaffected. |
| POST | `/api/v1/suppliers/{id}/pay-again` | Creates a **new** quote (current pricing) then a **new** order. Optional `Idempotency-Key`; the supplier id is part of the idempotency endpoint identity (same key reused against a different supplier never replays a mismatched payment). |

### `CreateSupplierRequest` / `UpdateSupplierRequest` (same shape)

```ts
{
  type: 'ALIPAY' | 'WECHAT_PAY' | 'CHINESE_BANK_ACCOUNT'; // required
  displayName: string;      // required, max 120
  legalName: string | null; // max 160
  phone: string | null;     // max 30
  email: string | null;     // max 160, validated
  country: string | null; city: string | null; province: string | null; // max 100 each
  bankName: string | null;   // required (service-checked) if type=CHINESE_BANK_ACCOUNT, max 120
  bankBranch: string | null; // max 120
  accountName: string | null; // max 120
  accountNumber: string;      // required, max 120 (Alipay/WeChat account or bank account number)
  bankAddress: string | null; // max 255
  swiftCode: string | null;   // max 20
  currency: 'XOF' | 'CNY';    // required
  purpose: Purpose | null;    // default classification only, never blindly copied onto an order
  notes: string | null;       // max 1000
}
```

### `SupplierSummaryResponse` (list item)
```ts
{ id, type, displayName, country: string|null, city: string|null,
  maskedAccountNumber: string,  // e.g. "******1234" -- only the last 4 chars visible
  purpose: Purpose|null, favorite: boolean, status: 'ACTIVE'|'INACTIVE', createdAt }
```
The plaintext account number is **never** returned in list views, only in `SupplierDetailResponse`.

### `SupplierDetailResponse`
Same fields as the create/update request plus `id`, `accountNumber` in clear, `favorite: boolean`,
`status: SupplierStatus`, `createdAt`, `updatedAt`.

### `PayAgainRequest`
```ts
{ amountXof: string; purpose: Purpose | null; purposeDetails: string | null; }
```
The amount is **always** re-entered by the user for a repeat payment -- never copied from a past
order/transaction. Backend builds an ordinary `CreateQuoteRequest` from it with **current**
pricing (never reuses a past rate/fee/CNY amount).

### UX conventions worth mirroring

- Supplier list has 3 filter tabs: All (active), Favorites, Deactivated (`INACTIVE`). Client-side
  search filters only the already-loaded page (max 50 rows) -- there is no full-text search
  endpoint.
- Empty-state copy: `"Aucun fournisseur favori"` / `"Aucun fournisseur desactive"` /
  `"Aucun fournisseur enregistre"`.
- Deactivate confirmation dialog copy: title `"Desactiver le fournisseur"`, message
  `"Ce fournisseur ne sera plus proposable pour un nouveau paiement. Vos ordres deja crees avec
  lui ne sont pas affectes. Aucune suppression n'a lieu."`, confirm button `"Desactiver"`.
- "Pay again" action label: **"Payer a nouveau"**.
- `pay-again` page pre-fills `purpose` from the supplier's default purpose if set, but the amount
  field always starts empty and is mandatory (`min 1`).

---

## 4. Settlement

Backend: `com/converter/settlement/**`. Angular: `core/models/settlement.model.ts`,
`core/services/settlement.service.ts`.

**Important**: `Settlement` is an **admin-only** resource (`/api/admin/**`, `@PreAuthorize
hasRole('ADMIN')`). The mobile app (a regular customer client) will never call these endpoints
directly and never needs an admin token. It tracks settlement progress **indirectly** through the
Order's own status:
- `order.status == 'PROCESSING'` -> settlement in progress ("Reglement en cours" -- *"Votre paiement
  est confirme. L'equipe execute actuellement le versement en Chine."*)
- `order.status == 'COMPLETED'` -> settlement executed ("Reglement execute" -- *"Le versement au
  beneficiaire en Chine a ete effectue."*)
- any earlier status -> not started ("Pas encore commence" -- *"Le reglement demarrera une fois
  votre paiement verifie."*)

Angular's `SettlementService` has **no HTTP calls at all** -- it's a pure `deriveFromStatus(order
.status)` mapper producing `{ stage: 'NOT_STARTED'|'IN_PROGRESS'|'COMPLETED', label, description
}`. Reproduce the same derivation logic in Flutter rather than calling `/api/admin/settlements/*`.

### Reference: admin `SettlementResponse` shape (for completeness only)
```ts
{
  id, orderId, status: 'PENDING' | 'EXECUTED',
  amountCny: string, method: BeneficiaryType,
  beneficiaryFullName, beneficiaryIdentifier,
  beneficiaryBankName: string|null, beneficiaryBankBranch: string|null,
  settlementReference: string|null, notes: string|null,
  executedBy: string|null, proofs: SettlementProof[],
  createdAt, executedAt: string|null
}
```
(Note: the Angular `Settlement` model types `method` as plain `string`, though the backend DTO
type is the `BeneficiaryType` enum.) Admin-only endpoints: `POST /api/admin/orders/{orderId}
/settlement`, `GET /api/admin/settlements/pending`, `GET /api/admin/settlements/{id}`, `POST
/api/admin/settlements/{id}/proofs` (multipart), `GET /api/admin/settlements/{id}/proofs
/{proofId}`, `POST /api/admin/settlements/{id}/execute` (idempotency-key supported).
`SettlementStatus`: `PENDING -> EXECUTED` (terminal).

---

## 5. Treasury / Wallet

### Treasury (admin-only -- reference only, mobile app does not call this)

Backend: `com/converter/treasury/**`. Base path `/api/admin/treasury` (`ADMIN` role only).

`Currency` enum: `XOF, CNY`.

`TreasuryAccountResponse`: `{ id, currency, balance: string, reservedBalance: string,
available: string, lowThreshold: string, updatedAt }`.

`TreasuryTransactionResponse`: `{ id, accountId, type: TreasuryTransactionType, amount: string,
balanceAfter: string, reservedAfter: string, orderId: string|null, performedBy: string|null,
reason: string|null, createdAt }`.

`TreasuryTransactionType`: `DEPOSIT, WITHDRAWAL, RESERVATION, RELEASE, ADJUSTMENT, REFUND`
(Angular's type union currently omits `REFUND` -- treat the backend enum as authoritative and
handle an unknown value gracefully in Flutter).

Endpoints: `GET /accounts/{currency}`, `GET /accounts/{currency}/transactions`, `POST /deposit`,
`POST /adjust` (both accept/recommend `Idempotency-Key` -- these are the **only** two financial
endpoints in the whole API with no SQL uniqueness safety net against a replay).

### Wallet (customer-facing -- mobile app DOES call this)

Backend: `com/converter/wallet/**`. Angular: `core/models/wallet.model.ts`,
`core/services/wallet.service.ts`. Base path `/api/v1/wallet` (JWT required).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/wallet` | Returns/creates (lazily, zero balance) the caller's own wallet. Ownership implicit -- never parametrized by id. |
| GET | `/api/v1/wallet/transactions?page=&size=` | Own ledger only. |

`WalletResponse`: `{ id, balance: string, reservedBalance: string, available: string, updatedAt }`.

`WalletTransactionResponse`: `{ id, type: WalletTransactionType, amount: string,
balanceAfter: string, reservedAfter: string, referenceId: string|null, reason: string|null,
createdAt }`.

`WalletTransactionType`: `CREDIT, DEBIT, RESERVE, RELEASE`.

The Wallet is what backs **PreferredRate** requests (an active request immobilizes `amountXof` on
the wallet -- see Section 6).

---

## 6. PreferredRate

Backend: `com/converter/preferredrate/**`. Angular: `core/models/preferred-rate.model.ts`,
`core/services/preferred-rate.service.ts`.

Base path: `/api/v1/preferred-rates` (JWT required).

### Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/preferred-rates` | Immediately immobilizes `amountXof` on the caller's Wallet. Valid 3 days. `201`. |
| GET | `/api/v1/preferred-rates?page=&size=` | List mine. |
| GET | `/api/v1/preferred-rates/{id}` | Detail. |
| POST | `/api/v1/preferred-rates/{id}/cancel` | Only from `ACTIVE`. Immediately releases the reserved Wallet amount. |

Automatic triggering (target rate reached -> exchange starts) is driven entirely by a backend
scheduler; this API only exposes create/read/cancel.

### `CreatePreferredRateRequest`
```ts
{ direction: 'XOF_TO_CNY'; amountXof: string; targetRate: string; }
```
`amountXof`: positive (`DecimalMin 0.01`), max 17 integer / 2 fraction digits. `targetRate`:
positive (`DecimalMin 0.000001`). `PreferredRateDirection` currently has a single value,
`XOF_TO_CNY` (field kept for future extensibility).

### `PreferredRateRequestResponse`
```ts
{
  id: string;
  direction: 'XOF_TO_CNY';
  amountXof: string;
  targetRate: string;
  currentRate: string | null;   // recomputed on every read (RateProvider + margin), null if unavailable or phase != WAITING
  gap: number | null;           // *** BigDecimal serialized as a RAW JSON NUMBER (unquoted) -- the ONE known
                                 //     exception to the "amounts/rates are quoted strings" rule in this API. ***
  phase: PreferredRatePhase;
  status: 'ACTIVE' | 'EXECUTED' | 'EXPIRED' | 'CANCELLED';
  achievedRate: string | null;
  createdAt: string; expiresAt: string;
  executedAt: string | null; expiredAt: string | null; cancelledAt: string | null;
  exchange: ExchangeSummaryResponse | null;
}
```
`gap = currentRate - targetRate` (same null conditions as `currentRate`), computed server-side "so
the frontend never has to subtract" -- display only, still parse as a decimal (a JS/Dart double is
adequate here only because it's explicitly a *display gap*, not a value fed back into any
transaction; if in doubt, still route it through a Decimal parser rather than `double`).

`PreferredRatePhase` (which screen to show -- computed backend-side, never derive it yourself):
`WAITING` (countdown to J+3 relevant), `EXCHANGE_IN_PROGRESS` (2h countdown relevant, no more
J+3), `EXCHANGE_COMPLETED`, `EXPIRED`, `CANCELLED`. Never show both countdowns simultaneously.

`ExchangeSummaryResponse`:
```ts
{ id, amountXof: string, achievedRate: string, amountCny: string,
  status: 'STARTED'|'COMPLETED'|'CANCELLED',
  stage: string,  // backend enum ExchangeStatus is only STARTED/COMPLETED/CANCELLED;
                  // Angular's ExchangeStage adds finer display buckets 'STARTED'|'PROGRESS_45'|'PROGRESS_90'|'COMPLETED'
  startedAt, deadlineAt,       // start + 2h, computed backend-side
  nextUpdateAt: string | null, // next expected notification; null once finished
  completedAt: string | null }
```

---

## 7. Notifications

Backend: `com/converter/notification/**`. Angular: `core/models/inbox-notification.model.ts`,
`core/services/inbox-notification.service.ts`, `features/notifications/notifications.page.ts`.

Base path: `/api/v1/notifications` (JWT required).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/notifications?page=&size=` | Most recent first. |
| GET | `/api/v1/notifications/unread-count` | `{ unreadCount: number }` (backend `long`). |
| POST | `/api/v1/notifications/{id}/read` | Marks one notification read; returns the updated notification. |

`NotificationResponse` (`InboxNotification` in Angular):
```ts
{ id, type: NotificationType, title: string, message: string, createdAt, readAt: string | null }
```
`title`/`message` are fully formed, human-ready strings from the backend -- never synthesize your
own copy from `type` alone; only use `type` for icon/grouping.

`NotificationChannel` enum: `IN_APP` only (MVP -- no SMS/WhatsApp/email/push yet).

### `NotificationType` -- full enum + Angular icon/label mapping (mirror this table in Flutter)

| Value | Material icon (Angular) | French label |
|---|---|---|
| `QUOTE_CREATED` | `request_quote` | Devis |
| `PAYMENT_SUBMITTED` | `payments` | Paiement declare |
| `PAYMENT_CONFIRMED` | `check_circle` | Paiement confirme |
| `EXCHANGE_STARTED` | `sync` | Echange demarre |
| `EXCHANGE_PROGRESS` | `hourglass_top` | Echange en cours |
| `EXCHANGE_COMPLETED` | `task_alt` | Echange termine |
| `PREFERRED_RATE_REACHED` | `trending_up` | Taux preferentiel atteint |
| `PREFERRED_RATE_EXPIRED` | `schedule` | Taux preferentiel expire |
| `EXCHANGE_CANCELLED` | `cancel` | Echange annule |
| `ORDER_EXPIRED` | `timer_off` | Ordre expire |
| `RATE_ALERT_TRIGGERED` | `notifications_active` | Alerte de taux declenchee |

Fallback icon for an unrecognized type: `notifications`.

---

## 8. Business

Backend: `com/converter/business/profile/**` and `com/converter/business/reporting/**`. Angular:
`core/models/business.model.ts`, `core/services/business.service.ts`,
`features/business/business.page.ts`.

There is **no** `accountType`/`PERSONAL`/`BUSINESS` field on the user anywhere. Whether a user is
"Business" is derived purely from **whether a `BusinessProfile` exists**: `GET
/api/v1/business-profile` returning `404 BUSINESS_PROFILE_NOT_FOUND` means the user is Personal --
this is treated as a normal, expected outcome (not an error banner) and the client should show a
profile-creation form.

### Profile -- base path `/api/v1/business-profile` (JWT required, singleton per user, never addressed by id)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/business-profile` | `404 BUSINESS_PROFILE_NOT_FOUND` if none. |
| PUT | `/api/v1/business-profile` | Upsert (idempotent): creates if absent (`201`), updates otherwise (`200`). Owner is always the authenticated user -- the body never carries a `userId`. |

`UpsertBusinessProfileRequest`:
```ts
{
  businessName: string;      // required, max 160
  businessType: BusinessType; // required
  registrationNumber: string | null; // max 60
  country: string;           // required, max 100
  city: string | null;       // max 100
  address: string | null;    // max 255
}
```
`BusinessType` enum: `IMPORTER, MERCHANT, SERVICES, OTHER` (French labels: Importateur,
Commercant, Services, Autre).

`BusinessProfileResponse`: same fields plus `id`, `createdAt`, `updatedAt`.

### Reporting -- base path `/api/v1/business` (JWT required, 404 if no BusinessProfile)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/business/payments/summary?from=&to=` | `from`/`to` optional `Instant` (ISO-8601), convention `from <= createdAt < to`. |

`BusinessPaymentSummaryResponse` (exact KPI field names):
```ts
{
  period: { from: string | null; to: string | null };
  transferCount: number;    // long -- ALL orders in scope (any status), never counts Refunds
  completedCount: number;
  cancelledCount: number;
  rejectedCount: number;
  totalAmountXof: string;   // *** sums COMPLETED orders only ***
  totalAmountCny: string;   // *** sums COMPLETED orders only ***
  totalFeesXof: string;     // *** sums COMPLETED orders only ***
}
```
This COMPLETED-only convention for the three totals is a deliberate, explicitly documented
backend choice (an order that never actually moved money -- cancelled/rejected -- must never
inflate a "volume processed" figure), not something the spec mandated -- replicate it exactly.

### UX conventions

- `404` on `GET /business-profile` is swallowed silently by the page (no error banner) and simply
  reveals the creation form.
- Toast copy: `"Profil professionnel cree."` / `"Profil mis a jour."`.
- Default country value pre-filled in the create form: `"Burkina Faso"`.
- Summary is only fetched once a profile exists (created or already present).

---

## 9. Settings (public)

Backend: `com/converter/settings/**`. Angular: `core/models/settings.model.ts`,
`core/services/settings.service.ts`.

**No authentication required.** Base path: `/api/settings` (note: **not** `/api/v1`).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/settings/public` | Only settings flagged `is_public = true` in DB are exposed here. |

`PublicSettingsResponse`:
```ts
{
  minOrderAmountCfa: string;     // BigDecimal -- quoted string
  maxOrderAmountCfa: string;     // BigDecimal -- quoted string
  rateLockDurationMinutes: number; // int
  maxProofFileSizeBytes: number;   // long
  maxProofsPerPayment: number;     // int
  enabledPaymentMethods: string[]; // comma-separated setting split server-side, empties dropped
  requirePaymentProof: boolean;
}
```
This is explicitly a **display convenience only** -- "chaque regle est revalidee cote serveur au
moment de l'action reelle" (every rule is re-validated server-side at the moment of the real
action). Use it to pre-populate form hints/limits, never as the sole gate before submitting.

Angular caches this response for the whole session via `shareReplay` (single HTTP call ever, per
app load) -- mirror that caching behavior in Flutter since the values change rarely (admin-only,
infrequent).

For reference, the full closed catalogue of backend setting keys (`SettingKey` enum) is: `MIN_ORDER_AMOUNT_CFA`,
`MAX_ORDER_AMOUNT_CFA`, `RATE_LOCK_DURATION_MINUTES`, `ORDER_AUTO_EXPIRE_ENABLED`,
`REQUIRE_PAYMENT_PROOF`, `TREASURY_RESERVE_ON_ORDER`, `MAX_PROOF_FILE_SIZE_BYTES`,
`MAX_PROOFS_PER_PAYMENT`, `ENABLED_PAYMENT_METHODS`, `MAX_OPEN_ORDERS_PER_USER`,
`DEFAULT_MARGIN_PERCENTAGE`, `DEFAULT_FEE_PERCENTAGE`, `DEFAULT_FIXED_FEE_XOF`,
`ORDER_PAYMENT_WINDOW_MINUTES`, `PAYMENT_AMOUNT_TOLERANCE_XOF`, `RATE_MAX_AGE_MINUTES`,
`KYC_REQUIRED_THRESHOLD_XOF` -- only the seven surfaced in `PublicSettingsResponse` above are
reachable without admin rights.

---

## 10. Idempotency

Backend: `com/converter/common/idempotency/**` (`IdempotencyGuard`, `IdempotencyService`,
`IdempotencyKey`). Angular: `core/services/idempotency.util.ts` (full algorithm below).

### Header & applicability

- Header name: **`Idempotency-Key`** (free-form string, server-enforced max length **80**
  characters).
- **Always optional.** Omitting it preserves the pre-idempotency behavior (no extra protection
  beyond whatever SQL constraints already exist on that endpoint).
- Endpoints observed wiring it in: `POST /api/v1/orders`, `POST /api/v1/suppliers/{id}/pay-again`,
  `POST /api/admin/settlements/{id}/execute`, `POST /api/admin/treasury/deposit`, `POST
  /api/admin/treasury/adjust` (payment submission is covered elsewhere but follows the identical
  pattern).

### Server-side semantics (`IdempotencyGuard.guard`)

The lookup key is always `(userId, endpoint, idemKey)` -- `userId` comes exclusively from the
authenticated principal, **never** from client input, so one user can never trigger a replay of
another user's cached response. `endpoint` is a fixed string per call site (e.g. `"POST
/api/v1/orders"`, or `"POST /api/v1/suppliers/" + id + "/pay-again"` when the path variable is
part of the operation's identity -- reusing a key against a *different* supplier id must never
replay a mismatched order).

1. No key header -> action runs normally, no bookkeeping.
2. Key length > 80 -> `400`-class `VALIDATION_ERROR`.
3. First time this `(userId, endpoint, key)` is seen -> the request body is hashed (**SHA-256** of
   its canonical JSON, see below) and captured as **pending**; the action executes; on success the
   HTTP status + JSON response body are stored against the key (same DB transaction as the
   business effect -- commits together or not at all); on any `RuntimeException` the pending
   capture is deleted so a legitimate retry after a business failure is never stuck.
4. Same key seen again, **same** request-body hash -> the original response (status + body) is
   replayed verbatim, the action is **never** re-executed.
5. Same key, **different** request-body hash -> `409 IDEMPOTENCY_KEY_REUSED`.
6. Same key, still pending (concurrent/in-flight or crashed mid-flight) -> `409
   IDEMPOTENT_REQUEST_IN_PROGRESS`.
7. A background scheduler (`IdempotencyPendingCleanupScheduler`) reclaims stale `pending` rows
   older than a configured timeout, so a genuine crash doesn't permanently block retries with that
   key. Captured rows are retained 24h (`expires_at`, informational only in this phase).

Request-body hash is computed by serializing the deserialized request object back to JSON via the
app's Jackson `ObjectMapper` and SHA-256-hashing the resulting string -- **not** a hash of the raw
HTTP bytes, so JSON key order does not matter server-side either.

### Client-side algorithm (`idempotency.util.ts`, reproduce exactly in Flutter)

```ts
newIdempotencyKey(): string        // crypto.randomUUID()
idempotencyHeaders(key): headers   // { 'Idempotency-Key': key } or undefined if key is null/empty

// stable fingerprint of a request payload, insensitive to object-key order:
fingerprint(payload): string {
  // recursively walk the payload; for plain objects, sort keys before re-building;
  // arrays are mapped element-wise preserving order; a WeakSet guards against
  // circular references (a cycle is replaced with null); result = JSON.stringify(normalised)
}

class IdempotencyAttempt {
  private key: string | null = null;
  private lastFingerprint: string | null = null;

  // Returns the key to send for THIS submit. Reuses the previous key if `payload`'s
  // fingerprint is unchanged since the last call (i.e. exact retry of the same intent);
  // otherwise mints and remembers a brand-new UUID (a materially different payload -- new
  // amount, new beneficiary, etc. -- must never reuse an old key, or the server returns
  // 409 IDEMPOTENCY_KEY_REUSED for what is actually a *new*, legitimate intent).
  keyFor(payload): string { ... }

  // Call after a confirmed success: the next submit is a brand-new intent (new key).
  complete(): void { this.key = null; this.lastFingerprint = null; }
}
```
Usage pattern in every mutating page (`order-create`, `pay-again`, ...): instantiate one
`IdempotencyAttempt` per logical action/form, call `keyFor(request)` right before sending, call
`complete()` in the success handler, and deliberately **do not** call `complete()` on error -- so a
user who clicks "retry" after a network failure resends the identical key+body and safely gets the
deduplicated result instead of a duplicate order/payment.

---

## 11. Auth guards / interceptors pattern

Angular: `core/interceptors/auth.interceptor.ts`, `core/interceptors/error.interceptor.ts`,
`core/services/token-storage.service.ts`, `core/services/api-error.util.ts` (all reproduced in
full below).

### Token storage

- **Storage key**: `converter.access_token` (in `localStorage`).
- Only the JWT is persisted -- never the password, never anything else.
- `TokenStorageService`: `getToken()` (`localStorage.getItem`), `setToken(token)`
  (`localStorage.setItem`), `clear()` (`localStorage.removeItem`). Backend token lifetime is 2
  hours; storage is cleared immediately on logout.

### Auth header injection (`auth.interceptor.ts`)

- Header name: **`Authorization`**, value **`Bearer <token>`**.
- Only attached when a token exists **and** the outgoing request URL starts with `/api` (i.e.
  never leaks the bearer token to any third-party/non-API request the app might make).

### Global error handling (`error.interceptor.ts`)

- On any `HttpErrorResponse` with `status === 401` **and** URL starting with `/api`: capture
  whether the user was authenticated *before* this call, call `authService.logout()`
  unconditionally, and -- only if they *were* previously authenticated -- navigate to `/login` with
  query param `sessionExpired=true`. (A 401 on an already-anonymous call, e.g. a failed login
  attempt itself, does not trigger a redirect loop.)
- Every other status code (`400`, `404`, `409`, `5xx`, network `0`, ...) is passed through
  unchanged -- each screen handles it locally via `extractErrorMessage`.

### Error message extraction (`api-error.util.ts`, full logic -- mirror exactly)

```ts
function extractErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ErrorResponse | undefined;
    if (body?.message) return body.message;       // 1. prefer the backend's own message
    if (error.status === 404) return 'Ressource introuvable ou inaccessible.';
    if (error.status === 0) return 'Connexion au serveur impossible. Verifiez votre reseau.';
    if (error.status >= 500) return 'Une erreur interne est survenue. Reessayez dans quelques instants.';
  }
  return 'Une erreur inattendue est survenue.';   // catch-all, including non-HTTP errors
}

function errorCode(error: unknown): string | null {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ErrorResponse | undefined;
    return body?.code ?? null;
  }
  return null;
}
```
Notes for the Flutter port:
- Priority order matters: a well-formed backend `message` always wins, regardless of status code.
- `status === 0` is the client's own signal for "no HTTP response at all" (DNS/connectivity
  failure, CORS, server unreachable) -- Dart's equivalent is typically a `SocketException`/timeout
  rather than a numeric status, so branch on the exception type instead.
- There is **no dedicated field-violations (`violations[]`) formatting helper** in this util today
  -- `ErrorResponse.violations` (`{ field, message }[]`) exists on the wire (see Section 12) but the
  current Angular code does not walk it in `extractErrorMessage`; if you want per-field errors in
  Flutter you'll need to read `violations` yourself from the raw `ErrorResponse`, there's no
  existing Angular pattern to mirror for that part specifically.
- `errorCode()` is the way screens branch on a specific `ErrorCode` (e.g.
  `IDEMPOTENCY_KEY_REUSED`, `QUOTE_EXPIRED`) instead of pattern-matching the message string.

---

## 12. Global API conventions

### Base path prefix pattern

Three distinct prefixes coexist under `/api`:
- **`/api/auth`** -- authentication, no version segment.
- **`/api/settings`** -- public settings, no version segment, no auth required.
- **`/api/v1/...`** -- versioned, customer-facing, JWT-authenticated resources (quotes, orders,
  suppliers, wallet, preferred-rates, notifications, business, business-profile, rates,
  rate-alerts).
- **`/api/admin/...`** -- administration, no version segment, `@PreAuthorize("hasRole('ADMIN')")`
  (audit-logs, cost-rates, orders, payments, rates, settings, treasury, users, settlements). The
  mobile customer app should never need these.

Frontend `environment.apiBaseUrl = '/api'` (both dev and prod builds -- the Angular app is served
behind the same origin/reverse proxy as the API; a Flutter client will instead need the full
backend origin, e.g. `https://<host>/api`).

### Pagination -- `PageResponse<T>` (exact field names, confirmed against backend record)

```ts
interface PageResponse<T> {
  content: T[];
  page: number;         // 0-based current page index
  size: number;         // page size requested
  totalElements: number; // long
  totalPages: number;
  first: boolean;
  last: boolean;
}
```
Query params are always `page` (0-based) and `size`; default page size varies per endpoint
(`20` is the most common default, `30` for treasury ledger).

### Success/error envelopes (already known, restated for completeness)
```ts
interface ApiResponse<T> { data: T; message: string; }

interface ErrorResponse {
  timestamp: string; status: number; error: string; code: string;
  message: string; path: string; traceId?: string;
  violations?: { field: string; message: string }[];
}
```

### Date/Instant format

Backend: `spring.jackson.serialization.write-dates-as-timestamps: false`, so every `java.time
.Instant` is serialized via `jackson-datatype-jsr310`'s default ISO-8601 representation. Examples
seen throughout backend tests: `"2026-09-03T12:00:00Z"`, `"2026-08-20T10:00:00Z"`, and with
sub-second precision `"2026-09-03T14:00:00.123456Z"` -- i.e. **UTC with a literal trailing `Z`**
(never a `+HH:mm` offset in this codebase). Parse with a standard ISO-8601 UTC parser
(`DateTime.parse` in Dart handles this natively).

### Decimal wire format -- read carefully

Every Angular model in this codebase types `BigDecimal`-backed backend fields as TypeScript
`string` (e.g. `amountXof: string`, `customerRate: string`, `balance: string`, `feeXof: string`,
etc.) -- this convention is consistent across **every** money/rate field across Quote, Order,
Supplier, Settlement, Treasury, Wallet, and PreferredRate, with exactly **one** documented
exception found in this codebase: `PreferredRateRequestResponse.gap`, which the Angular model
explicitly annotates as `number` with the comment *"BigDecimal serialise en nombre JSON natif par
Jackson (pas de guillemets), contrairement aux autres champs montant/taux"* (BigDecimal serialized
as a native JSON number, unlike other amount/rate fields).

**Caveat for the Flutter implementer**: no explicit Jackson `BigDecimal`-as-string serializer,
`@JsonFormat` annotation, or `ObjectMapper` customizer was found anywhere in
`backend/src/main/java` or `application*.yml` during this pass -- the quoting behavior implied by
the Angular types could not be independently confirmed by reading backend serialization config.
Treat the Angular type declarations above as the best available evidence of actual wire behavior
(they are unusually precise and the `gap` exception is explicitly called out only because someone
verified it against a real response), but **verify empirically against a live/staging backend
response before finalizing the Flutter JSON models**. Regardless of whether a given field arrives
quoted or bare, always parse every amount/rate field into a fixed-point/Decimal type in Dart
(e.g. the `decimal` package) -- **never** into `double`.
