/* ============================================================
   VIEWS · B — Students, Subjects, AI Insights, At-Risk,
               Comparison, Student detail drawer
   ============================================================ */
(function () {
  const D = window.AID;
  const { Head, Seg, AppCtx } = window;

  /* ============================================================
     STUDENTS INTELLIGENCE
     ============================================================ */
  function StudentRow({ s, onOpen }) {
    return (
      <tr data-student={s.id} onClick={onOpen}>
        <td className="num" style={{ color: "var(--ink-3)" }}>{s.id}</td>
        <td><b>{s.name}</b></td>
        <td className="u-mono" style={{ fontSize: 12 }}>SEC {s.section}</td>
        <td className="r num">{s.avg}</td>
        <td className="r num">{s.gpa.toFixed(2)}</td>
        <td className="r"><span className={"chip " + (s.grade === "F" || s.grade === "E" ? "f" : s.grade === "A" ? "a" : "")}>{s.grade}</span></td>
        <td className="r num" style={{ color: s.trend < 0 ? "var(--accent)" : "var(--ink)" }}>{s.trend > 0 ? "+" : ""}{s.trend}</td>
        <td><span className={"band " + s.riskBand}><i></i>{s.riskBand}</span></td>
      </tr>
    );
  }

  function Students() {
    const { openStudent } = React.useContext(AppCtx);
    const all = React.useMemo(() => [...D.students].sort((a, b) => b.avg - a.avg), []);
    const [section, setSection] = React.useState("all");
    const [band, setBand] = React.useState("all");
    const [q, setQ] = React.useState("");

    const query = q.trim().toLowerCase();
    const rows = all.filter((s) => {
      const okSec = section === "all" || s.section === section;
      const okBand = band === "all" || s.riskBand === band;
      const okQ = !query || s.name.toLowerCase().includes(query);
      return okSec && okBand && okQ;
    });

    return (
      <section data-screen-label="Students">
        <Head idx="03" title="students" sub="100 academic profiles. Sort, filter, and open any record for a full subject-by-subject read." />
        <div className="filters">
          <div className="filter-grp"><span className="fl">section</span>
            <Seg value={section} setValue={setSection} options={[["all", "All"], ["A", "A"], ["B", "B"]]} />
          </div>
          <div className="filter-grp"><span className="fl">risk</span>
            <Seg value={band} setValue={setBand} options={[["all", "All"], ["critical", "Critical"], ["elevated", "Elevated"], ["stable", "Stable"]]} />
          </div>
          <div className="filter-grp" style={{ flex: 1 }}><span className="fl">find</span>
            <input className="search-in" placeholder="type a name…" autoComplete="off" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <div className="filter-grp"><span className="fl">shown</span><span className="num" style={{ fontWeight: 700 }}>{rows.length}</span></div>
        </div>
        <div style={{ overflowX: "auto" }}>
          <table className="tbl">
            <thead><tr>
              <th>ID</th><th>Name</th><th>Section</th>
              <th className="r">Avg</th><th className="r">GPA</th><th className="r">Grade</th>
              <th className="r">Trend</th><th>Risk band</th>
            </tr></thead>
            <tbody>{rows.map((s) => <StudentRow key={s.id} s={s} onOpen={() => openStudent(s.id)} />)}</tbody>
          </table>
        </div>
      </section>
    );
  }

  /* ============================================================
     STUDENT DETAIL DRAWER
     ============================================================ */
  function DetailStat({ label, value, color, last }) {
    return (
      <div style={{ padding: "14px 16px", ...(last ? {} : { borderRight: "1px solid var(--hair)" }) }}>
        <div className="kicker" style={{ color: "var(--ink-3)" }}>{label}</div>
        <div className="num" style={{ fontSize: 30, fontWeight: 700, marginTop: 6, ...(color ? { color } : {}) }}>{value}</div>
      </div>
    );
  }

  function StudentDetail({ id, onClose }) {
    const s = D.students.find((x) => x.id === id);
    if (!s) return null;
    const exams = D.SUBJECTS.flatMap((su) => s.results[su.id].map((r) => ({ ...r, subject: su.id }))).slice(0, 18);
    return (
      <React.Fragment>
        <div style={{ padding: "26px 28px", borderBottom: "2px solid var(--ink)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "start" }}>
            <div>
              <div className="kicker" style={{ color: "var(--accent)" }}>{s.id} · Section {s.section}</div>
              <h2 style={{ fontSize: 34, fontWeight: 700, letterSpacing: "-0.03em", lineHeight: 0.95, marginTop: 12 }}>{s.name}</h2>
            </div>
            <button id="sd-close" className="btn" style={{ padding: "8px 14px" }} data-magnetic="0.4" onClick={onClose}><span data-magnetic-label>Close ✕</span></button>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 0, marginTop: 24, border: "1px solid var(--hair)" }}>
            <DetailStat label="Avg" value={s.avg} />
            <DetailStat label="GPA" value={s.gpa.toFixed(2)} />
            <DetailStat label="Attend" value={s.attendance} />
            <DetailStat label="Risk" value={s.risk} color={s.risk >= 38 ? "var(--accent)" : "var(--ink)"} last />
          </div>
        </div>
        <div style={{ padding: "24px 28px" }}>
          <div className="cell-label">Subject averages <span>%</span></div>
          {D.SUBJECTS.map((su) => {
            const avg = s.subjectAverages[su.id];
            return (
              <div className="rank-row" key={su.id}>
                <span className="ri num">{su.id}</span>
                <span className="rn">{su.name}</span>
                <span className="track"><i style={{ width: avg + "%", ...(avg < D.PASS_MARK ? { background: "var(--accent)" } : {}) }}></i></span>
                <span className="rv num">{avg}</span>
              </div>
            );
          })}
        </div>
        <div style={{ padding: "0 28px 30px" }}>
          <div className="cell-label">Exam history <span>{exams.length} sittings</span></div>
          <table className="tbl">
            <thead><tr><th>Subj</th><th>Exam</th><th>Date</th><th className="r">Score</th><th className="r">Grade</th></tr></thead>
            <tbody>{exams.map((e, i) => (
              <tr key={i}>
                <td className="u-mono" style={{ fontSize: 11, color: "var(--ink-3)" }}>{e.subject}</td>
                <td>{e.title}</td>
                <td className="u-mono" style={{ fontSize: 11 }}>{e.date}</td>
                <td className="r num">{e.score}</td>
                <td className="r"><span className={"chip " + (e.grade === "F" || e.grade === "E" ? "f" : "")}>{e.grade}</span></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      </React.Fragment>
    );
  }

  /* ============================================================
     SUBJECT ANALYTICS
     ============================================================ */
  function Subjects() {
    const stats = [...D.subjectStats].sort((a, b) => b.avg - a.avg);
    const mx = 100;
    return (
      <section data-screen-label="Subjects">
        <Head idx="04" title="subjects" sub="Six disciplines, ranked. Mean, pass rate, section split and the student leading each." />
        <div className="grid" style={{ gridTemplateColumns: "repeat(3,1fr)" }}>
          {stats.map((s, i) => (
            <div className="chart-block" key={s.id} data-magnetic="0.1">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                <div className="u-mono" style={{ fontSize: 11, color: "var(--accent)", letterSpacing: "0.12em" }}>{s.id}</div>
                <div className="u-mono" style={{ fontSize: 11, color: "var(--ink-3)" }}>RANK {String(i + 1).padStart(2, "0")}</div>
              </div>
              <h3 style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-0.02em", margin: "14px 0 18px", lineHeight: 1 }}>{s.name}</h3>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 0, border: "1px solid var(--hair)" }}>
                <div style={{ padding: "12px 14px", borderRight: "1px solid var(--hair)" }}><div className="kicker" style={{ color: "var(--ink-3)" }}>Mean</div><div className="num" style={{ fontSize: 32, fontWeight: 700, marginTop: 6 }}>{s.avg}</div></div>
                <div style={{ padding: "12px 14px" }}><div className="kicker" style={{ color: "var(--ink-3)" }}>Pass</div><div className="num" style={{ fontSize: 32, fontWeight: 700, marginTop: 6, color: s.passRate < 60 ? "var(--accent)" : "var(--ink)" }}>{s.passRate}%</div></div>
              </div>
              <div style={{ marginTop: 18 }} className="versus-bars">
                <div className="ab a"><span>A</span><span className="t"><i data-grow style={{ width: Math.round((s.aAvg / mx) * 100) + "%" }}></i></span><span className="num">{s.aAvg}</span></div>
                <div className="ab b"><span>B</span><span className="t"><i data-grow style={{ width: Math.round((s.bAvg / mx) * 100) + "%" }}></i></span><span className="num">{s.bAvg}</span></div>
              </div>
              <div style={{ marginTop: 18, paddingTop: 14, borderTop: "1px solid var(--hair)", fontSize: 12, color: "var(--ink-2)" }}>
                <span className="u-mono" style={{ fontSize: 10, letterSpacing: "0.1em", color: "var(--ink-3)" }}>TOP</span> {s.topStudent} · <b className="num">{s.topScore}</b>
              </div>
            </div>
          ))}
        </div>
      </section>
    );
  }

  /* ============================================================
     AI INSIGHTS
     ============================================================ */
  function Insights() {
    const s = D.summary;
    return (
      <section data-screen-label="AI Insights">
        <Head idx="05" title="ai insights" sub="Generated from live aggregates by the local LLM. Ranked by intervention weight." />
        <div className="console" data-parallax="-0.04">
          <div className="line dim">$ aid-engine analyze --cohort 2026S --model local-llm</div>
          <div className="line"><span className="pre">›</span> ingested {s.results.toLocaleString()} results · {s.students} students · {s.subjects} subjects</div>
          <div className="line"><span className="pre">›</span> risk model converged · {s.atRisk} flagged ({s.critical} critical)</div>
          <div className="line"><span className="pre">›</span> generated <b>{D.insights.length}</b> insights <span className="dim">// {new Date().toISOString().slice(0, 10)} · 312ms</span></div>
        </div>
        <div className="insights-list">
          {D.insights.map((ins) => (
            <div className="insight" key={ins.id}>
              <div className="insight-tag">
                <div className="t">{ins.tag}</div>
                <div className="w">WEIGHT · {ins.weight}</div>
                <div className="id">{ins.id}</div>
              </div>
              <div className="insight-body">
                <h3>{ins.headline}</h3>
                <p>{ins.body}</p>
              </div>
              <div className="insight-metric">
                <div className="m num">{ins.metric}</div>
                <div className="ml">{ins.metricLabel}</div>
              </div>
            </div>
          ))}
        </div>
      </section>
    );
  }

  /* ============================================================
     AT-RISK
     ============================================================ */
  function reasonFor(s) {
    const r = [];
    if (s.avg < 55) r.push("low average");
    if (s.failing > 0) r.push(`${s.failing} failing subject${s.failing > 1 ? "s" : ""}`);
    if (s.trend < -3) r.push("declining trend");
    if (s.attendance < 82) r.push("attendance");
    return r.slice(0, 3).join(" · ") || "borderline";
  }

  function AtRisk() {
    const { openStudent } = React.useContext(AppCtx);
    const list = D.atRisk;
    const crit = list.filter((s) => s.riskBand === "critical");
    const elev = list.filter((s) => s.riskBand === "elevated");
    const meanRisk = Math.round(list.reduce((a, s) => a + s.risk, 0) / list.length);
    return (
      <section data-screen-label="At-Risk">
        <Head idx="06" title="at-risk" sub="Students crossing the intervention threshold, ranked by composite risk score (0–100)." />
        <div className="risk-hero">
          <div className="risk-stat crit"><div className="kicker" style={{ color: "var(--ink-3)" }}>Critical · score ≥ 60</div><div className="v num" data-count={crit.length}>0</div></div>
          <div className="risk-stat"><div className="kicker" style={{ color: "var(--ink-3)" }}>Elevated · 38–59</div><div className="v num" data-count={elev.length}>0</div></div>
          <div className="risk-stat"><div className="kicker" style={{ color: "var(--ink-3)" }}>Mean risk · flagged</div><div className="v num" data-count={meanRisk}>0</div></div>
        </div>
        <div style={{ overflowX: "auto" }}>
          <table className="tbl">
            <thead><tr><th>Rank</th><th>Student</th><th>Section</th><th className="r">Avg</th><th className="r">Trend</th><th className="r">Attend</th><th>Primary signals</th><th className="r">Risk</th></tr></thead>
            <tbody>{list.map((s, i) => (
              <tr key={s.id} data-student={s.id} onClick={() => openStudent(s.id)}>
                <td className="num" style={{ color: "var(--ink-3)" }}>{String(i + 1).padStart(2, "0")}</td>
                <td><b>{s.name}</b></td>
                <td className="u-mono" style={{ fontSize: 12 }}>SEC {s.section}</td>
                <td className="r num">{s.avg}</td>
                <td className="r num" style={{ color: s.trend < 0 ? "var(--accent)" : "var(--ink)" }}>{s.trend > 0 ? "+" : ""}{s.trend}</td>
                <td className="r num">{s.attendance}</td>
                <td style={{ fontSize: 12, color: "var(--ink-2)" }}>{reasonFor(s)}</td>
                <td className="r"><span className={"band " + s.riskBand} style={{ justifyContent: "flex-end" }}><i></i><b className="num">{s.risk}</b></span></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      </section>
    );
  }

  /* ============================================================
     PERFORMANCE COMPARISON
     ============================================================ */
  function BigStat({ sec, value }) {
    return (
      <div style={{ padding: "24px 26px", borderRight: "1px solid var(--hair)" }}>
        <div className="kicker" style={{ color: "var(--ink-3)" }}>Section {sec}</div>
        <div className="num" style={{ fontSize: "clamp(44px,5vw,76px)", fontWeight: 700, letterSpacing: "-0.04em", marginTop: 10, lineHeight: 0.85 }}>{value}</div>
      </div>
    );
  }

  function Comparison() {
    const { openStudent } = React.useContext(AppCtx);
    const A = D.sectionStats[0], B = D.sectionStats[1];
    return (
      <section data-screen-label="Comparison">
        <Head idx="07" title="comparison" sub="Section A vs B, head to head — and every student plotted by average against attendance." />
        <div className="grid" style={{ gridTemplateColumns: "1fr 1fr", borderBottom: "1px solid var(--hair)" }}>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
            <BigStat sec="A" value={A.avg} /><BigStat sec="A" value={A.passRate + "%"} />
          </div>
          <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
            <BigStat sec="B" value={B.avg} /><BigStat sec="B" value={B.passRate + "%"} />
          </div>
        </div>
        <div className="cmp-grid">
          <div className="chart-block">
            <div className="chart-title"><h3>Mean by subject</h3><span className="meta">A ▪ &nbsp; B ▪</span></div>
            {D.subjectStats.map((s) => (
              <div className="versus-row" key={s.id} style={{ gridTemplateColumns: "60px 1fr", marginBottom: 14 }}>
                <span className="lab">{s.id}</span>
                <div className="versus-bars">
                  <div className="ab a"><span>A</span><span className="t"><i data-grow style={{ width: s.aAvg + "%" }}></i></span><span className="num">{s.aAvg}</span></div>
                  <div className="ab b"><span>B</span><span className="t"><i data-grow style={{ width: s.bAvg + "%" }}></i></span><span className="num">{s.bAvg}</span></div>
                </div>
              </div>
            ))}
          </div>
          <div className="chart-block">
            <div className="chart-title"><h3>Average × attendance</h3><span className="meta">100 students</span></div>
            <div className="scatter">
              <span className="axis" style={{ left: 8, top: 8 }}>high attend</span>
              <span className="axis" style={{ left: 8, bottom: 8 }}>low attend</span>
              <span className="axis" style={{ right: 8, bottom: 8 }}>high avg →</span>
              {D.students.map((s) => {
                const x = (s.avg / 100) * 100;
                const y = 100 - ((s.attendance - 60) / 40) * 100;
                return <span key={s.id} className={"dot " + (s.section === "B" ? "b" : "")} data-student={s.id} style={{ left: x.toFixed(1) + "%", top: y.toFixed(1) + "%" }} data-magnetic="0.6" onClick={() => openStudent(s.id)}></span>;
              })}
            </div>
          </div>
        </div>
      </section>
    );
  }

  Object.assign(window, { Students, Subjects, Insights, AtRisk, Comparison, StudentDetail });
})();
