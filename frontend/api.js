/* ============================================================
   API — real backend client (Spring Boot, JWT).
   Single source of truth for HTTP. Adds the Bearer token,
   normalises every backend status into a typed ApiError, and
   exposes one method per endpoint the UI actually uses.

   Backend base URL resolves from (in order):
     window.AID_API_BASE  →  localStorage 'aid-api-base'  →  :8080
   ============================================================ */
(function (global) {
  "use strict";

  const BASE =
    global.AID_API_BASE ||
    (function () { try { return localStorage.getItem("aid-api-base"); } catch (e) { return null; } })() ||
    "http://localhost:8080";

  /* ---- typed error ----------------------------------------------------
     kind drives user-facing copy; status is the raw HTTP code.
       NETWORK        fetch threw (server down / CORS / offline)
       UNAUTHORIZED   401 — token missing/expired/invalid
       FORBIDDEN      403 — authenticated but lacks permission
       NOT_FOUND      404
       BAD_REQUEST    400 (e.g. student has no results yet)
       RATE_LIMIT     429 — analytics bucket exhausted
       AI_BAD_GATEWAY 502 — model returned unusable output
       AI_UNAVAILABLE 503 — model provider unreachable
       SERVER         500 / anything else
  */
  function ApiError(kind, status, message) {
    const e = new Error(message || kind);
    e.name = "ApiError";
    e.kind = kind;
    e.status = status || 0;
    return e;
  }

  function kindForStatus(status) {
    switch (status) {
      case 400: return "BAD_REQUEST";
      case 401: return "UNAUTHORIZED";
      case 403: return "FORBIDDEN";
      case 404: return "NOT_FOUND";
      case 429: return "RATE_LIMIT";
      case 502: return "AI_BAD_GATEWAY";
      case 503: return "AI_UNAVAILABLE";
      default:  return status >= 500 ? "SERVER" : "SERVER";
    }
  }

  /* ---- core request --------------------------------------------------- */
  async function request(path, opts) {
    opts = opts || {};
    const headers = { Accept: "application/json" };
    if (opts.body !== undefined) headers["Content-Type"] = "application/json";

    // attach bearer token unless explicitly anonymous (login)
    if (!opts.anonymous && global.AUTH && global.AUTH.getToken) {
      const tok = global.AUTH.getToken();
      if (tok) headers.Authorization = "Bearer " + tok;
    }

    let res;
    try {
      res = await fetch(BASE + path, {
        method: opts.method || "GET",
        headers,
        body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
        // backend CORS uses allowCredentials(true); credentials kept default
        // 'same-origin' would drop the Authorization preflight match, so omit.
      });
    } catch (netErr) {
      throw ApiError("NETWORK", 0, "Cannot reach the analytics service.");
    }

    if (res.status === 204) return null;

    let payload = null;
    const text = await res.text();
    if (text) { try { payload = JSON.parse(text); } catch (e) { payload = text; } }

    if (!res.ok) {
      const serverMsg = payload && typeof payload === "object" ? payload.message : null;
      throw ApiError(kindForStatus(res.status), res.status, serverMsg);
    }
    return payload;
  }

  /* ---- endpoint methods ---------------------------------------------- */
  const API = {
    BASE,
    ApiError,
    request,

    // POST /api/auth/login  → { token, tokenType, role, email, expiresIn }
    login(email, password) {
      return request("/api/auth/login", {
        method: "POST",
        anonymous: true,
        body: { email: email, password: password },
      });
    },

    // GET /api/students?page&size  → PagedResponse<StudentResponse>
    listStudents(page, size) {
      const p = page || 0, s = size || 100;
      return request("/api/students?page=" + p + "&size=" + s + "&sort=id,asc");
    },

    // GET /api/results?page&size  → PagedResponse<ResultResponse>
    listResults(page, size) {
      const p = page || 0, s = size || 500;
      return request("/api/results?page=" + p + "&size=" + s);
    },

    // GET /api/results/student/{id}  → ResultResponse[]
    resultsByStudent(id) {
      return request("/api/results/student/" + id);
    },

    // GET /api/exams  → ExamResponse[]
    listExams() {
      return request("/api/exams");
    },

    // GET /api/analytics/students/{id}/summary  → StudentPerformanceSummaryResponse
    summary(id) {
      return request("/api/analytics/students/" + id + "/summary");
    },

    // GET /api/analytics/students/{id}/insights  → StudentInsightsResponse
    insights(id) {
      return request("/api/analytics/students/" + id + "/insights");
    },
  };

  /* ---- friendly copy for each error kind (used by views) -------------- */
  API.messageFor = function (err) {
    const k = err && err.kind ? err.kind : "SERVER";
    switch (k) {
      case "NETWORK":        return "Can’t reach the server. Is the backend running on " + BASE + "?";
      case "UNAUTHORIZED":   return "Your session has expired. Please sign in again.";
      case "FORBIDDEN":      return "This account doesn’t have permission to view analytics.";
      case "NOT_FOUND":      return "Not found.";
      case "BAD_REQUEST":    return err.message || "This student has no recorded results yet.";
      case "RATE_LIMIT":     return "Too many requests — the analytics rate limit was hit. Wait a minute and retry.";
      case "AI_BAD_GATEWAY": return "The AI model returned an unusable response. Try regenerating in a moment.";
      case "AI_UNAVAILABLE": return "The AI service is temporarily unavailable. The numbers above are still accurate.";
      default:               return err && err.message ? err.message : "Something went wrong. Please try again.";
    }
  };

  global.API = API;
})(window);
