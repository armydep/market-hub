# 07 · Frontend route map

Route guards are client-side convenience only — the real gate is always the backend's
`SecurityConfig`/`@PreAuthorize` (`constraints.md`: "UI hiding is not an authorization control").

| Path | Guard | Page | Notes |
|---|---|---|---|
| `/` | public | DashboardPage | Default landing route for everyone. |
| `/coins/:symbol` | public | CoinDetailPage | Not-found state on an unknown symbol. |
| `/register` | public | RegisterPage | |
| `/sign-in` | public | SignInPage | |
| `/forgot-password` | public | ForgotPasswordPage | |
| `/reset-password` | public | ResetPasswordPage | Token carried as a URL param, never logged client-side. |
| `/account` | `RequireAuth` | AccountPage | |
| `/alerts` | `RequireAuth` | AlertsPage | |
| `/notifications` | `RequireAuth` | NotificationsPage | |
| `/admin/users` | `RequireAuth role="ADMIN"` | AdminUsersPage | Wrong role sees an inline "Access denied", not a redirect. |
| `*` | — | EmptyState (404) | |
