/* ============================================================
   ENTRY · AUTH — role gate + student login + admin login
   Mock auth (no live backend): any input, or "demo access",
   proceeds into the existing dashboard.
   ============================================================ */
(function () {
  const D = window.AID;
  const S = D.summary;

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
              <p className="gc-desc">The whole register at a glance — {S.students} students, {S.subjects} subjects, {S.results.toLocaleString()} results. Distribution, ranking, risk and AI insight.</p>
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
    const [roll, setRoll] = React.useState("");
    const [pass, setPass] = React.useState("");
    const [busy, setBusy] = React.useState(false);
    const go = (e) => { e && e.preventDefault(); setBusy(true); setTimeout(() => onEnter("student"), 520); };
    const demo = () => { setRoll("S007"); setPass("••••••"); setTimeout(go, 260); };

    return (
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
              <div className="blk"><div className="lab">Subjects</div><div className="val num">{S.subjects}</div></div>
              <div className="blk"><div className="lab">Exams</div><div className="val num">{S.exams}</div></div>
              <div className="blk"><div className="lab">Pass mark</div><div className="val num">40%</div></div>
            </div>
          </div>

          <form className="a-right" onSubmit={go}>
            <div className="form-head"><span>Identification</span><span>01 / 02</span></div>
            <div className="field">
              <label htmlFor="s-roll">Roll number</label>
              <input id="s-roll" value={roll} onChange={(e) => setRoll(e.target.value)} placeholder="e.g. S007" autoComplete="off" />
            </div>
            <div className="field">
              <label htmlFor="s-pass">Passcode</label>
              <input id="s-pass" type="password" value={pass} onChange={(e) => setPass(e.target.value)} placeholder="••••••" autoComplete="off" />
            </div>
            <button className="auth-submit" type="submit" data-magnetic="0.3">
              <span data-magnetic-label>{busy ? "Opening record…" : "View my performance"}</span><span>→</span>
            </button>
            <button className="auth-demo" type="button" onClick={demo}>Use demo student access</button>
          </form>
        </div>

        <div className="frame-bot">
          <span>Secure · JWT session</span>
          <span className="mid">Student portal</span>
          <span className="end">Need help? Registrar office</span>
        </div>
      </div>
    );
  }

  /* ============================================================
     ADMIN LOGIN — mission control, high density
     ============================================================ */
  function AdminLogin({ onBack, onEnter }) {
    const [op, setOp] = React.useState("");
    const [key, setKey] = React.useState("");
    const [busy, setBusy] = React.useState(false);
    const go = (e) => { e && e.preventDefault(); setBusy(true); setTimeout(() => onEnter("admin"), 560); };
    const demo = () => { setOp("ADM-01"); setKey("••••••••"); setTimeout(go, 280); };
    const stamp = new Date().toISOString().slice(0, 19).replace("T", " ");

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
              <div className="blk"><div className="lab">Cohort</div><div className="val">2026·S — {S.students} students</div></div>
              <div className="blk"><div className="lab">Index</div><div className="val">{S.results.toLocaleString()} results</div></div>
              <div className="blk"><div className="lab">Flagged</div><div className="val">{S.atRisk} ({S.critical} critical)</div></div>
              <div className="status">
                <div className="row ok"><i></i> Database — online</div>
                <div className="row ok"><i></i> Flyway — migrated</div>
                <div className="row ok"><i></i> Local LLM — ready</div>
                <div className="row"><i></i> Auth — awaiting operator</div>
              </div>
            </aside>

            {/* center — credentials */}
            <section className="mc-main">
              <span className="mc-eyebrow">Restricted · operator clearance</span>
              <h1 className="mc-head" data-elastic="0.05">mission<br />control</h1>
              <form className="mc-form" onSubmit={go}>
                <div className="grid2">
                  <div className="field">
                    <label htmlFor="a-op">Operator ID</label>
                    <input id="a-op" value={op} onChange={(e) => setOp(e.target.value)} placeholder="ADM-01" autoComplete="off" />
                  </div>
                  <div className="field">
                    <label htmlFor="a-key">Passkey</label>
                    <input id="a-key" type="password" value={key} onChange={(e) => setKey(e.target.value)} placeholder="••••••••" autoComplete="off" />
                  </div>
                </div>
                <button className="auth-submit accent" type="submit" data-magnetic="0.3">
                  <span data-magnetic-label>{busy ? "Authenticating…" : "Enter console"}</span><span>→</span>
                </button>
                <button className="auth-demo" type="button" onClick={demo}>Use demo operator clearance</button>
              </form>
            </section>

            {/* right — boot console */}
            <aside className="mc-console">
              <div className="ttl">auth.log</div>
              <div className="l dim">$ aid-auth --role admin --mode interactive</div>
              <div className="l"><span className="pre">›</span> handshake · TLS 1.3 ok</div>
              <div className="l"><span className="pre">›</span> loading cohort 2026S</div>
              <div className="l"><span className="pre">›</span> risk model · converged</div>
              <div className="l"><span className="pre">›</span> {S.results.toLocaleString()} results indexed</div>
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
