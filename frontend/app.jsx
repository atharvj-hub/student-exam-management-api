/* ============================================================
   APP — shell, masthead, rail, student drawer, navigation,
         theme, clock + GSAP motion integration.
   ============================================================ */
(function () {
  const { AppCtx } = window;
  const AUTH = window.AUTH;

  const NAV = [
    ["overview", "Dashboard"],
    ["students", "Students"],
    ["insights", "AI Insights"],
  ];

  /* ---- clock (isolated so its 1s tick never re-renders the views) ------- */
  function Clock() {
    const [clock, setClock] = React.useState("00:00:00");
    React.useEffect(() => {
      const tick = () => setClock(new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit" }));
      tick();
      const id = setInterval(tick, 1000);
      return () => clearInterval(id);
    }, []);
    return <b className="num">{clock}</b>;
  }

  /* ---- masthead --------------------------------------------------------- */
  function Masthead({ theme, onToggleTheme, session, onLogout }) {
    const user = session && session.user;
    return (
      <header className="masthead">
        <div className="mast-brand" data-magnetic="0.2">
          <div className="mast-mark" data-magnetic-label></div>
          <div className="mast-title">Academic Intelligence<span>Performance system · 2026·S</span></div>
        </div>
        <div className="mast-meta">
          {user && (
            <div className="mast-clock mast-id"><span>{session.role === "admin" ? "Operator" : "Student"}</span><b className="num">{user.id}</b></div>
          )}
          <div className="mast-clock"><span>Local</span><Clock /></div>
          <button className="theme-toggle" data-magnetic="0.3" onClick={onToggleTheme}>
            <span className="dot"></span><span className="tlabel" data-magnetic-label>{theme === "dark" ? "Light" : "Dark"}</span>
          </button>
          <button className="theme-toggle signout" data-magnetic="0.3" onClick={onLogout}>
            <span className="dot"></span><span className="tlabel" data-magnetic-label>Sign out</span>
          </button>
        </div>
      </header>
    );
  }

  /* ---- left rail nav (scoped to the session's role) ---------------------- */
  function Rail({ current, onNav, role }) {
    const items = NAV.filter(([key]) => AUTH.canAccess(role, key));
    return (
      <aside className="rail">
        <div className="rail-num">Index · {String(items.length).padStart(2, "0")} views</div>
        <nav className="nav">
          {items.map(([key, label], i) => (
            <button className="nav-item" key={key} data-dir-aware aria-current={current === key} onClick={() => onNav(key)}>
              <span className="nav-idx">{String(i + 1).padStart(2, "0")}</span>
              <span className="nav-label" data-magnetic-label>{label}</span>
            </button>
          ))}
        </nav>
        <div className="rail-foot">
          Spring Boot 3<br /><b>·</b> PostgreSQL · Flyway<br /><b>·</b> JWT · AOP audit<br /><b>·</b> Local LLM insights
        </div>
      </aside>
    );
  }

  /* ---- scroll-linked editorial typography (Phases 3/4) -----------------
     Giant editorial words that mask-reveal + drift as the view scrolls.
     Words drawn from the spec: ANALYTICS · PERFORMANCE · AI INSIGHTS ·
     PASS RATE · RISK INDEX. Overview is a full-bleed poster (no scroll)
     so it is intentionally excluded. */
  const SCROLL_WORDS = {
    overview: [["dashboard", "33%", { left: "-2%" }], ["pass rate", "71%", { right: "-2%" }]],
    insights: [["ai insights", "46%", { left: "-2%" }]],
    students: [["performance", "56%", { left: "-2%" }]],
  };
  function ScrollTypeLayer({ view }) {
    const items = SCROLL_WORDS[view];
    if (!items) return null;
    return (
      <div className="scroll-type-layer" aria-hidden="true">
        {items.map(([w, top, pos], i) => (
          <span key={view + i} className="stl-word" data-scroll-type data-scroll-par={0.12 + i * 0.06} style={{ top, ...pos }}>{w}</span>
        ))}
      </div>
    );
  }

  /* ---- app -------------------------------------------------------------- */
  const VIEWS = {
    overview: window.Dashboard,
    students: window.Students,
    insights: window.Insights,
  };

  /* ---- 403 — role does not include this console area --------------------- */
  const NAV_LABELS = Object.fromEntries(NAV);
  function Forbidden({ denied, role, onNav }) {
    return (
      <div className="forbidden" data-screen-label="403 Forbidden">
        <span className="f-code">403 · Forbidden</span>
        <h2>operator clearance<br />required</h2>
        <p>
          {NAV_LABELS[denied] ? "“" + NAV_LABELS[denied] + "”" : "This area"} is restricted to administrator
          sessions. Your {role} access covers personal performance views only — your record, analytics,
          subjects and results.
        </p>
        <button className="f-back" onClick={() => onNav("overview")}>Back to overview <span>→</span></button>
      </div>
    );
  }

  function App({ view, denied, session, onNav, onLogout }) {
    const role = session ? session.role : "student";
    const [theme, setTheme] = React.useState(() => localStorage.getItem("aid-theme") || "light");
    const [selected, setSelected] = React.useState(null);
    const lastId = React.useRef(null);
    const rootRef = React.useRef(null);

    /* navigation + drawer actions (route changes live in the URL hash) */
    const nav = React.useCallback((key) => {
      setSelected(null);
      onNav(key);
    }, [onNav]);
    const openStudent = React.useCallback((id) => { lastId.current = id; setSelected(id); }, []);
    const closeStudent = React.useCallback(() => setSelected(null), []);
    const ctx = React.useMemo(() => ({ nav, openStudent }), [nav, openStudent]);

    /* theme */
    React.useEffect(() => {
      document.documentElement.setAttribute("data-theme", theme);
      localStorage.setItem("aid-theme", theme);
    }, [theme]);

    /* esc closes drawer */
    React.useEffect(() => {
      const h = (e) => { if (e.key === "Escape") setSelected(null); };
      document.addEventListener("keydown", h);
      return () => document.removeEventListener("keydown", h);
    }, []);

    /* per-view: persist, scroll to top, restart entrance, rescan motion */
    React.useEffect(() => {
      if (view !== "forbidden") localStorage.setItem("aid-view", view);
      const root = rootRef.current;
      if (root) {
        root.classList.remove("entering");
        void root.offsetWidth;
        root.classList.add("entering");
      }
      window.scrollTo(0, 0);
      if (window.MOTION) {
        // Phase 5/6 — opt the existing dashboard into the elevated motion
        // vocabulary without touching every view: major headlines get the
        // elastic hover, chart/analytics cards get direction-aware fill.
        document.querySelectorAll(
          ".section-head h2:not([data-elastic]), .hero-line:not([data-elastic]), .insight-body h3:not([data-elastic])"
        ).forEach((el) => el.setAttribute("data-elastic", "0.04"));
        document.querySelectorAll(".chart-block:not([data-dir-aware])")
          .forEach((el) => el.setAttribute("data-dir-aware", ""));
        // Phases 3/4 — major editorial headings mask-reveal on scroll
        document.querySelectorAll(".section-head h2:not([data-scroll-type])")
          .forEach((el) => el.setAttribute("data-scroll-type", ""));
        window.MOTION.scan(document);
      }
    }, [view]);

    /* drawer: rescan motion when a student record opens */
    React.useEffect(() => {
      if (selected && window.MOTION) {
        const el = document.getElementById("student-detail");
        if (el) window.MOTION.scan(el);
      }
    }, [selected]);

    const View = view === "forbidden" ? null : (VIEWS[view] || VIEWS.overview);
    const detailId = selected || lastId.current;

    return (
      <AppCtx.Provider value={ctx}>
        <div id="app">
          <Masthead theme={theme} onToggleTheme={() => setTheme(theme === "dark" ? "light" : "dark")} session={session} onLogout={onLogout} />
          <div className="shell">
            <Rail current={view} onNav={nav} role={role} />
            <main className="canvas">
              <div id="view-root" className="view is-active entering" ref={rootRef}>
                <ScrollTypeLayer view={view} />
                <div className="view-content">
                  {View ? <View /> : <Forbidden denied={denied} role={role} onNav={nav} />}
                </div>
              </div>
              <footer className="foot">
                <div>Live data · Spring Boot API<br />JWT · AOP audit · AI insights</div>
              </footer>
            </main>
          </div>
        </div>

        <div className={"sd-scrim" + (selected ? " open" : "")} id="sd-scrim" onClick={closeStudent}></div>
        <aside className={"student-detail" + (selected ? " open" : "")} id="student-detail">
          {detailId && <window.StudentDetail id={detailId} onClose={closeStudent} />}
        </aside>
      </AppCtx.Provider>
    );
  }

  // Exported for the entry router (entry.jsx) to mount as the "app" stage.
  window.AppMain = App;
})();
