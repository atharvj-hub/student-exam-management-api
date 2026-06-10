/* ============================================================
   MOTION SYSTEM — magnetic-tween vocabulary only.
   Wire effects by adding a data attribute. Nothing bouncy,
   nothing spinning, nothing floating. Just precise pulls.

     data-magnetic="0.4"   cursor pull on the element (power2.out),
                           elastic release on leave. Optional child
                           [data-magnetic-label] gets a lighter pull.
     data-parallax="0.18"  pointer-depth offset, mapRange-driven,
                           power3.out follow. negative = counter-move.
     data-reveal           Jam-style mask + slide-in on first view.
                           data-reveal="chars" splits into glyphs.
                           data-reveal-delay / data-reveal-stagger.
     data-trail            (on a container, usually <body>) spawns a
                           lagging trail of marks that chase the
                           cursor with staggered power2.out follows.
   ============================================================ */
(function () {
  if (!window.gsap) return;
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const coarse = window.matchMedia("(pointer: coarse), (hover: none)").matches;
  const { mapRange, clamp } = gsap.utils;

  /* ---------------------------------------------------------------
     1 · MAGNETIC  — the core vocabulary (from overwrite/dynamic tweens)
     map cursor position inside the element to a translation,
     overwrite:"auto" so concurrent tweens layer cleanly,
     elastic.out release.
  --------------------------------------------------------------- */
  function bindMagnetic(zone) {
    if (coarse) return;
    const strength = parseFloat(zone.dataset.magnetic) || 0.4;
    const label = zone.querySelector("[data-magnetic-label]");
    const labelStrength = strength * 0.55;
    const xTo = gsap.quickTo(zone, "x", { duration: 0.5, ease: "power3.out" });
    const yTo = gsap.quickTo(zone, "y", { duration: 0.5, ease: "power3.out" });

    zone.addEventListener("mousemove", (e) => {
      const r = zone.getBoundingClientRect();
      const mx = mapRange(r.left, r.right, -r.width / 2, r.width / 2, e.clientX);
      const my = mapRange(r.top, r.bottom, -r.height / 2, r.height / 2, e.clientY);
      xTo(mx * strength);
      yTo(my * strength);
      if (label) {
        gsap.to(label, { x: mx * labelStrength, y: my * labelStrength, duration: 0.5, ease: "power3.out", overwrite: true });
      }
    });
    zone.addEventListener("mouseleave", () => {
      gsap.to(zone, { x: 0, y: 0, duration: 0.8, ease: "elastic.out(1,0.4)", overwrite: "auto" });
      if (label) gsap.to(label, { x: 0, y: 0, duration: 0.8, ease: "elastic.out(1,0.4)", overwrite: true });
    });
  }

  /* ---------------------------------------------------------------
     2 · PARALLAX — pointer depth, same mapRange→translate vocabulary.
     Global pointer drives every [data-parallax] via a quickTo so it
     reads as magnetic follow, not a jump.
  --------------------------------------------------------------- */
  const parallaxItems = [];
  function bindParallax(el) {
    if (coarse) return;
    const depth = parseFloat(el.dataset.parallax) || 0.15;
    parallaxItems.push({
      el, depth,
      xTo: gsap.quickTo(el, "x", { duration: 1.1, ease: "power3.out" }),
      yTo: gsap.quickTo(el, "y", { duration: 1.1, ease: "power3.out" }),
    });
  }
  function driveParallax(e) {
    const cx = window.innerWidth / 2, cy = window.innerHeight / 2;
    const dx = (e.clientX - cx) / cx; // -1..1
    const dy = (e.clientY - cy) / cy;
    for (const it of parallaxItems) {
      it.xTo(dx * it.depth * -42);
      it.yTo(dy * it.depth * -42);
    }
  }

  /* ---------------------------------------------------------------
     3 · REVEAL — Jam-style: mask wipe + slide, power4, staggered.
  --------------------------------------------------------------- */
  function splitChars(el) {
    const text = el.textContent;
    el.textContent = "";
    el.setAttribute("aria-label", text);
    const frag = document.createDocumentFragment();
    [...text].forEach((ch) => {
      const s = document.createElement("span");
      s.textContent = ch === " " ? "\u00A0" : ch;
      s.style.display = "inline-block";
      s.setAttribute("aria-hidden", "true");
      frag.appendChild(s);
    });
    el.appendChild(frag);
    return el.querySelectorAll("span");
  }

  function playReveal(el) {
    if (el.__revealed) return;
    el.__revealed = true;
    const delay = parseFloat(el.dataset.revealDelay) || 0;
    if (reduce) { el.style.clipPath = "none"; return; }

    const mode = el.dataset.reveal;
    if (mode === "chars") {
      const spans = splitChars(el);
      gsap.set(el, { clipPath: "inset(-10% -10% -10% -10%)" });
      gsap.from(spans, {
        yPercent: 115, opacity: 0,
        duration: 1.0, ease: "power4.out",
        stagger: parseFloat(el.dataset.revealStagger) || 0.03,
        delay,
      });
      safety(el, [el, ...spans], delay + 1.3);
    } else if (mode === "wipe") {
      gsap.fromTo(el, { clipPath: "inset(0 100% 0 0)" }, { clipPath: "inset(0 0% 0 0)", duration: 1.1, ease: "power4.inOut", delay });
      safety(el, [el], delay + 1.4);
    } else if (mode === "rise") {
      gsap.from(el, { yPercent: 18, opacity: 0, duration: 0.9, ease: "power4.out", delay });
      safety(el, [el], delay + 1.2);
    } else {
      // default — slide from left under a mask, The Jam style
      gsap.set(el, { clipPath: "inset(0 0 0 0)", overflow: "hidden" });
      gsap.from(el, { xPercent: -8, opacity: 0, duration: 1.0, ease: "power4.out", delay });
      safety(el, [el], delay + 1.3);
    }
  }
  // guarantee visible end-state even if rAF is throttled
  function safety(host, nodes, secs) {
    setTimeout(() => {
      if (gsap.isTweening(nodes[nodes.length - 1])) return;
      nodes.forEach((n) => gsap.set(n, { clearProps: "transform,opacity,clipPath" }));
      host.style.clipPath = "none";
    }, secs * 1000);
  }

  function bindReveals() {
    const els = document.querySelectorAll("[data-reveal]");
    if (!("IntersectionObserver" in window)) { els.forEach(playReveal); return; }
    const io = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (en.isIntersecting) { playReveal(en.target); io.unobserve(en.target); }
      });
    }, { threshold: 0.18 });
    els.forEach((el) => io.observe(el));
  }

  /* ---------------------------------------------------------------
     4 · TRAIL — lagging marks chasing the cursor with staggered
     power2.out follows (magnetic vocabulary, applied N times).
  --------------------------------------------------------------- */
  function bindTrail() {
    if (coarse || reduce) return;
    const layer = document.createElement("div");
    layer.className = "trail-layer";
    document.body.appendChild(layer);
    const COUNT = 6;
    const marks = [];
    for (let i = 0; i < COUNT; i++) {
      const m = document.createElement("div");
      m.className = "trail-mark";
      // shrink + fade down the tail
      const s = 1 - i / (COUNT + 1);
      gsap.set(m, { scale: s, opacity: 0 });
      layer.appendChild(m);
      marks.push({
        el: m,
        xTo: gsap.quickTo(m, "x", { duration: 0.45 + i * 0.12, ease: "power2.out" }),
        yTo: gsap.quickTo(m, "y", { duration: 0.45 + i * 0.12, ease: "power2.out" }),
        base: s,
      });
    }
    let moving;
    window.addEventListener("mousemove", (e) => {
      marks.forEach((mk) => { mk.xTo(e.clientX); mk.yTo(e.clientY); });
      gsap.to(marks.map((m) => m.el), { opacity: (i, t) => marks[i].base * 0.9, duration: 0.2, overwrite: true });
      clearTimeout(moving);
      moving = setTimeout(() => {
        gsap.to(marks.map((m) => m.el), { opacity: 0, duration: 0.5, overwrite: true });
      }, 120);
    });
  }

  /* ---------------------------------------------------------------
     5 · CUSTOM CURSOR — dot + ring, ring magnetically lags (power3)
  --------------------------------------------------------------- */
  function bindCursor() {
    if (coarse) return;
    const dot = document.createElement("div"); dot.className = "cursor-dot";
    const ring = document.createElement("div"); ring.className = "cursor-ring";
    document.body.append(dot, ring);
    // start centred so they're visible before the first move
    gsap.set([dot, ring], { x: window.innerWidth / 2, y: window.innerHeight / 2 });
    const dx = gsap.quickTo(dot, "x", { duration: 0.06, ease: "power3.out" });
    const dy = gsap.quickTo(dot, "y", { duration: 0.06, ease: "power3.out" });
    const rx = gsap.quickTo(ring, "x", { duration: 0.5, ease: "power3.out" });
    const ry = gsap.quickTo(ring, "y", { duration: 0.5, ease: "power3.out" });
    window.addEventListener("mousemove", (e) => {
      dx(e.clientX); dy(e.clientY); rx(e.clientX); ry(e.clientY);
    });
    // ring expands over interactive things — "auto" so it ONLY touches scale,
    // never the x/y follow tweens (overwrite:true would freeze the ring).
    const grow = () => gsap.to(ring, { scale: 1.9, duration: 0.4, ease: "power3.out", overwrite: "auto" });
    const shrink = () => gsap.to(ring, { scale: 1, duration: 0.5, ease: "power3.out", overwrite: "auto" });
    document.addEventListener("mouseover", (e) => {
      if (e.target.closest("a,button,[data-magnetic],.nav-item,.tbl tbody tr,.insight")) grow();
    });
    document.addEventListener("mouseout", (e) => {
      if (e.target.closest("a,button,[data-magnetic],.nav-item,.tbl tbody tr,.insight")) shrink();
    });
  }

  /* ---------------------------------------------------------------
     Counters — number reveals (precise, not playful)
  --------------------------------------------------------------- */
  function countUp(el) {
    if (el.__counted) return; el.__counted = true;
    const target = parseFloat(el.dataset.count);
    const decimals = (el.dataset.count.split(".")[1] || "").length;
    const suffix = el.dataset.suffix || "";
    const delay = parseFloat(el.dataset.countDelay) || 0;
    const final = target.toFixed(decimals) + suffix;
    if (reduce) { el.textContent = final; return; }
    const obj = { v: 0 };
    const tw = gsap.to(obj, {
      v: target, duration: 1.4, ease: "power3.out", delay,
      onUpdate: () => { el.textContent = obj.v.toFixed(decimals) + suffix; },
      onComplete: () => { el.textContent = final; },
    });
    // guarantee final value even if rAF is throttled (capture/inactive tab)
    setTimeout(() => { if (el.textContent === "0" || tw.progress() < 1) el.textContent = final; }, (delay + 1.6) * 1000);
  }
  function inView(el) {
    const r = el.getBoundingClientRect();
    return r.top < innerHeight && r.bottom > 0;
  }
  function bindCounters() {
    const els = document.querySelectorAll("[data-count]:not([data-count-bound])");
    els.forEach((el) => el.setAttribute("data-count-bound", "1"));
    if (!("IntersectionObserver" in window)) { els.forEach(countUp); return; }
    const io = new IntersectionObserver((entries) => {
      entries.forEach((en) => { if (en.isIntersecting) { countUp(en.target); io.unobserve(en.target); } });
    }, { threshold: 0.3 });
    els.forEach((el) => { if (inView(el)) countUp(el); else io.observe(el); });
  }

  /* ---------------------------------------------------------------
     Bar grow — scaleX/scaleY from 0 on view
  --------------------------------------------------------------- */
  function bindBars() {
    const els = document.querySelectorAll("[data-grow]");
    const io = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (!en.isIntersecting || en.target.__grew) return;
        en.target.__grew = true;
        const axis = en.target.dataset.grow === "y" ? "scaleY" : "scaleX";
        if (reduce) return;
        gsap.from(en.target, { [axis]: 0, duration: 1.0, ease: "power4.out", delay: parseFloat(en.target.dataset.growDelay) || 0 });
        io.unobserve(en.target);
      });
    }, { threshold: 0.3 });
    els.forEach((el) => io.observe(el));
  }

  /* ===============================================================
     EXTENDED VOCABULARY (Phases 3–7) — engineered, restrained.
     All gated on reduce / coarse, all opt-in via data-attributes,
     all re-scannable. Nothing here replaces the core system.
  =============================================================== */

  /* 5 · ELASTIC HEADLINE — organic stretch / compress on hover.
     Cursor x biases the stretch so it feels like the type leans
     toward the pointer. Editorial, not playful. */
  function bindElastic(el) {
    if (coarse) return;
    const amp = parseFloat(el.dataset.elastic) || 0.05;
    el.style.display = el.style.display || (getComputedStyle(el).display.startsWith("inline") ? "inline-block" : "");
    el.style.transformOrigin = "50% 60%";
    el.addEventListener("mouseenter", () => {
      gsap.to(el, { scaleX: 1 + amp, scaleY: 1 - amp * 0.7, duration: 0.45, ease: "power3.out", overwrite: "auto" });
    });
    el.addEventListener("mousemove", (e) => {
      const r = el.getBoundingClientRect();
      const bias = clamp(mapRange(r.left, r.right, -1, 1, e.clientX), -1, 1);
      gsap.to(el, { skewX: bias * amp * -28, x: bias * amp * 14, duration: 0.5, ease: "power3.out", overwrite: "auto" });
    });
    el.addEventListener("mouseleave", () => {
      gsap.to(el, { scaleX: 1, scaleY: 1, skewX: 0, x: 0, duration: 1.1, ease: "elastic.out(1,0.4)", overwrite: "auto" });
    });
  }

  /* 6 · DIRECTION-AWARE HOVER — the existing recessed-panel hover,
     but the fill enters from the cursor's edge and leaves toward
     the exit edge. Injects one overlay; content is raised above it
     via CSS (.dir-aware > * { z-index:1 }). Under 300ms. */
  function bindDirAware(el) {
    if (coarse) return;
    const cs = getComputedStyle(el);
    if (cs.position === "static") el.style.position = "relative";
    el.style.overflow = "hidden";
    el.classList.add("dir-aware");
    const fill = document.createElement("span");
    fill.className = "dir-fill";
    el.insertBefore(fill, el.firstChild);
    const OFF = { left: [-101, 0], right: [101, 0], top: [0, -101], bottom: [0, 101] };
    const edge = (e) => {
      const r = el.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5;
      const y = (e.clientY - r.top) / r.height - 0.5;
      return Math.abs(x) > Math.abs(y) ? (x < 0 ? "left" : "right") : (y < 0 ? "top" : "bottom");
    };
    el.addEventListener("mouseenter", (e) => {
      const [x, y] = OFF[edge(e)];
      gsap.set(fill, { xPercent: x, yPercent: y });
      gsap.to(fill, { xPercent: 0, yPercent: 0, duration: 0.28, ease: "power3.out", overwrite: true });
    });
    el.addEventListener("mouseleave", (e) => {
      const [x, y] = OFF[edge(e)];
      gsap.to(fill, { xPercent: x, yPercent: y, duration: 0.28, ease: "power3.in", overwrite: true });
    });
  }

  /* 7 · GRID DISTORTION — Swiss poster grid that leans toward the
     cursor. Lines translate a few px max; the grid never breaks,
     never warps. Generated once into [data-grid-distort]. */
  function bindGridDistort(host) {
    if (coarse) return;
    const cols = parseInt(host.dataset.gridCols) || 12;
    const rows = parseInt(host.dataset.gridRows) || 7;
    const layer = document.createElement("div");
    layer.className = "grid-distort";
    const lines = [];
    for (let i = 1; i < cols; i++) {
      const v = document.createElement("i"); v.className = "gl v"; v.style.left = (i / cols) * 100 + "%";
      layer.appendChild(v); lines.push({ el: v, ax: "x", base: i / cols });
    }
    for (let j = 1; j < rows; j++) {
      const h = document.createElement("i"); h.className = "gl h"; h.style.top = (j / rows) * 100 + "%";
      layer.appendChild(h); lines.push({ el: h, ax: "y", base: j / rows });
    }
    host.insertBefore(layer, host.firstChild);
    lines.forEach((l) => { l.to = gsap.quickTo(l.el, l.ax, { duration: 0.9, ease: "power3.out" }); });
    host.addEventListener("mousemove", (e) => {
      const r = host.getBoundingClientRect();
      const px = (e.clientX - r.left) / r.width;
      const py = (e.clientY - r.top) / r.height;
      lines.forEach((l) => {
        const d = l.ax === "x" ? l.base - px : l.base - py;
        l.to(clamp(-d * 26, -7, 7));
      });
    });
    host.addEventListener("mouseleave", () => lines.forEach((l) => l.to(0)));
  }

  /* 3/4 · SCROLL-LINKED TYPOGRAPHY — ScrollTrigger driven.
     [data-scroll-type]  mask-wipe reveal, scrubbed to scroll.
     [data-scroll-par]   slow background-type parallax on scroll. */
  function bindScroll(root) {
    if (reduce || !window.ScrollTrigger) return;
    root.querySelectorAll("[data-scroll-type]:not([data-stt-bound])").forEach((el) => {
      el.setAttribute("data-stt-bound", "1");
      gsap.fromTo(el, { clipPath: "inset(0 100% 0 0)" },
        { clipPath: "inset(0 0% 0 0)", ease: "none",
          scrollTrigger: { trigger: el, start: "top 88%", end: "top 48%", scrub: true } });
    });
    root.querySelectorAll("[data-scroll-par]:not([data-stp-bound])").forEach((el) => {
      el.setAttribute("data-stp-bound", "1");
      const depth = parseFloat(el.dataset.scrollPar) || 0.2;
      gsap.to(el, { yPercent: depth * -38, ease: "none",
        scrollTrigger: { trigger: el.parentElement || el, start: "top bottom", end: "bottom top", scrub: true } });
    });
  }

  /* CONTINUOUS FLOAT — perpetual drift so the scene is never static.
     Deliberately animates yPercent / xPercent / rotation ONLY, leaving
     x / y (px) free for the parallax system — the two compose into one
     matrix instead of overwriting each other. Each element gets its own
     looping (yoyo, repeat:-1) GSAP timeline at its own speed + phase. */
  function bindFloat(el) {
    if (reduce) return;
    const speed = parseFloat(el.dataset.float) || 1;
    const ax = el.dataset.floatX != null ? parseFloat(el.dataset.floatX) : 4;
    const ay = el.dataset.floatY != null ? parseFloat(el.dataset.floatY) : 8;
    const ar = el.dataset.floatRot != null ? parseFloat(el.dataset.floatRot) : 0;
    const base = 7 / speed;
    gsap.to(el, {
      yPercent: ay, xPercent: ax, rotation: ar,
      duration: base * (0.82 + Math.random() * 0.5),
      ease: "sine.inOut", yoyo: true, repeat: -1,
      delay: -Math.random() * base,
    });
  }

  /* ---------------------------------------------------------------
     Public: (re)scan the DOM. Call after rendering a view.
  --------------------------------------------------------------- */
  const MOTION = {
    scan(root = document) {
      root.querySelectorAll("[data-magnetic]:not([data-magnetic-bound])").forEach((el) => {
        el.setAttribute("data-magnetic-bound", "1"); bindMagnetic(el);
      });
      root.querySelectorAll("[data-parallax]:not([data-parallax-bound])").forEach((el) => {
        el.setAttribute("data-parallax-bound", "1"); bindParallax(el);
      });
      root.querySelectorAll("[data-elastic]:not([data-elastic-bound])").forEach((el) => {
        el.setAttribute("data-elastic-bound", "1"); bindElastic(el);
      });
      root.querySelectorAll("[data-dir-aware]:not([data-dir-bound])").forEach((el) => {
        el.setAttribute("data-dir-bound", "1"); bindDirAware(el);
      });
      root.querySelectorAll("[data-grid-distort]:not([data-grid-bound])").forEach((el) => {
        el.setAttribute("data-grid-bound", "1"); bindGridDistort(el);
      });
      root.querySelectorAll("[data-float]:not([data-float-bound])").forEach((el) => {
        el.setAttribute("data-float-bound", "1"); bindFloat(el);
      });
      bindReveals();
      bindCounters();
      bindBars();
      // drop ScrollTriggers whose element has been unmounted (view changes)
      if (window.ScrollTrigger) {
        window.ScrollTrigger.getAll().forEach((t) => {
          if (t.trigger && !document.body.contains(t.trigger)) t.kill();
        });
      }
      bindScroll(root);
      if (window.ScrollTrigger) window.ScrollTrigger.refresh();
    },
    reveal: playReveal,
  };

  function boot() {
    if (window.ScrollTrigger) gsap.registerPlugin(window.ScrollTrigger);
    if (!coarse) window.addEventListener("mousemove", driveParallax, { passive: true });
    bindCursor();
    if (document.body.hasAttribute("data-trail")) bindTrail();
    MOTION.scan(document);
  }

  window.MOTION = MOTION;
  if (document.readyState !== "loading") boot();
  else document.addEventListener("DOMContentLoaded", boot);
})();
