  /* ============================================================
   VIEWS · B — Students (Screen 2), Student Detail (Screen 3),
               AI Insights (Screen 4).
   All numbers come from the real backend:
     · list standing  → aggregated from GET /api/results (raw scores)
     · detail         → GET /api/analytics/students/{id}/summary
     · trend chart    → GET /api/results/student/{id}
     · AI insight     → GET /api/analytics/students/{id}/insights
   Nothing is fabricated client-side.
   ============================================================ */
(function () {
  const { Head, Seg, Loading, ErrorState, Badge, Block, TrendChart, AppCtx, useAsync } = window;

  /* ---- shared helpers ------------------------------------------------- */
  function num(v, d) { return v == null ? "—" : Number(v).toFixed(d == null ? 1 : d); }
  function trendTone(t) {
    return t === "IMPROVING" ? "ok" : t === "DECLINING" ? "bad" : t === "VOLATILE" ? "warn" : "muted";
  }
  // Standing derived transparently from REAL average + trend (labelled in UI).
  function standingFor(avg, trend) {
    if (avg == null) return { key: "none", label: "no data", tone: "muted" };
    if (avg < 50 || (trend === "DECLINING" && avg < 60)) return { key: "critical", label: "At risk", tone: "bad" };
    if (avg < 65 || trend === "VOLATILE") return { key: "watch", label: "Watch", tone: "warn" };
    return { key: "stable", label: "On track", tone: "ok" };
  }

  /* ============================================================
     STUDENTS (Screen 2)
     ============================================================ */
  function loadStudents() {
    return Promise.all([
      window.API.listStudents(0, 200),
      window.API.listResults(0, 2000),
    ]).then(([students, results]) => {
      const rows = (results && results.content) || [];
      const byStudent = {};
      rows.forEach((r) => {
        const id = r.student && r.student.id;
        if (id == null) return;
        (byStudent[id] = byStudent[id] || []).push(r);
      });
      return (students.content || []).map((s) => {
        const rs = byStudent[s.id] || [];
        let avg = null, trend = "STABLE", passRate = null;
        if (rs.length) {
          const sorted = [...rs].sort((a, b) =>
            String((a.exam && a.exam.examDate) || "").localeCompare(String((b.exam && b.exam.examDate) || "")));
          const vals = sorted.map((r) => Number(r.percentage));
          avg = vals.reduce((a, b) => a + b, 0) / vals.length;
          passRate = Math.round((rs.filter((r) => r.status === "PASS").length / rs.length) * 100);
          if (vals.length >= 3) {
            const third = Math.max(1, Math.floor(vals.length / 3));
            const early = vals.slice(0, third).reduce((a, b) => a + b, 0) / third;
            const late = vals.slice(-third).reduce((a, b) => a + b, 0) / third;
            const swing = Math.max(...vals) - Math.min(...vals);
            const delta = late - early;
            trend = delta > 2 ? "IMPROVING" : delta < -2 ? "DECLINING" : swing > 28 ? "VOLATILE" : "STABLE";
          }
        }
        return {
          id: s.id, name: s.name, rollNumber: s.rollNumber, section: s.section || "—",
          avg, passRate, trend, standing: standingFor(avg, trend),
        };
      }).sort((a, b) => (b.avg || -1) - (a.avg || -1));
    });
  }

  function Students() {
    const { openStudent } = React.useContext(AppCtx);
    const { loading, data, error, reload } = useAsync(loadStudents, []);
    const [section, setSection] = React.useState("all");
    const [stand, setStand] = React.useState("all");
    const [q, setQ] = React.useState("");

    const query = q.trim().toLowerCase();
    const rows = (data || []).filter((s) => {
      const okSec = section === "all" || s.section === section;
      const okStand = stand === "all" || s.standing.key === stand;
      const okQ = !query || s.name.toLowerCase().includes(query) || String(s.rollNumber).toLowerCase().includes(query);
      return okSec && okStand && okQ;
    });

    return (
      <section data-screen-label="Students">
        <Head idx="02 / 03" title="students" sub="Every enrolled student with their real average, trend and standing. Open any record for the full analytics read." />
        {loading && <Loading label="Loading students and results…" />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && (
          <React.Fragment>
            <div className="filters">
              <div className="filter-grp"><span className="fl">section</span>
                <Seg value={section} setValue={setSection} options={[["all", "All"], ["A", "A"], ["B", "B"]]} />
              </div>
              <div className="filter-grp"><span className="fl">standing</span>
                <Seg value={stand} setValue={setStand} options={[["all", "All"], ["critical", "At risk"], ["watch", "Watch"], ["stable", "On track"]]} />
              </div>
              <div className="filter-grp" style={{ flex: 1 }}><span className="fl">find</span>
                <input className="search-in" placeholder="name or roll…" autoComplete="off" value={q} onChange={(e) => setQ(e.target.value)} />
              </div>
              <div className="filter-grp"><span className="fl">shown</span><span className="num" style={{ fontWeight: 700 }}>{rows.length}</span></div>
            </div>
            <div style={{ overflowX: "auto" }}>
              <table className="tbl">
                <thead><tr>
                  <th>Roll</th><th>Name</th><th>Section</th>
                  <th className="r">Avg score</th><th className="r">Pass %</th><th>Trend</th><th>Standing</th>
                </tr></thead>
                <tbody>
                  {rows.map((s) => (
                    <tr key={s.id} data-student={s.id} onClick={() => openStudent(s.id)} style={{ cursor: "pointer" }}>
                      <td className="num" style={{ color: "var(--ink-3)" }}>{s.rollNumber}</td>
                      <td><b>{s.name}</b></td>
                      <td className="u-mono" style={{ fontSize: 12 }}>SEC {s.section}</td>
                      <td className="r num">{num(s.avg)}</td>
                      <td className="r num">{s.passRate == null ? "—" : s.passRate + "%"}</td>
                      <td><Badge tone={trendTone(s.trend)}>{s.trend.toLowerCase()}</Badge></td>
                      <td><Badge tone={s.standing.tone}>{s.standing.label}</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="foot-note">Average, pass % and trend are aggregated from real recorded results. “Standing” is derived from those values — not a fabricated risk model. The authoritative AI risk flag is on the <b>AI Insights</b> screen.</p>
          </React.Fragment>
        )}
      </section>
    );
  }

  /* ============================================================
     STUDENT DETAIL (Screen 3) — analytics summary + trend chart
     ============================================================ */
  function loadDetail(id) {
    return Promise.all([
      window.API.summary(id),
      window.API.resultsByStudent(id).catch(() => []),
    ]).then(([summary, results]) => ({ summary, results }));
  }

  function DetailStat({ label, value, tone }) {
    return (
      <div className="d-stat">
        <div className="kicker" style={{ color: "var(--ink-3)" }}>{label}</div>
        <div className={"num d-stat-v" + (tone ? " " + tone : "")}>{value}</div>
      </div>
    );
  }

  function StudentDetail({ id, onClose }) {
    const { nav } = React.useContext(AppCtx);
    const { loading, data, error, reload } = useAsync(() => loadDetail(id), [id]);

    const goInsight = () => {
      const name = data && data.summary ? data.summary.name : null;
      window.__insightStudent = { id, name };
      onClose && onClose();
      nav("insights");
    };

    return (
      <div className="sd-inner">
        <div className="sd-head">
          <button id="sd-close" className="btn small" onClick={onClose}><span>Close ✕</span></button>
          {loading && <Loading label="Loading student analytics…" />}
          {error && <ErrorState error={error} onRetry={reload} />}
          {data && data.summary && (
            <div>
              <div className="kicker" style={{ color: "var(--accent)" }}>{data.summary.rollNumber} · Section {data.summary.section || "—"}</div>
              <h2 className="sd-name">{data.summary.name}</h2>
            </div>
          )}
        </div>

        {data && data.summary && (
          <React.Fragment>
            <div className="d-stat-row">
              <DetailStat label="Overall avg" value={num(data.summary.overallAverage)} />
              <DetailStat label="Pass rate" value={num(data.summary.passRate, 0) + "%"} />
              <DetailStat label="Exams" value={data.summary.examsTaken} />
              <DetailStat label="Trend" value={(data.summary.overallTrend || "—").toLowerCase()} tone={trendTone(data.summary.overallTrend)} />
            </div>

            <div className="sd-section">
              <div className="cell-label">Performance trend over time <span>{data.results.length} exams</span></div>
              <TrendChart points={[...data.results]
                .filter((r) => r.exam && r.exam.examDate)
                .sort((a, b) => String(a.exam.examDate).localeCompare(String(b.exam.examDate)))
                .map((r, i) => ({ label: String(i + 1), value: Number(r.percentage) }))} />
            </div>

            <div className="sd-section">
              <div className="cell-label">Subject performance <span>vs section cohort</span></div>
              <table className="tbl">
                <thead><tr><th>Subject</th><th className="r">Exams</th><th className="r">Avg</th><th>Trend</th><th className="r">vs cohort</th></tr></thead>
                <tbody>
                  {(data.summary.subjects || []).map((su) => (
                    <tr key={su.subjectCode}>
                      <td><b>{su.subjectName}</b> <span className="u-mono" style={{ color: "var(--ink-3)", fontSize: 11 }}>{su.subjectCode}</span></td>
                      <td className="r num">{su.examCount}</td>
                      <td className="r num">{num(su.average)}</td>
                      <td><Badge tone={trendTone(su.trend)}>{(su.trend || "—").toLowerCase()}</Badge></td>
                      <td className="r num" style={{ color: Number(su.deltaFromSubjectCohortAvg) < 0 ? "var(--accent)" : "var(--ink)" }}>
                        {su.deltaFromSubjectCohortAvg == null ? "—" : (Number(su.deltaFromSubjectCohortAvg) >= 0 ? "+" : "") + num(su.deltaFromSubjectCohortAvg)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="sd-section">
              <div className="cell-label">Cohort comparison <span>section {data.summary.section || "—"}</span></div>
              <div className="d-stat-row">
                <DetailStat label="Section avg" value={num(data.summary.cohort && data.summary.cohort.sectionAverage)} />
                <DetailStat label="This student vs section"
                  value={(() => { const d = data.summary.cohort && data.summary.cohort.deltaFromSection; return d == null ? "—" : (Number(d) >= 0 ? "+" : "") + num(d); })()}
                  tone={data.summary.cohort && Number(data.summary.cohort.deltaFromSection) < 0 ? "bad" : "ok"} />
              </div>
            </div>

            <div className="sd-cta">
              <button className="btn accent" onClick={goInsight}><span>Generate AI insight</span><span className="arrow">→</span></button>
            </div>
          </React.Fragment>
        )}
      </div>
    );
  }

  /* ============================================================
     AI INSIGHTS (Screen 4) — structured render of /insights
     ============================================================ */
  function profileTone(p) {
    return p === "TOP_PERFORMER" ? "ok" : p === "SOLID" ? "accent"
      : p === "AT_RISK" || p === "STRUGGLING" ? "bad" : "warn";
  }
  function confTone(c) { return c === "HIGH" ? "ok" : c === "LOW" ? "warn" : "accent"; }

  function CacheStrip({ resp }) {
    const at = resp.analyzedAt ? new Date(resp.analyzedAt).toLocaleString() : "—";
    return (
      <div className="cache-strip">
        <Badge tone={resp.fromCache ? "muted" : "accent"}>{resp.fromCache ? "Served from cache" : "Freshly generated"}</Badge>
        <span className="cs-item">model <b>{resp.modelUsed || "—"}</b></span>
        <span className="cs-item">analyzed <b>{at}</b></span>
        {resp.dataHash ? <span className="cs-item">hash <b className="u-mono">{String(resp.dataHash).slice(0, 10)}…</b></span> : null}
      </div>
    );
  }

  function InsightPanel({ resp }) {
    const p = resp.insight || {};
    const strengths = (p.subjectInsights || []).filter((s) => s.relativeToCohort === "ABOVE");
    const improve = (p.subjectInsights || []).filter((s) => s.relativeToCohort === "BELOW");
    return (
      <div className="ai-panel">
        <CacheStrip resp={resp} />

        <div className="ai-badges">
          <Badge tone={p.atRisk ? "bad" : "ok"}>{p.atRisk ? "At risk" : "Not at risk"}</Badge>
          <Badge tone={profileTone(p.performanceProfile)}>{(p.performanceProfile || "—").replace(/_/g, " ").toLowerCase()}</Badge>
          <Badge tone={confTone(p.confidence)}>confidence · {(p.confidence || "—").toLowerCase()}</Badge>
        </div>

        <div className="ai-card">
          <div className="ai-card-label">Overall assessment</div>
          <p className="ai-assessment">{p.overallAssessment}</p>
        </div>

        <div className="grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
          <div className="ai-card">
            <div className="ai-card-label ok">Strengths</div>
            {strengths.length ? strengths.map((s, i) => (
              <div className="ai-item" key={i}><b className="u-mono">{s.subjectCode}</b><span>{s.observation}</span></div>
            )) : <div className="state-block compact">No subjects above cohort average.</div>}
          </div>
          <div className="ai-card">
            <div className="ai-card-label bad">Improvement areas</div>
            {improve.length ? improve.map((s, i) => (
              <div className="ai-item" key={i}><b className="u-mono">{s.subjectCode}</b><span>{s.observation}</span></div>
            )) : <div className="state-block compact">No subjects below cohort average.</div>}
          </div>
        </div>

        {(p.patterns || []).length > 0 && (
          <div className="ai-card">
            <div className="ai-card-label">Detected patterns</div>
            {p.patterns.map((pt, i) => (
              <div className="ai-pattern" key={i}>
                <Badge tone={trendTone(pt.type)}>{(pt.type || "—").toLowerCase()}</Badge>
                <span className="u-mono ai-scope">{(pt.scope || "").replace(/_/g, " ").toLowerCase()}</span>
                <span className="ai-pattern-desc">{pt.description}</span>
              </div>
            ))}
          </div>
        )}

        <div className="ai-card">
          <div className="ai-card-label accent">Recommended interventions</div>
          {(p.interventions || []).length ? (
            <ol className="ai-interventions">
              {p.interventions.map((it, i) => <li key={i}>{it}</li>)}
            </ol>
          ) : <div className="state-block compact">No interventions recommended.</div>}
        </div>
      </div>
    );
  }

  function Insights() {
    const roster = useAsync(() => window.API.listStudents(0, 200), []);
    const preset = window.__insightStudent || null;
    const [sel, setSel] = React.useState(preset ? String(preset.id) : "");
    const [gen, setGen] = React.useState({ loading: false, resp: null, error: null });

    // default selection once the roster arrives
    React.useEffect(() => {
      if (!sel && roster.data && roster.data.content && roster.data.content.length) {
        setSel(String(roster.data.content[0].id));
      }
    }, [roster.data]); // eslint-disable-line

    // auto-run when arriving from the detail drawer
    React.useEffect(() => {
      if (preset && preset.id) { window.__insightStudent = null; run(String(preset.id)); }
    }, []); // eslint-disable-line

    const run = React.useCallback((idArg) => {
      const id = idArg || sel;
      if (!id) return;
      setGen({ loading: true, resp: null, error: null });
      window.API.insights(id)
        .then((resp) => {
          setGen({ loading: false, resp, error: null });
          const name = (roster.data && roster.data.content || []).find((s) => String(s.id) === String(id));
          window.AID_RECENT = window.AID_RECENT || [];
          window.AID_RECENT.unshift({
            studentName: name ? name.name : "Student " + id,
            profile: resp.insight && resp.insight.performanceProfile,
            atRisk: resp.insight && resp.insight.atRisk,
            fromCache: resp.fromCache,
            at: Date.now(),
          });
        })
        .catch((error) => setGen({ loading: false, resp: null, error }));
    }, [sel, roster.data]);

    return (
      <section data-screen-label="AI Insights">
        <Head idx="03 / 03" title="ai insights" sub="Structured narrative from the local LLM over real analytics. Cached per data state — regenerating identical data is served instantly." />

        {roster.loading && <Loading label="Loading students…" />}
        {roster.error && <ErrorState error={roster.error} onRetry={roster.reload} />}
        {roster.data && (
          <div className="ai-controls">
            <label className="ai-pick">
              <span className="fl">student</span>
              <select value={sel} onChange={(e) => setSel(e.target.value)} disabled={gen.loading}>
                {(roster.data.content || []).map((s) => (
                  <option key={s.id} value={s.id}>{s.rollNumber} · {s.name}</option>
                ))}
              </select>
            </label>
            <button className="btn accent" onClick={() => run()} disabled={gen.loading || !sel}>
              <span>{gen.loading ? "Generating…" : "Generate insight"}</span>{!gen.loading && <span className="arrow">→</span>}
            </button>
          </div>
        )}

        {gen.loading && <Loading label="Asking the model — this can take a few seconds on a cache miss…" />}
        {gen.error && (
          <ErrorState
            error={gen.error}
            onRetry={gen.error.kind === "RATE_LIMIT" || gen.error.kind === "AI_BAD_GATEWAY" || gen.error.kind === "AI_UNAVAILABLE" ? () => run() : null}
          />
        )}
        {gen.resp && <InsightPanel resp={gen.resp} />}
      </section>
    );
  }

  Object.assign(window, { Students, StudentDetail, Insights });
})();
