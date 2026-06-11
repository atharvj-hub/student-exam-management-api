/* ============================================================
   ENTRY · AUTH — role gate + student login + admin login
   Mock auth (no live backend): any input, or "demo access",
   proceeds into the existing dashboard.
   ============================================================ */
(function () {
  const AUTH = window.AUTH;

  /* ---- shared auth feedback line (error / notice / success) ---------- */
  function AuthMsg({ kind, children }) {
    if (!children) return null;
    return (
      <div className={"auth-msg " + kind} role={kind === "error" ? "alert" : "status"}>{children}</div>
    );
  }

  /* ---- shared login submit machinery ---------------------------------
     One state machine for both login pages:
     idle → busy → ok (→ onEnter) | error (message, back to idle).
     The submit guard prevents double-fire while a request is in flight. */
  function useLogin(role, onEnter) {
    const [busy, setBusy] = React.useState(false);
    const [ok, setOk] = React.useState(false);
    const [error, setError] = React.useState(null);
    const [notice] = React.useState(() => AUTH.consumeNotice()); // expiry / signout reason
    const alive = React.useRef(true);
    React.useEffect(() => () => { alive.current = false; }, []);

    // creds = { email, password }. One real auth path for both screens:
    // the backend JWT decides the actual role, not the screen the user picked.
    const submit = React.useCallback(async (creds, emptyMsg) => {
      if (busy || ok) return;
      setError(null);
      if (!String(creds.email || "").trim() || !String(creds.password || "")) {
        setError(emptyMsg || "Enter your email and password.");
        return;
      }
      setBusy(true);
      try {
        const session = await AUTH.login(creds.email, creds.password);
        if (!alive.current) return;
        setOk(true);
        setTimeout(() => { if (alive.current) onEnter(session); }, 850);
      } catch (err) {
        if (!alive.current) return;
        setBusy(false);
        setError(err && err.message ? err.message : "Sign-in failed — please try again.");
      }
    }, [busy, ok, onEnter]);

    /* page-level validation failures (e.g. unticked declaration) */
    const fail = React.useCallback((msg) => { if (!busy && !ok) setError(msg); }, [busy, ok]);

    return { busy, ok, error, notice, submit, fail };
  }

  /* ============================================================
     MACHINE STAGE — Rube Goldberg interaction layer (shared by
     both login pages). Geometry contract with login-machine.js:
     270px form, four 60px rows, form + 1000×1000 SVG centred on
     the same 0×0 anchor. React owns values/validity/auth; the
     machine owns choreography (gears · sprayer · spiral · hammer
     · car · grabbing hand · pull system · submit docking).
     ============================================================ */
  function MachineStage({ inkRGB, idPrefix, ph1, ph2, auto1, agreeLabel, submitLabel, v1, v2, set1, set2, valid1, valid2, agree, setAgree, busy, ok, error }) {
    const stageRef = React.useRef(null);
    const m = React.useRef(null);

    React.useEffect(() => {
      if (window.createLoginMachine) m.current = window.createLoginMachine(stageRef.current, { inkRGB });
      window.__machine = m.current; // debug/test handle (inert-safe)
      return () => { if (m.current) { m.current.destroy(); m.current = null; } };
    }, []);
    React.useEffect(() => { if (m.current) m.current.setPrimary(valid1); }, [valid1]);
    React.useEffect(() => { if (m.current) m.current.setSecondary(valid2); }, [valid2]);
    React.useEffect(() => { if (m.current) m.current.setCheckbox(agree); }, [agree]);
    React.useEffect(() => { if (ok && m.current) m.current.celebrate(); }, [ok]);
    React.useEffect(() => { if (error && m.current) m.current.reject(); }, [error]);

    return (
      <div className="rg-stage" ref={stageRef}>
        <div className="rg-anchor">
          <div className="rg-svg" aria-hidden="true"></div>
          <div className="rg-form">
            <label className="rg-row">
              <input className={"rg-input" + (valid1 ? " valid" : "")} id={idPrefix + "-id"} value={v1} onChange={(e) => set1(e.target.value)} placeholder={ph1} autoComplete={auto1 || "username"} disabled={busy || ok} />
            </label>
            <label className="rg-row">
              <input className={"rg-input" + (valid2 ? " valid" : "")} id={idPrefix + "-secret"} type="password" value={v2} onChange={(e) => set2(e.target.value)} placeholder={ph2} autoComplete="current-password" disabled={busy || ok} />
            </label>
            <label className="rg-row rg-check">
              <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} disabled={busy || ok} />
              <span>{agreeLabel}</span>
            </label>
            <div className="rg-row">
              <input type="submit" className="rg-submit" value={ok ? "Access granted" : busy ? "Verifying…" : submitLabel} disabled={busy || ok} />
            </div>
          </div>
        </div>
      </div>
    );
  }

  /* background typographic layer (Phase 3) ----------------------------- */
  function TypeBg({ words }) {
    return (
      <div className="type-bg" aria-hidden="true">
        {words.map((w, i) => (
          <span key={i} data-parallax={w.p} data-scroll-par={w.p} style={w.style}>{w.t}</span>
        ))}
      </div>
    );
  }

  /* atmospheric background objects (Phase: layered motion scene) -------
     Floating vertical bars, circles and abstract geometric forms across
     three depth bands. Each carries data-parallax (mouse depth) AND
     data-float (a continuous looping GSAP timeline) — different speeds,
     opacities and depths, so the scene drifts perpetually. */
  function Atmosphere({ variant = "a" }) {
    return (
      <div className={"atmos atmos-" + variant} aria-hidden="true">
        {/* back band — slow, faint */}
        <span className="obj bar lg" data-parallax="0.04" data-float="0.55" data-float-x="0" data-float-y="6" style={{ left: "17%", top: "-8%" }}></span>
        <span className="obj ring lg" data-parallax="0.05" data-float="0.5" data-float-y="5" data-float-rot="6" style={{ right: "8%", top: "10%" }}></span>
        <span className="obj disc" data-parallax="0.06" data-float="0.7" data-float-y="8" style={{ left: "6%", bottom: "15%" }}></span>
        {/* mid band */}
        <span className="obj bar md" data-parallax="0.10" data-float="1" data-float-y="10" style={{ left: "61.8%", top: "6%" }}></span>
        <span className="obj diamond" data-parallax="0.11" data-float="1.05" data-float-y="9" data-float-rot="-8" style={{ right: "23%", bottom: "19%" }}></span>
        <span className="obj ring md" data-parallax="0.12" data-float="0.9" data-float-y="7" data-float-rot="5" style={{ left: "31%", bottom: "7%" }}></span>
        {/* front band — quicker, crisper */}
        <span className="obj sq" data-parallax="0.17" data-float="1.4" data-float-y="15" data-float-rot="4" style={{ right: "13%", top: "31%" }}></span>
        <span className="obj bar sm" data-parallax="0.16" data-float="1.5" data-float-y="17" style={{ left: "43%", top: "58%" }}></span>
        <span className="obj dot" data-parallax="0.2" data-float="1.7" data-float-y="20" style={{ left: "79%", top: "68%" }}></span>
      </div>
    );
  }

  /* combined backdrop: distortable Swiss grid + atmosphere ------------- */
  function Backdrop({ variant, cols = 12, rows = 7 }) {
    return (
      <React.Fragment>
        <div className="grid-distort-host" data-grid-distort data-grid-cols={cols} data-grid-rows={rows} style={{ position: "absolute", inset: 0, zIndex: 0 }}></div>
        <Atmosphere variant={variant} />
      </React.Fragment>
    );
  }

  /* ============================================================
     ROLE GATE
     ============================================================ */
  function RoleGate({ onChoose, onReplay }) {
    const [notice] = React.useState(() => AUTH.consumeNotice());
    return (
      <div className="entry-stage" data-screen-label="Role Gate">
        <TypeBg words={[
          { t: "access", p: 0.05, style: { top: "-6%", left: "-4%" } },
          { t: "intelligence", p: 0.08, style: { bottom: "-12%", right: "-8%" } },
        ]} />
        <Backdrop variant="gate" cols={8} rows={5} />
        <div className="frame-top">
          <span className="frame-mark"><b></b> Academic Intelligence System</span>
          <span className="mid">Access Control</span>
          <span className="end">Select operating role</span>
        </div>
        {notice && <div className="entry-notice"><AuthMsg kind="notice">{notice}</AuthMsg></div>}

        <div className="gate-core">
          <button className="gate-col student" data-dir-aware onClick={() => onChoose("student")}>
            <div>
              <span className="gc-idx">01 — Student</span>
              <h2 className="gc-title" data-elastic="0.045">personal<br />performance</h2>
            </div>
            <div>
              <p className="gc-desc">Your results, subject by subject. Grade trajectory, pass standing and where to focus next — read clearly, without noise.</p>
              <div className="gc-tags"><span>Roll number</span><span>Subject averages</span><span>Grade trend</span></div>
              <div className="gc-enter">Enter student access <span className="arr">→</span></div>
            </div>
          </button>

          <button className="gate-col admin" data-dir-aware onClick={() => onChoose("admin")}>
            <div>
              <span className="gc-idx">02 — Administrator</span>
              <h2 className="gc-title" data-elastic="0.045">cohort<br />intelligence</h2>
            </div>
            <div>
              <p className="gc-desc">The whole register at a glance — every student, subject and exam result, live from the API. Totals, trends, standing and AI insight.</p>
              <div className="gc-tags"><span>Mission control</span><span>Risk model</span><span>Local LLM</span></div>
              <div className="gc-enter">Enter admin console <span className="arr">→</span></div>
            </div>
          </button>
        </div>

        <div className="frame-bot">
          <span>Spring Boot 3 · PostgreSQL · JWT</span>
          <span className="mid">2026 · Spring term</span>
          <span className="end"><button className="auth-demo" onClick={onReplay}>Replay intro</button></span>
        </div>
      </div>
    );
  }

  /* ============================================================
     STUDENT LOGIN — clean, minimal, academic
     ============================================================ */
  function StudentLogin({ onBack, onEnter }) {
    const [email, setEmail] = React.useState("");
    const [pass, setPass] = React.useState("");
    const [agree, setAgree] = React.useState(false);
    const { busy, ok, error, notice, submit, fail } = useLogin("student", onEnter);

    /* visual validity — drives the machine; auth still verifies server-side */
    const valid1 = /^\S+@\S+\.\S+$/.test(email.trim());
    const valid2 = pass.length >= 6;

    const go = (e) => {
      e && e.preventDefault();
      if (!agree) { fail("Tick the declaration — the machine needs it to hand you the button."); return; }
      submit({ email: email, password: pass }, "Enter your email and password.");
    };
    const demo = () => {
      setEmail("admin@example.com"); setPass("Admin123!"); setAgree(true);
      submit({ email: "admin@example.com", password: "Admin123!" }); // explicit creds — not stale state
    };

    return (
      <div className="auth student">
        <div className="entry-stage" data-screen-label="Student Login">
        <TypeBg words={[{ t: "results", p: 0.05, style: { bottom: "-14%", left: "-3%" } }]} />
        <Backdrop variant="student" cols={10} rows={6} />
        <button className="auth-back" onClick={onBack}>← Access control</button>
        <div className="frame-top">
          <span className="frame-mark"><b></b> Academic Intelligence</span>
          <span className="mid">Student Access</span>
          <span className="end">2026 · S</span>
        </div>

        <div className="auth-core">
          <div className="a-left">
            <span className="a-eyebrow">Personal performance</span>
            <h1 className="a-head" data-elastic="0.05">your<br /><span className="accent">record</span></h1>
            <p className="a-sub">Sign in with your roll number to open your academic profile — every exam, every subject, your grade trend and pass standing.</p>
            <div className="a-foot">
              <div className="blk"><div className="lab">Auth</div><div className="val">JWT</div></div>
              <div className="blk"><div className="lab">API</div><div className="val">Spring Boot</div></div>
              <div className="blk"><div className="lab">Pass mark</div><div className="val num">40%</div></div>
            </div>
          </div>

          <form className="a-right rg-host" onSubmit={go} noValidate>
            <div className="form-head"><span>Identification</span><span>01 / 02</span></div>
            <div className="rg-msgs">
              <AuthMsg kind="notice">{notice}</AuthMsg>
              <AuthMsg kind="error">{error}</AuthMsg>
              <AuthMsg kind="ok">{ok ? "Access granted — opening your record." : null}</AuthMsg>
              {!notice && !error && !ok && <div className="rg-hint">Every valid input sets the machine in motion</div>}
            </div>
            <MachineStage
              inkRGB="17,18,14"
              idPrefix="s"
              ph1="Email · e.g. admin@example.com"
              ph2="Password"
              auto1="username"
              agreeLabel="I confirm this is my own record"
              submitLabel="View my performance"
              v1={email} v2={pass} set1={setEmail} set2={setPass}
              valid1={valid1} valid2={valid2}
              agree={agree} setAgree={setAgree}
              busy={busy} ok={ok} error={error}
            />
            <button className="auth-demo" type="button" onClick={demo} disabled={busy || ok}>Use demo student access</button>
          </form>
        </div>

        <div className="frame-bot">
          <span>Secure · JWT session</span>
          <span className="mid">Student portal</span>
          <span className="end">Need help? Registrar office</span>
        </div>
        </div>
      </div>
    );
  }

  /* ============================================================
     ADMIN LOGIN — mission control, high density
     ============================================================ */
  function AdminLogin({ onBack, onEnter }) {
    const [email, setEmail] = React.useState("");
    const [key, setKey] = React.useState("");
    const [agree, setAgree] = React.useState(false);
    const { busy, ok, error, notice, submit, fail } = useLogin("admin", onEnter);

    /* visual validity — drives the machine; auth still verifies server-side */
    const valid1 = /^\S+@\S+\.\S+$/.test(email.trim());
    const valid2 = key.length >= 6;

    const go = (e) => {
      e && e.preventDefault();
      if (!agree) { fail("Confirm operator authorization — the machine holds the button until you do."); return; }
      submit({ email: email, password: key }, "Enter your operator email and password.");
    };
    const demo = () => {
      setEmail("admin@example.com"); setKey("Admin123!"); setAgree(true);
      submit({ email: "admin@example.com", password: "Admin123!" }); // explicit creds — not stale state
    };
    const stamp = new Date().toISOString().slice(0, 19).replace("T", " ");
    const authStatus = ok ? "Auth — clearance granted" : busy ? "Auth — verifying…" : error ? "Auth — rejected" : "Auth — awaiting operator";

    return (
      <div className="auth admin">
        <div className="entry-stage" data-screen-label="Admin Login">
          <TypeBg words={[{ t: "control", p: 0.06, style: { top: "-10%", right: "-6%" } }]} />
          <Backdrop variant="admin" cols={12} rows={6} />
          <button className="auth-back" onClick={onBack}>← Access control</button>
          <div className="frame-top">
            <span className="frame-mark"><b></b> AID · Mission Control</span>
            <span className="mid">Administrator Authentication</span>
            <span className="end num">{stamp} UTC</span>
          </div>

          <div className="auth-core">
            {/* left rail — system telemetry */}
            <aside className="mc-rail">
              <div className="blk"><div className="lab">Node</div><div className="val">aid-engine · eu-1</div></div>
              <div className="blk"><div className="lab">Cohort</div><div className="val">2026·S — live register</div></div>
              <div className="blk"><div className="lab">Index</div><div className="val">Spring Boot API</div></div>
              <div className="blk"><div className="lab">Engine</div><div className="val">Local LLM insights</div></div>
              <div className="status">
                <div className="row ok"><i></i> Database — online</div>
                <div className="row ok"><i></i> Flyway — migrated</div>
                <div className="row ok"><i></i> Local LLM — ready</div>
                <div className={"row" + (ok ? " ok" : "")}><i></i> {authStatus}</div>
              </div>
            </aside>

            {/* center — credentials */}
            <section className="mc-main">
              <span className="mc-eyebrow">Restricted · operator clearance</span>
              <h1 className="mc-head" data-elastic="0.05">mission<br />control</h1>
              <form className="mc-form rg-host" onSubmit={go} noValidate>
                <div className="rg-msgs">
                  <AuthMsg kind="notice">{notice}</AuthMsg>
                  <AuthMsg kind="error">{error}</AuthMsg>
                  <AuthMsg kind="ok">{ok ? "Clearance granted — opening console." : null}</AuthMsg>
                  {!notice && !error && !ok && <div className="rg-hint">Every valid input sets the machine in motion</div>}
                </div>
                <MachineStage
                  inkRGB="228,226,214"
                  idPrefix="a"
                  ph1="Operator email · admin@example.com"
                  ph2="Password"
                  auto1="username"
                  agreeLabel="Authorized operator · audited"
                  submitLabel="Enter console"
                  v1={email} v2={key} set1={setEmail} set2={setKey}
                  valid1={valid1} valid2={valid2}
                  agree={agree} setAgree={setAgree}
                  busy={busy} ok={ok} error={error}
                />
                <button className="auth-demo" type="button" onClick={demo} disabled={busy || ok}>Use demo operator clearance</button>
              </form>
            </section>

            {/* right — boot console */}
            <aside className="mc-console">
              <div className="ttl">auth.log</div>
              <div className="l dim">$ aid-auth --role admin --mode interactive</div>
              <div className="l"><span className="pre">›</span> handshake · TLS 1.3 ok</div>
              <div className="l"><span className="pre">›</span> loading cohort 2026S</div>
              <div className="l"><span className="pre">›</span> analytics · ready</div>
              <div className="l"><span className="pre">›</span> live results indexed</div>
              <div className="l dim">// awaiting operator credentials</div>
              <div className="l dim">// session will expire in 30:00</div>
            </aside>
          </div>

          <div className="frame-bot">
            <span>RBAC · admin scope</span>
            <span className="mid">All actions audited (AOP)</span>
            <span className="end">v2.6</span>
          </div>
        </div>
      </div>
    );
  }

  Object.assign(window, { RoleGate, StudentLogin, AdminLogin, Atmosphere, Backdrop });
})();
