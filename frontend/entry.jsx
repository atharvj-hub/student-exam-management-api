/* ============================================================
   ENTRY — kinetic typography intro (Phase 2) + Root router.
   Flow:  intro → access gate → student|admin login → dashboard
   Routes mirror /student/login and /admin/login via hash.
   ============================================================ */
(function () {
  const D = window.AID;
  const S = D.summary;
  const { RoleGate, StudentLogin, AdminLogin, Atmosphere } = window;

  const WORDS = [
    { t: "academic", depth: 0 },
    { t: "intelligence", depth: 1, accent: true },
    { t: "system", depth: 2 },
  ];

  function Chars({ text }) {
    return [...text].map((c, i) => <span className="ch" key={i}>{c}</span>);
  }

  /* ============================================================
     KINETIC INTRO
     ============================================================ */
  function Intro({ onDone }) {
    const ref = React.useRef(null);
    const onDoneRef = React.useRef(onDone);
    onDoneRef.current = onDone;

    React.useEffect(() => {
      const root = ref.current;
      const words = [...root.querySelectorAll(".intro-word")];
      const kicker = root.querySelector(".intro-kicker");
      const meta = root.querySelector(".intro-meta");
      const allChars = [...root.querySelectorAll(".ch")];
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      let doneT, safetyT, advanced = false;
      const advance = () => { if (advanced) return; advanced = true; onDoneRef.current(); };
      // wall-clock safety: if the rAF ticker is throttled/stalled, force the
      // end-state visible and advance anyway — never strand on a blank hero.
      const forceVisible = () => {
        if (window.gsap) gsap.set(allChars, { clearProps: "all" });
        kicker.style.opacity = 1; meta.style.opacity = 1;
      };

      // bind grid-distortion + bg-type parallax for this stage immediately
      if (window.MOTION) window.MOTION.scan(root);

      if (reduce || !window.gsap) {
        forceVisible();
        doneT = setTimeout(advance, 1800);
        return () => clearTimeout(doneT);
      }

      safetyT = setTimeout(() => { forceVisible(); advance(); }, 7000);

      gsap.set(allChars, { yPercent: 122, opacity: 0 });

      const tl = gsap.timeline({
        onComplete: () => {
          // PERSISTENT LAYERED MOTION — after the lock, hand the words to the
          // parallax (mouse depth) + float (continuous) systems so foreground
          // type keeps drifting independently and the scene is never static.
          const depth = [0.12, 0.2, 0.08];
          const fl = [0.55, 0.75, 0.45];
          const fy = [2.6, 3.2, 2.2];
          const fr = [-0.5, 0.6, -0.4];
          words.forEach((w, i) => {
            w.setAttribute("data-parallax", String(depth[i] ?? 0.1));
            w.setAttribute("data-float", String(fl[i] ?? 0.5));
            w.setAttribute("data-float-x", "0");
            w.setAttribute("data-float-y", String(fy[i] ?? 2.4));
            w.setAttribute("data-float-rot", String(fr[i] ?? 0));
          });
          if (window.MOTION) window.MOTION.scan(root);
          doneT = setTimeout(advance, 1900);
        },
      });
      // 1 — characters reveal, word by word
      tl.to(kicker, { opacity: 1, duration: 0.6, ease: "power2.out" }, 0);
      words.forEach((w, i) => {
        tl.to(w.querySelectorAll(".ch"),
          { yPercent: 0, opacity: 1, duration: 0.9, ease: "power4.out", stagger: 0.035 },
          0.18 + i * 0.3);
      });
      // 2 — words drift independently
      const drift = [[-20, 7, -0.6], [15, -9, 0.5], [-9, 11, -0.35]];
      tl.to(words, {
        x: (i) => drift[i][0], y: (i) => drift[i][1], rotation: (i) => drift[i][2],
        duration: 0.85, ease: "power2.inOut", stagger: 0.05,
      }, ">-0.15");
      // 3 — lock into the grid
      tl.to(words, { x: 0, y: 0, rotation: 0, duration: 1.0, ease: "power4.out", stagger: 0.04 }, ">-0.15");
      // 4 — metadata / login affordance fades in
      tl.to(meta, { opacity: 1, duration: 0.7, ease: "power2.out" }, "<0.2");

      return () => { tl.kill(); clearTimeout(doneT); clearTimeout(safetyT); };
    }, []);

    return (
      <div className="entry-stage" data-screen-label="Intro" ref={ref}>
        <div className="grid-distort-host" data-grid-distort data-grid-cols="12" data-grid-rows="7" style={{ position: "absolute", inset: 0, zIndex: 0 }}></div>
        <div className="type-bg" aria-hidden="true">
          <span data-parallax="0.05" style={{ top: "-8%", left: "-4%" }}>performance</span>
          <span data-parallax="0.08" style={{ bottom: "-14%", right: "-6%" }}>analytics</span>
        </div>
        <Atmosphere variant="intro" />

        <div className="frame-top">
          <span className="frame-mark"><b></b> Academic Intelligence System</span>
          <span className="mid">Booting</span>
          <span className="end">Spring term · 2026·S</span>
        </div>

        <div className="intro-core">
          <span className="intro-kicker">Performance Intelligence · v2.6</span>
          <div className="intro-stack">
            {WORDS.map((w) => (
              <span key={w.t} className={"intro-word" + (w.accent ? " accent" : "")}>
                <Chars text={w.t} />
              </span>
            ))}
          </div>

          <div className="intro-meta">
            <div className="col-a">
              <div className="lab">What this is</div>
              <p>Not a student portal — a performance intelligence layer over the exam register. It reads {S.results.toLocaleString()} results across {S.students} students to surface who leads, what's failing, and who needs intervention.</p>
            </div>
            <div className="col-b">
              <div className="lab">Boot sequence</div>
              <div className="console-mini">
                <div className="l"><span className="pre">›</span> connect · postgres ok</div>
                <div className="l"><span className="pre">›</span> flyway · migrated</div>
                <div className="l"><span className="pre">›</span> risk model · converged</div>
                <div className="l dim">// ready — select access</div>
              </div>
            </div>
            <div className="col-c">
              <div className="lab">Cohort</div>
              <div className="big num">2026·S</div>
            </div>
          </div>
        </div>

        <div className="frame-bot">
          <span>Local LLM insights</span>
          <span className="mid">Initialising interface</span>
          <span className="end"><button className="skip" onClick={onDone}>Skip intro →</button></span>
        </div>
      </div>
    );
  }

  /* ============================================================
     ROOT ROUTER
     ============================================================ */
  const HASH = { intro: "#/", gate: "#/access", student: "#/student/login", admin: "#/admin/login", app: "#/app" };
  const fromHash = () => {
    const h = location.hash || "#/";
    return Object.keys(HASH).find((k) => HASH[k] === h);
  };
  function initialStage() {
    const h = fromHash();
    if (h === "app") return "app";
    if (h && h !== "intro") return h;
    return sessionStorage.getItem("aid-introSeen") ? "gate" : "intro";
  }

  function Root() {
    const [stage, setStage] = React.useState(initialStage);

    React.useEffect(() => {
      if (HASH[stage] && location.hash !== HASH[stage]) location.hash = HASH[stage];
      if (stage !== "intro") sessionStorage.setItem("aid-introSeen", "1");
    }, [stage]);

    React.useEffect(() => {
      const onHash = () => { const s = fromHash(); if (s && s !== stage) setStage(s); };
      window.addEventListener("hashchange", onHash);
      return () => window.removeEventListener("hashchange", onHash);
    }, [stage]);

    // rescan motion when an entry stage mounts (binds dir-aware/elastic/grid)
    React.useEffect(() => {
      if (stage !== "app" && window.MOTION) {
        const id = requestAnimationFrame(() => window.MOTION.scan(document));
        return () => cancelAnimationFrame(id);
      }
    }, [stage]);

    const enterApp = React.useCallback((role) => {
      sessionStorage.setItem("aid-role", role);
      sessionStorage.setItem("aid-introSeen", "1");
      setStage("app");
    }, []);

    if (stage === "app") {
      const App = window.AppMain;
      return <App />;
    }

    return (
      <div className="entry">
        {stage === "intro" && <Intro onDone={() => setStage("gate")} />}
        {stage === "gate" && <RoleGate onChoose={(r) => setStage(r)} onReplay={() => setStage("intro")} />}
        {stage === "student" && <StudentLogin onBack={() => setStage("gate")} onEnter={enterApp} />}
        {stage === "admin" && <AdminLogin onBack={() => setStage("gate")} onEnter={enterApp} />}
      </div>
    );
  }

  ReactDOM.createRoot(document.getElementById("root")).render(<Root />);
})();
