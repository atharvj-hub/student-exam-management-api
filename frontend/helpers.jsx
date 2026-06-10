/* ============================================================
   HELPERS — React context + shared small components
   Wrapped in an IIFE; exports land on window so every Babel
   script can use them without top-level name collisions.
   ============================================================ */
(function () {
  /* App-wide actions, provided by <App> (navigation + open student) */
  const AppCtx = React.createContext({ nav: () => {}, openStudent: () => {} });

  /* ---- StatCell : big metric block (optionally count-up animated) ------ */
  function StatCell({ k, value, unit, size = "clamp(38px,4.4vw,66px)", flag = false, raw = null, suffix = "", delay = 0 }) {
    const hasCount = raw != null;
    const countProps = hasCount
      ? { "data-count": raw, "data-count-delay": delay, ...(suffix ? { "data-suffix": suffix } : {}) }
      : {};
    return (
      <div className={"metric" + (flag ? " flag" : "")} data-magnetic="0.18">
        <div className="k">{k}</div>
        <div className="v num" style={{ fontSize: size }} {...countProps}>{hasCount ? "0" : value}</div>
        <div className="u">{unit}</div>
      </div>
    );
  }

  /* ---- Block : titled chart cell -------------------------------------- */
  function Block({ title, meta, style, children }) {
    return (
      <div className="chart-block" style={style}>
        <div className="chart-title"><h3>{title}</h3><span className="meta">{meta}</span></div>
        {children}
      </div>
    );
  }

  /* ---- Head : section header (reveals the lowercase title) ------------- */
  function Head({ idx, title, sub }) {
    return (
      <div className="section-head">
        <span className="idx">{idx} / 07</span>
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

  Object.assign(window, { AppCtx, StatCell, Block, Head, Seg });
})();
