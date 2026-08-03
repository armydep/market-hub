# 06 · API outline

Every route is under context path `/api`. Full spec at `/api/v3/api-docs`, interactive UI at
`/api/swagger-ui.html`. Cross-user access is always `404`, never `403` — no id enumeration.

## Auth & account

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/auth/register` | public | Mints role TRADER always; a submitted `role` field is silently dropped. |
| POST | `/auth/login` | public | 401 on bad credentials or unknown email (identical body); 403 if blocked or temporarily locked. |
| POST | `/auth/password-reset/request` | public | Identical response whether or not the email exists. |
| POST | `/auth/password-reset/confirm` | public | Clears any active lockout on success. |
| GET | `/account` | authenticated | Caller's own account only. |
| PATCH | `/account` | authenticated | Change email; requires current password; 409 if the new email is taken. |
| POST | `/account/password` | authenticated | Requires current password. |
| GET | `/account/preferences` | authenticated | Falls back to the app default-visible set until first saved. |
| PUT | `/account/preferences` | authenticated | Full replace; 400 on any key outside the column catalog. |

## Market (public)

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/market/coins` | public | Paginated, sorted, searched — all applied server-side before pagination. |
| GET | `/market/coins/{symbol}` | public | Case-insensitive; 404 on unknown symbol. |
| GET | `/market/columns` | public | Supported columns, default-visible set, supported page sizes. |

## Alerts & notifications

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/alerts` | authenticated | Active alerts, owner-scoped. |
| GET | `/alerts/triggered` | authenticated | Triggered, uncleared alerts. |
| POST | `/alerts` | authenticated | 400 unknown symbol; 400 if already satisfied by the current price. |
| PATCH | `/alerts/{id}` | authenticated | Condition/target only — symbol is immutable. 404 if not found, not yours, or not active. |
| DELETE | `/alerts/{id}` | authenticated | Active alerts only. |
| POST | `/alerts/{id}/clear` | authenticated | Triggered-and-uncleared only. |
| GET | `/notifications` | authenticated | Uncleared, most-recently-triggered first. |
| POST | `/notifications/{id}/clear` | authenticated | Independent of the underlying alert's own cleared state. |

## Admin & operational

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/admin/users` | ADMIN | Paged, no search/sort — intentionally basic. |
| POST | `/admin/users/{id}/block` | ADMIN | Row-locked target read; writes an audit record in the same transaction. |
| POST | `/admin/users/{id}/unblock` | ADMIN | Same idempotency + audit guarantee as block. |
| GET | `/actuator/health`, `/actuator/info` | public | Excluded from request logging. |
| GET | `/v3/api-docs`, `/swagger-ui.html` | public | springdoc. |
