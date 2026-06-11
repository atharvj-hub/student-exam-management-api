/* ============================================================
   HELPERS — React context + shared UI primitives.
   Wrapped in an IIFE; exports land on window so every Babel
   script can use them without top-level name collisions.
   ============================================================ */
(function () {
  /* App-wide actions, provided by <App> (navigation + open student) */
  const AppCtx = React.createContext({ nav: () => {}, openStudent: () => {} });

  /* ---- useAsync : run an async fn, expose {loading,data,error,reload} -- */
  function useAsync(fn, deps) {
    const [state, setState] = React.useState({ loading: true, data: null, error: null });
    const alive = React.useRef(true);
    const tick = React.useState(0);
    const reload = React.useCallback(() => tick[1]((n) => n + 1), []);
    React.useEffect(() => {
      alive.current = true;
      setState({ loading: true, data: null, error: null });
      Promise.resolve()
        .then(fn)
        .then((data) => { if (alive.current) setState({ loading: false, data, error: null }); })
        .catch((error) => { if (alive.current) setState({ loading: false, data: null, error }); });
      return () => { alive.current = false; };
      // eslint-disable-next-line
    }, [...(deps || []), tick[0]]);
    return { ...state, reload };
  }

  /* ---- Loading : centred status line ---------------------------------- */
  function Loading({ label }) {
    return (
      <div className="state-block" role="status">
        <span className="state-spin" aria-hidden="true"></span>
        <span>{label || "Loading…"}</span>
      </div>
    );
  }

  /* ---- ErrorState : friendly, typed, retryable ------------------------ */
  function ErrorState({ error, onRetry, compact }) {
    const msg = window.API ? window.API.messageFor(error) : (error && error.message) || "Something went wrong.";
    const code = error && error.status ? error.status : null;
    return (
      <div className={"state-block error" + (compact ? " compact" : "")} role="alert">
        <div className="state-msg">{msg}</div>
        {code ? <div className="state-code num">HTTP {code}</div> : null}
        {onRetry ? <button className="btn small" onClick={onRetry}><span>Retry</span></button> : null}
      </div>
    );
  }

  /* ---- Badge : labelled status pill ----------------------------------- */
  function Badge({ tone, children }) {
    return <span className={"ui-badge" + (tone ? " " + tone : "")}>{children}</span>;
  }

  /* ---- StatCell : big metric block ------------------------------------ */
  function StatCell({ k, value, unit, size = "clamp(32px,3.6vw,54px)", flag = false }) {
    return (
      <div className={"metric" + (flag ? " flag" : "")} data-magnetic="0.18">
        <div className="k">{k}</div>
        <div className="v num" style={{ fontSize: size }}>{value}</div>
        <div className="u">{unit}</div>
      </div>
    );
  }

  /* ---- Block : titled chart cell -------------------------------------- */
  function Block({ title, meta, style, children }) {
    return (
      <div className="chart-block" style={style}>
        <div className="chart-title"><h3>{title}</h3>{meta ? <span className="meta">{meta}</span> : null}</div>
        {children}
      </div>
    );
  }

  /* ---- Head : section header ------------------------------------------ */
  function Head({ idx, title, sub }) {
    return (
      <div className="section-head">
        <span className="idx">{idx}</span>
        <div><h2 data-reveal="chars" data-reveal-stagger="0.02">{title}</h2></div>
        <p className="sub">{sub}</p>
      </div>
    );
  }

  /* ---- Seg : segmented filter control --------------------------------- */
  function Seg({ value, setValue, options }) {
    return (
      <div className="seg">
        {options.map(([val, label]) => (
          <button key={val} aria-pressed={value === val} onClick={() => setValue(val)}>{label}</button>
        ))}
      </div>
    );
  }

  /* ---- TrendChart : single inline SVG line chart (real results) -------
     points: [{ label, value }] in chronological order. No external libs. */
  function TrendChart({ points, height = 200 }) {
    if (!points || points.length === 0) return <div className="state-block compact">No exam history to plot.</div>;
    const W = 680, H = height, padL = 34, padR = 14, padT = 16, padB = 30;
    const xs = points.length > 1 ? points.length - 1 : 1;
    const innerW = W - padL - padR, innerH = H - padT - padB;
    const x = (i) => padL + (i / xs) * innerW;
    const y = (v) => padT + (1 - Math.max(0, Math.min(100, v)) / 100) * innerH;
    const line = points.map((p, i) => (i === 0 ? "M" : "L") + x(i).toFixed(1) + " " + y(p.value).toFixed(1)).join(" ");
    const area = line + " L" + x(points.length - 1).toFixed(1) + " " + (padT + innerH) + " L" + padL + " " + (padT + innerH) + " Z";
    const gridVals = [0, 25, 50, 75, 100];
    return (
      <svg className="trend-chart" viewBox={"0 0 " + W + " " + H} width="100%" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Performance trend over time">
        {gridVals.map((g) => (
          <g key={g}>
            <line x1={padL} x2={W - padR} y1={y(g)} y2={y(g)} className="tc-grid" />
            <text x={6} y={y(g) + 3} className="tc-axis num">{g}</text>
          </g>
        ))}
        <path d={area} className="tc-area" />
        <path d={line} className="tc-line" />
        {points.map((p, i) => (
          <g key={i}>
            <circle cx={x(i)} cy={y(p.value)} r="3.4" className="tc-dot" />
            <text x={x(i)} y={H - 10} className="tc-xaxis" textAnchor="middle">{p.label}</text>
          </g>
        ))}
      </svg>
    );
  }

  Object.assign(window, { AppCtx, useAsync, Loading, ErrorState, Badge, StatCell, Block, Head, Seg, TrendChart });
})();
