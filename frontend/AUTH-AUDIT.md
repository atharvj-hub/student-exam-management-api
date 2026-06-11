# Authentication Audit & Stabilization Report
**Academic Intelligence — React frontend · June 2026 sprint**
Scope: auth completion + hardening only. No UI redesign, no new visual effects, no layout changes.

---

## Step 1 — Audit findings (before this sprint)

### ✅ Working correctly
- Entry flow rendering: intro → role gate → student/admin login screens
- Hash routes for entry stages (`#/`, `#/access`, `#/student/login`, `#/admin/login`)
- `aid-introSeen` (sessionStorage) — intro skipped on return visits
- Theme + last-view persistence (`aid-theme`, `aid-view`)
- Loading ("Opening record…" / "Authenticating…") label on submit buttons
- Clock isolated in its own component (1s tick doesn't re-render views)

### ⚠️ Partially implemented
- **Login submit**: button had a busy label, but *any* input (including empty) "logged in" after a fixed `setTimeout` — no validation, no error path, no success state
- **Role storage**: `aid-role` was written to sessionStorage on entry… and never read anywhere. No role had any effect on access
- **Routing**: `#/app` existed, but the seven console views were internal state only — not addressable, not guarded, not deep-linkable

### ❌ Broken
- **Anonymous dashboard access**: navigating directly to `#/app` rendered the full admin dashboard with no session check at all
- **Student login page layout**: `StudentLogin` was missing its `auth student` scope wrapper, so none of its CSS (`.auth.student …`) applied — the page rendered unstyled/stacked (confirmed against snapshot)
- **hashchange listener**: re-subscribed on every stage change (`[stage]` dependency) — listener churn on each navigation

### ❌ Missing
- JWT issuance, storage, parsing, signature check, expiry — "JWT" existed only as copy on the screens
- Logout — no control, no clearing of state
- Role protection — students could reach every admin view
- Error handling — no 401, no network-failure, no expired/invalid-token path; failures would be silent
- Session expiry watch / refresh handling
- Reason banners ("session expired", "signed out") across redirects

---

## What was changed (file by file)

| File | Change |
|---|---|
| `auth.js` **(new)** | Session service: mock JWT (signed `header.payload.sig`, 30-min `exp`), localStorage persistence (`aid-auth-token/-role/-user`), in-memory session cache (one storage read per load), `login()` with simulated round-trip + error taxonomy, `logout()`, sliding renewal (`touch()`), expiry watch, one-shot notices, `canAccess(role, view)` |
| `entry.jsx` | Router rewritten: route table for all console views, auth guard on every app route, legacy `#/app` alias, unknown-hash fallback (no blank screens, no loops), single hashchange listener, expiry watch while in app, login → land on last permitted view |
| `entry-auth.jsx` | Both login forms wired to `AUTH.login`: validation, error / loading / success states, notice banners, double-submit guard, demo buttons pass explicit credentials (no stale-state race). **Fixed missing `auth student` wrapper** (restores intended layout). Admin telemetry row now reflects auth state |
| `app.jsx` | App is now route-driven (view from URL hash). Rail nav filtered by role; masthead shows session identity (role + ID) and a **Sign out** control; 403 panel for admin-only views under a student session |
| `entry.css` | Added state styles only: `.auth-msg` (error/notice/ok), `.entry-notice`, disabled form states, `.signout`, `.forbidden` (403) |
| `index.html` | Loads `auth.js` after `data.js` |

---

## Route map (after)

| URL | View | Access |
|---|---|---|
| `#/` | Kinetic intro | public (skipped if seen/signed in) |
| `#/access` | Role gate | public |
| `#/student/login` | Student login | public |
| `#/admin/login` | Admin login | public |
| `#/dashboard` | Overview | student + admin |
| `#/analytics` | Analytics | student + admin |
| `#/subjects` | Subjects | student + admin |
| `#/results` | Comparison | student + admin |
| `#/students` | Student register | **admin only** → 403 for students |
| `#/ai-insights` | AI insights | **admin only** → 403 for students |
| `#/at-risk` | Risk register | **admin only** → 403 for students |
| `#/app` (legacy) | — | redirects: session → `#/dashboard`, else `#/access` |
| anything else | — | one safe redirect, never a loop |

## Token model
- Mock JWT: base64url header + payload (`sub`, `role`, `name`, `iat`, `exp`) + deterministic signature (demo stand-in for HMAC — **not** cryptographic; a real backend signs server-side)
- TTL 30:00 (matches the "session will expire in 30:00" console line)
- **Sliding renewal**: any navigation with <5:00 remaining re-issues the token — active users are never logged out mid-task
- Tampered/malformed token → purged, redirect to gate with "session invalid" notice
- Expired token (idle) → watcher (30s interval + tab-visibility check) purges and redirects to the role's login with "session expired" notice

## Error handling
| Case | Behaviour |
|---|---|
| Empty fields | Inline validation message, no request |
| Unknown roll / operator ID, short passcode | 401-style inline error, friendly copy |
| Offline (`navigator.onLine === false`) | "Network unavailable — check your connection and try again." |
| Expired JWT | Auto-logout + notice on login screen |
| Invalid/tampered JWT | Purge + notice on role gate |
| Student on admin route (403) | Editorial 403 panel + back-to-overview |
| Anonymous on any app route | Redirect to access gate with notice |

## Performance fixes
- hashchange listener bound **once** (was re-bound per stage)
- Session cached in memory — localStorage read once per page load, not per check
- Expiry watch is a single guarded instance (no duplicate intervals)
- Login submit guarded against double-fire while a request is in flight
- Unmount-safe async login (no setState on unmounted form)

---

## Step 9 — Test checklist

Demo credentials — student: any real roll `S001`–`S100` + passcode ≥4 chars (or "demo access" button). Admin: `ADM-01` (or `admin`) + passkey ≥6 chars.

- ✓ Student login works (valid roll → token + role + user stored → lands on dashboard)
- ✓ Student wrong roll (e.g. `X999`) → inline 401 error, stays on form
- ✓ Admin login works (`ADM-01`) → admin console, full 7-view nav
- ✓ Admin wrong ID format → inline 401 error
- ✓ Empty submit (both forms) → validation message, no request
- ✓ Logout (masthead) → token/role/user cleared, redirected to access gate with notice
- ✓ Anonymous `#/dashboard` (or any app route) → redirected to gate, "sign in" notice
- ✓ Anonymous legacy `#/app` → redirected to gate
- ✓ Student at `#/students`, `#/ai-insights`, `#/at-risk` → 403 panel (no admin data rendered); nav hides those views
- ✓ Admin at all seven routes → renders
- ✓ Browser refresh while signed in → still signed in, same view (token from localStorage, hash preserved)
- ✓ Expired token → auto-redirect to role login with "session expired"
- ✓ Tampered token in localStorage → purged, "session invalid" notice
- ✓ Unknown hash → safe redirect, no blank screen, no loop
- ✓ No console-only failures: every auth failure has a visible, friendly message
