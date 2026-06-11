/* ============================================================
   AUTH — real session service (Spring Boot JWT).
   Wraps window.API. Single source of truth for: login via
   POST /api/auth/login, JWT storage + expiry, role, logout,
   expiry watch, and cross-redirect notices.

   Storage (localStorage — survives refresh, cleared on logout):
     aid-auth-token  the real backend JWT (HS256)
     aid-auth-role   "admin" | "student"   (mirrors token claim)
     aid-auth-user   JSON { id(email), name, role }

   Session is read from storage once and cached in memory; later
   getSession() calls are pure memory reads that re-check expiry.
   ============================================================ */
(function (global) {
  "use strict";

  const KEYS = { token: "aid-auth-token", role: "aid-auth-role", user: "aid-auth-user" };
  const NOTICE_KEY = "aid-auth-notice";

  /* ---- base64url decode (for reading the JWT payload) ---------------- */
  function b64uDecode(str) {
    str = str.replace(/-/g, "+").replace(/_/g, "/");
    const pad = str.length % 4 === 0 ? "" : "=".repeat(4 - (str.length % 4));
    return decodeURIComponent(escape(atob(str + pad)));
  }

  /* decodeJwt(token) -> { ok, claims } | { ok:false, reason }
     Validates structure + expiry only (the backend already signed it;
     the frontend cannot verify HS256 without the secret, by design). */
  function decodeJwt(token) {
    if (typeof token !== "string") return { ok: false, reason: "invalid" };
    const parts = token.split(".");
    if (parts.length !== 3) return { ok: false, reason: "invalid" };
    let claims;
    try { claims = JSON.parse(b64uDecode(parts[1])); } catch (e) { return { ok: false, reason: "invalid" }; }
    if (!claims || typeof claims.exp !== "number") return { ok: false, reason: "invalid" };
    if (Date.now() >= claims.exp * 1000) return { ok: false, reason: "expired", claims };
    return { ok: true, claims };
  }

  /* The backend JWT carries a `permissions` array, not a role claim.
     Admin-only capabilities (analytics / AI insights) imply the admin role;
     otherwise treat the session as a student. An explicit `role` claim, if
     ever added server-side, takes precedence. */
  function roleFromClaims(claims) {
    if (claims.role) return claims.role.toString().toUpperCase() === "ADMIN" ? "admin" : "student";
    const perms = Array.isArray(claims.permissions) ? claims.permissions : [];
    return (perms.indexOf("AI_INSIGHTS_VIEW") !== -1 || perms.indexOf("ANALYTICS_VIEW") !== -1) ? "admin" : "student";
  }

  /* ---- storage (guarded — privacy modes can throw) ------------------- */
  function storeGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function storeSet(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  function storeDel(k) { try { localStorage.removeItem(k); } catch (e) {} }
  function clearStorage() { storeDel(KEYS.token); storeDel(KEYS.role); storeDel(KEYS.user); }

  /* ---- in-memory session cache --------------------------------------- */
  let cache; // undefined = not read; null = signed out; object = session
  const listeners = new Set();
  function emit() { listeners.forEach((fn) => { try { fn(cache); } catch (e) {} }); }

  function readFromStorage() {
    const token = storeGet(KEYS.token);
    if (!token) return null;
    const t = decodeJwt(token);
    if (!t.ok) {
      clearStorage();
      setNotice(t.reason === "expired"
        ? "Session expired — please sign in again."
        : "Your session was invalid and has been reset. Please sign in again.");
      return null;
    }
    let user = null;
    try { user = JSON.parse(storeGet(KEYS.user) || "null"); } catch (e) { user = null; }
    const role = roleFromClaims(t.claims);
    if (!user) user = { id: t.claims.sub, name: t.claims.sub, role };
    return { token, role, user, exp: t.claims.exp * 1000 };
  }

  function getSession() {
    if (cache === undefined) cache = readFromStorage();
    if (cache && Date.now() >= cache.exp) {
      clearStorage();
      cache = null;
      setNotice("Session expired — please sign in again.");
      emit();
    }
    return cache;
  }

  function getToken() {
    const s = getSession();
    return s ? s.token : null;
  }

  /* No server-side refresh endpoint exists; touch() simply re-validates. */
  function touch() { return getSession(); }

  /* ---- one-shot notice (reason banner across redirects) -------------- */
  function setNotice(msg) { try { sessionStorage.setItem(NOTICE_KEY, msg); } catch (e) {} }
  function consumeNotice() {
    try {
      const m = sessionStorage.getItem(NOTICE_KEY);
      if (m) sessionStorage.removeItem(NOTICE_KEY);
      return m || null;
    } catch (e) { return null; }
  }

  /* ---- login ----------------------------------------------------------
     login(email, password) -> Promise<session>
     Rejects with an ApiError (.kind: NETWORK | UNAUTHORIZED | …). */
  async function login(email, password) {
    let resp;
    try {
      resp = await global.API.login(email, password);
    } catch (err) {
      // normalise: 401 from the backend means bad credentials
      if (err && err.kind === "UNAUTHORIZED") {
        err.message = "Invalid email or password.";
      } else if (err && err.kind === "NETWORK") {
        err.message = "Sign-in service unreachable — is the backend running?";
      }
      throw err;
    }
    const token = resp.token;
    const t = decodeJwt(token);
    if (!t.ok) {
      const e = new Error("The server returned an invalid token.");
      e.kind = "SERVER";
      throw e;
    }
    // LoginResponse.role is authoritative ("ADMIN"/"STUDENT"); fall back to
    // inferring from the token's permissions if the field is absent.
    const role = resp.role ? (resp.role.toUpperCase() === "ADMIN" ? "admin" : "student") : roleFromClaims(t.claims);
    const user = { id: resp.email || t.claims.sub, name: resp.email || t.claims.sub, role };
    storeSet(KEYS.token, token);
    storeSet(KEYS.role, role);
    storeSet(KEYS.user, JSON.stringify(user));
    cache = { token, role, user, exp: t.claims.exp * 1000 };
    emit();
    return cache;
  }

  function logout(reasonMessage) {
    clearStorage();
    cache = null;
    if (reasonMessage) setNotice(reasonMessage);
    emit();
  }

  /* ---- expiry watch (single instance) -------------------------------- */
  let watchId = null;
  function startExpiryWatch(onExpired) {
    stopExpiryWatch();
    const check = () => {
      const had = cache;
      const s = getSession();
      if (had && !s && typeof onExpired === "function") onExpired();
    };
    watchId = setInterval(check, 30 * 1000);
    document.addEventListener("visibilitychange", check);
    startExpiryWatch._visHandler = check;
    return stopExpiryWatch;
  }
  function stopExpiryWatch() {
    if (watchId) { clearInterval(watchId); watchId = null; }
    if (startExpiryWatch._visHandler) {
      document.removeEventListener("visibilitychange", startExpiryWatch._visHandler);
      startExpiryWatch._visHandler = null;
    }
  }

  function subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }

  /* ---- role scopes ----------------------------------------------------
     The analytics endpoints require ADMIN permissions on the backend, so
     the student-facing register / insights views are admin-only here too.
     Students keep the read-only overview. */
  const ADMIN_ONLY_VIEWS = ["students", "insights"];
  function canAccess(role, view) {
    if (role === "admin") return true;
    return ADMIN_ONLY_VIEWS.indexOf(view) === -1;
  }

  global.AUTH = {
    login, logout, getSession, getToken, touch, subscribe,
    startExpiryWatch, stopExpiryWatch,
    setNotice, consumeNotice,
    canAccess, ADMIN_ONLY_VIEWS,
    _decodeJwt: decodeJwt,
  };
})(window);
