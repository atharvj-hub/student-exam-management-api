/* ============================================================
   VIEWS · A — Overview poster + Analytics Dashboard
   ============================================================ */
(function () {
  const D = window.AID;
  const { StatCell, Block, AppCtx } = window;
  const maxBy = (arr, f) => Math.max(...arr.map(f));

  /* ============================================================
     OVERVIEW / POSTER
     ============================================================ */
  function Overview() {
    const { nav } = React.useContext(AppCtx);
    const s = D.summary;
    return (
      <section className="poster" data-screen-label="Overview">
        <div className="grid-distort-host" data-grid-distort data-grid-cols="12" data-grid-rows="6" style={{ position: "absolute", top: 0, left: 0, right: 0, height: "72%", zIndex: 0 }}></div>
        <div className="poster-grid" style={{ position: "relative", zIndex: 1 }}>
          <div className="hero-type" data-parallax="0.06">
            <span className="kicker" style={{ color: "var(--accent)" }} data-reveal="" data-reveal-delay="0.1">Spring Boot · PostgreSQL · Local LLM · v2.6</span>
            <div style={{ marginTop: 18 }}>
              <span className="hero-line xl" data-reveal="chars" data-reveal-delay="0.2" data-reveal-stagger="0.04">academic</span>
              <span className="hero-line xl accent" data-reveal="chars" data-reveal-delay="0.45" data-reveal-stagger="0.04">intelligence</span>
            </div>
          </div>

          <div className="hero-sub hair-t">
            <p style={{ maxWidth: "46ch", fontSize: 15, lineHeight: 1.5, color: "var(--ink-2)" }} data-reveal="rise" data-reveal-delay="0.6">
              Not a student portal. A performance intelligence system reading{" "}
              <b style={{ color: "var(--ink)" }}>{s.results.toLocaleString()}</b> exam results across{" "}
              <b style={{ color: "var(--ink)" }}>{s.students}</b> students to surface who leads, what's failing, and who needs intervention — now.
            </p>
            <div data-reveal="rise" data-reveal-delay="0.7">
              <div className="kicker" style={{ color: "var(--ink-3)" }}>cohort</div>
              <div className="num" style={{ fontSize: 40, fontWeight: 700, letterSpacing: "-0.03em", marginTop: 8 }}>2026·S</div>
            </div>
            <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "flex-end" }} data-reveal="rise" data-reveal-delay="0.8">
              <button className="btn accent" data-magnetic="0.5" onClick={() => nav("dashboard")}>
                <span data-magnetic-label>Enter dashboard</span><span className="arrow">→</span>
              </button>
            </div>
          </div>

          <div className="metric-strip" style={{ gridColumn: "1/13" }}>
            <StatCell k="Students" value={s.students} unit="across 2 sections" raw={s.students} delay={0.2} />
            <StatCell k="Subjects" value={s.subjects} unit="6 disciplines" raw={s.subjects} delay={0.3} />
            <StatCell k="Exams" value={s.exams} unit="3 per subject" raw={s.exams} delay={0.4} />
            <StatCell k="Pass rate" value={s.passRate} unit={"cohort average " + s.overallAvg} raw={s.passRate} suffix="%" delay={0.5} />
            <StatCell k="At-risk" value={s.atRisk} unit={s.critical + " critical"} raw={s.atRisk} flag delay={0.6} />
          </div>
        </div>
      </section>
    );
  }

  /* ============================================================
     DASHBOARD — chart renderers
     ============================================================ */
  function GradeBars() {
    const g = D.gradeDistribution;
    const mx = maxBy(g, (x) => x.count);
    return (
      <div className="gradebars">
        {g.map((x) => (
          <div className="col" key={x.grade}>
            <div className={"b" + (x.grade === "F" ? " f" : "")} data-grow="y" style={{ height: Math.round((x.count / mx) * 100) + "%" }}></div>
            <div className="lab"><span className="g">{x.grade}</span><span className="c num">{x.count}</span></div>
          </div>
        ))}
      </div>
    );
  }

  function SubjectRanking() {
    const r = [...D.subjectStats].sort((a, b) => b.avg - a.avg);
    const mx = maxBy(r, (x) => x.avg);
    return r.map((s, i) => (
      <div className="rank-row" key={s.id}>
        <span className="ri num">{String(i + 1).padStart(2, "0")}</span>
        <span className="rn">{s.name}</span>
        <span className="track"><i data-grow style={{ width: Math.round((s.avg / mx) * 100) + "%", ...(i === r.length - 1 ? { background: "var(--accent)" } : {}) }}></i></span>
        <span className="rv num">{s.avg}</span>
      </div>
    ));
  }

  function Versus() {
    const subs = D.subjectStats;
    const mx = 100;
    return (
      <div className="versus">
        {subs.map((s) => (
          <div className="versus-row" key={s.id}>
            <span className="lab">{s.id}</span>
            <div className="versus-bars">
              <div className="ab a"><span>A</span><span className="t"><i data-grow style={{ width: Math.round((s.aAvg / mx) * 100) + "%" }}></i></span><span className="num">{s.aAvg}</span></div>
              <div className="ab b"><span>B</span><span className="t"><i data-grow style={{ width: Math.round((s.bAvg / mx) * 100) + "%" }}></i></span><span className="num">{s.bAvg}</span></div>
            </div>
          </div>
        ))}
      </div>
    );
  }

  function TopPerformers() {
    const { openStudent } = React.useContext(AppCtx);
    return D.topPerformers.map((s, i) => (
      <div className="perf-row" key={s.id} data-student={s.id} onClick={() => openStudent(s.id)}>
        <span className="pi num">{String(i + 1).padStart(2, "0")}</span>
        <span className="pn">{s.name}</span>
        <span className="ps">SEC {s.section} · GPA {s.gpa.toFixed(2)}</span>
        <span className="pv num">{s.avg}</span>
      </div>
    ));
  }

  function WeakestSubjects() {
    const r = [...D.subjectStats].sort((a, b) => a.avg - b.avg).slice(0, 3);
    return r.map((s, i) => (
      <div className="rank-row" key={s.id}>
        <span className="ri num" style={{ color: "var(--accent)" }}>{String(i + 1).padStart(2, "0")}</span>
        <span className="rn">{s.name}</span>
        <span className="track"><i data-grow style={{ width: s.passRate + "%", background: "var(--accent)" }}></i></span>
        <span className="rv num">{s.passRate}%</span>
      </div>
    ));
  }

  function RecentActivity() {
    return (
      <div className="activity">
        {D.recentActivity.map((e) => (
          <div className="act-row" key={e.id}>
            <span><b>{e.subject}</b></span>
            <span className="as">{e.title} · {e.date}</span>
            <span className="as">avg</span><span className="av num">{e.avg}</span>
            <span className="num" style={{ color: e.passRate >= 60 ? "var(--ink)" : "var(--accent)", fontFamily: "'SF Mono',monospace", fontSize: 12, fontWeight: 700 }}>{e.passRate}%</span>
          </div>
        ))}
      </div>
    );
  }

  function DashStats() {
    const s = D.summary;
    const size = "clamp(32px,3.6vw,54px)";
    return (
      <div className="dash-stats">
        <StatCell k="Students" value={s.students} unit="enrolled" raw={s.students} size={size} />
        <StatCell k="Pass rate" value={s.passRate} unit={"≥ " + D.PASS_MARK + " threshold"} raw={s.passRate} suffix="%" size={size} />
        <StatCell k="Cohort avg" value={s.overallAvg} unit="of 100" raw={s.overallAvg} size={size} />
        <StatCell k="Exams" value={s.exams} unit="graded" raw={s.exams} size={size} />
        <StatCell k="At-risk" value={s.atRisk} unit={s.critical + " critical"} raw={s.atRisk} flag size={size} />
      </div>
    );
  }

  /* --- layout A: editorial broadsheet ---------------------------------- */
  function DashLayoutA() {
    return (
      <React.Fragment>
        <div className="grid" style={{ gridTemplateColumns: "1.6fr 1fr" }}>
          <Block title="Grade distribution" meta={"n=" + D.summary.students}><GradeBars /></Block>
          <Block title="Weakest subjects" meta="by pass rate"><WeakestSubjects /></Block>
        </div>
        <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
          <Block title="Subject performance ranking" meta="mean score"><SubjectRanking /></Block>
          <Block title="Section A vs B" meta="mean by subject"><Versus /></Block>
        </div>
        <div className="grid" style={{ gridTemplateColumns: "1fr 1.4fr" }}>
          <Block title="Top performers" meta="by average"><TopPerformers /></Block>
          <Block title="Recent exam activity" meta="latest sittings"><RecentActivity /></Block>
        </div>
      </React.Fragment>
    );
  }

  /* --- layout B: dense terminal matrix --------------------------------- */
  function DashLayoutB() {
    return (
      <React.Fragment>
        <div className="grid" style={{ gridTemplateColumns: "repeat(3,1fr)" }}>
          <Block title="Grade distribution" meta={"n=" + D.summary.students}><GradeBars /></Block>
          <Block title="Subject ranking" meta="mean"><SubjectRanking /></Block>
          <Block title="Section A vs B" meta="by subject"><Versus /></Block>
        </div>
        <div className="grid" style={{ gridTemplateColumns: "repeat(3,1fr)" }}>
          <Block title="Top performers" meta="average"><TopPerformers /></Block>
          <Block title="Weakest subjects" meta="pass rate"><WeakestSubjects /></Block>
          <Block title="Recent activity" meta="latest"><RecentActivity /></Block>
        </div>
      </React.Fragment>
    );
  }

  function Dashboard() {
    const [layout, setLayout] = React.useState(() => localStorage.getItem("aid-layout") || "A");
    const bodyRef = React.useRef(null);
    React.useEffect(() => {
      localStorage.setItem("aid-layout", layout);
      if (window.MOTION && bodyRef.current) window.MOTION.scan(bodyRef.current);
    }, [layout]);

    return (
      <section className="dash" data-screen-label="Dashboard">
        <div className="section-head">
          <span className="idx">02 / 07</span>
          <div><h2 data-reveal="chars" data-reveal-stagger="0.02">analytics</h2></div>
          <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 12 }}>
            <p className="sub">Whole-cohort read in one screen — distribution, ranking, section split, momentum.</p>
            <div className="layout-switch" role="group" aria-label="Layout">
              <button aria-pressed={layout === "A"} onClick={() => setLayout("A")}>Editorial</button>
              <button aria-pressed={layout === "B"} onClick={() => setLayout("B")}>Terminal</button>
            </div>
          </div>
        </div>
        <DashStats />
        <div id="dash-body" ref={bodyRef}>
          {layout === "A" ? <DashLayoutA /> : <DashLayoutB />}
        </div>
      </section>
    );
  }

  Object.assign(window, { Overview, Dashboard });
})();
