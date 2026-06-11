/* ============================================================
   VIEWS · A — Dashboard (Screen 1)
   Real backend totals, cohort pass rate, recent exam activity,
   and recent AI analyses generated this session.
   ============================================================ */
(function () {
  const { StatCell, Block, Head, Loading, ErrorState, Badge, AppCtx, useAsync } = window;

  /* Session-scoped log of AI analyses the operator has generated.
     Populated by the Insights view; read here. Truthful — only real
     responses appear, nothing fabricated. */
  window.AID_RECENT = window.AID_RECENT || [];

  function loadDashboard() {
    return Promise.all([
      window.API.listStudents(0, 1),
      window.API.listExams(),
      window.API.listResults(0, 1000),
    ]).then(([students, exams, results]) => {
      const rows = (results && results.content) || [];
      const passed = rows.filter((r) => r.status === "PASS").length;
      const passRate = rows.length ? Math.round((passed / rows.length) * 1000) / 10 : 0;
      const recent = [...rows]
        .sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")) || (b.id - a.id))
        .slice(0, 8);
      return {
        totalStudents: students.totalElements,
        totalExams: Array.isArray(exams) ? exams.length : 0,
        totalResults: results.totalElements,
        passRate,
        recent,
      };
    });
  }

  function RecentActivity({ rows }) {
    if (!rows.length) return <div className="state-block compact">No results recorded yet.</div>;
    return (
      <div className="activity">
        {rows.map((r) => (
          <div className="act-row" key={r.id}>
            <span><b>{r.student ? r.student.name : "—"}</b></span>
            <span className="as">{r.exam ? r.exam.examName : "—"}</span>
            <span className="as">score</span>
            <span className="av num">{r.percentage != null ? Number(r.percentage).toFixed(0) : "—"}</span>
            <span className="num" style={{ color: r.status === "PASS" ? "var(--ink)" : "var(--accent)", fontSize: 12, fontWeight: 700 }}>{r.status}</span>
          </div>
        ))}
      </div>
    );
  }

  function RecentAnalyses() {
    const list = (window.AID_RECENT || []).slice(0, 5);
    if (!list.length) {
      return <div className="state-block compact">No AI analyses yet — open <b>AI Insights</b> and generate one. It will appear here.</div>;
    }
    return (
      <div className="activity">
        {list.map((a, i) => (
          <div className="act-row" key={i}>
            <span><b>{a.studentName}</b></span>
            <span className="as">{a.profile ? a.profile.replace(/_/g, " ").toLowerCase() : "—"}</span>
            <span>{a.atRisk ? <Badge tone="bad">at risk</Badge> : <Badge tone="ok">stable</Badge>}</span>
            <span>{a.fromCache ? <Badge tone="muted">cached</Badge> : <Badge tone="accent">fresh</Badge>}</span>
          </div>
        ))}
      </div>
    );
  }

  function Dashboard() {
    const { loading, data, error, reload } = useAsync(loadDashboard, []);

    return (
      <section className="dash" data-screen-label="Dashboard">
        <Head idx="01 / 03" title="dashboard" sub="Whole-cohort totals, straight from the backend — students, exams, results and live pass rate." />
        {loading && <Loading label="Loading cohort totals…" />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && (
          <React.Fragment>
            <div className="dash-stats">
              <StatCell k="Students" value={data.totalStudents} unit="enrolled" />
              <StatCell k="Exams" value={data.totalExams} unit="scheduled" />
              <StatCell k="Results" value={data.totalResults} unit="recorded" />
              <StatCell k="Pass rate" value={data.passRate + "%"} unit={"of " + data.totalResults + " results"} />
            </div>
            <div className="grid" style={{ gridTemplateColumns: "1.3fr 1fr" }}>
              <Block title="Recent exam activity" meta="latest results"><RecentActivity rows={data.recent} /></Block>
              <Block title="Recent AI analyses" meta="this session"><RecentAnalyses /></Block>
            </div>
          </React.Fragment>
        )}
      </section>
    );
  }

  window.Dashboard = Dashboard;
})();
