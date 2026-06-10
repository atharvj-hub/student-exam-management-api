/* ============================================================
   ACADEMIC INTELLIGENCE — synthetic dataset
   Deterministic (seeded) so every render is stable.
   100 students · 2 sections · 6 subjects · 18 exams · results
   ============================================================ */
(function (global) {
  // --- seeded PRNG (mulberry32) -------------------------------------------
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  const rng = mulberry32(20260608);
  const rand = (min, max) => min + (max - min) * rng();
  const randInt = (min, max) => Math.floor(rand(min, max + 1));
  const pick = (arr) => arr[Math.floor(rng() * arr.length)];
  // gaussian via Box-Muller (true normal)
  const gauss = (mean, sd) => {
    let u = 0, v = 0;
    while (u === 0) u = rng();
    while (v === 0) v = rng();
    return mean + sd * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
  };
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

  // --- vocabulary ----------------------------------------------------------
  const SUBJECTS = [
    { id: "MAT", name: "Mathematics",     mean: 73, sd: 17 },
    { id: "PHY", name: "Physics",         mean: 61, sd: 19 },
    { id: "CSC", name: "Computer Science",mean: 80, sd: 13 },
    { id: "LIT", name: "Literature",      mean: 75, sd: 13 },
    { id: "ECO", name: "Economics",       mean: 67, sd: 16 },
    { id: "BIO", name: "Biology",         mean: 58, sd: 20 },
  ];
  const SECTIONS = ["A", "B"];
  const FIRST = ["Aria","Noah","Maya","Leo","Iris","Kai","Sofia","Theo","Lena","Omar","Nina","Eli","Tara","Yusuf","Vera","Jonas","Ada","Reza","Mira","Luca","Hana","Ivan","Zara","Felix","Lucia","Arjun","Elsa","Mateo","Nora","Diego","Saara","Hugo","Ravi","Clara","Toma","Anika","Ezra","Maja","Cyrus","Liv","Idris","Petra","Samir","Greta","Nikhil","Asha","Bruno","Yara","Otto","Inez"];
  const LAST  = ["Vance","Okafor","Lindqvist","Haddad","Moreau","Sato","Romano","Bauer","Petrov","Nair","Costa","Larsen","Khan","Bianchi","Novak","Andersen","Reyes","Farahani","Holm","Mensah","Fischer","Dube","Ricci","Berg","Voss","Iyer","Sharma","Lund","Cruz","Marek","Dahl","Eriksen","Boateng","Wagner","Singh","Toma","Kovac","Sasaki","Aziz","Falk"];

  // --- exams: 3 per subject = 18 ------------------------------------------
  const EXAM_TYPES = ["Midterm I", "Midterm II", "Final"];
  const MONTHS = ["Feb", "Apr", "May"];
  const exams = [];
  SUBJECTS.forEach((s) => {
    EXAM_TYPES.forEach((t, i) => {
      exams.push({
        id: `${s.id}-${i + 1}`,
        subjectId: s.id,
        subject: s.name,
        title: t,
        date: `${MONTHS[i]} 2026`,
        order: i,
        weight: i === 2 ? 0.5 : 0.25,
      });
    });
  });

  // --- students ------------------------------------------------------------
  function gradeFor(score) {
    if (score >= 90) return "A";
    if (score >= 80) return "B";
    if (score >= 70) return "C";
    if (score >= 60) return "D";
    if (score >= 50) return "E";
    return "F";
  }
  const PASS_MARK = 50;

  const usedNames = new Set();
  function uniqueName() {
    let n, guard = 0;
    do { n = `${pick(FIRST)} ${pick(LAST)}`; guard++; }
    while (usedNames.has(n) && guard < 50);
    usedNames.add(n);
    return n;
  }

  const students = [];
  for (let i = 0; i < 100; i++) {
    const section = SECTIONS[i % 2]; // 50 / 50
    // each student has an innate ability; section B tuned slightly lower for contrast
    const ability = clamp(gauss(section === "A" ? 3 : -8, 18), -48, 36);
    const results = {};
    let total = 0, count = 0, weightedTotal = 0, weightSum = 0;
    SUBJECTS.forEach((s) => {
      results[s.id] = exams
        .filter((e) => e.subjectId === s.id)
        .map((e) => {
          const noise = gauss(0, 9);
          const score = Math.round(clamp(s.mean + ability * 0.7 + noise, 8, 99));
          total += score; count++;
          weightedTotal += score * e.weight; weightSum += e.weight;
          return { examId: e.id, title: e.title, date: e.date, score, grade: gradeFor(score) };
        });
    });
    const avg = +(total / count).toFixed(1);
    const gpa = +(avg / 20).toFixed(2); // 0–5 scale
    // failing subjects: subject average < pass
    let failing = 0;
    const subjectAverages = {};
    SUBJECTS.forEach((s) => {
      const sAvg = results[s.id].reduce((a, r) => a + r.score, 0) / results[s.id].length;
      subjectAverages[s.id] = +sAvg.toFixed(1);
      if (sAvg < PASS_MARK) failing++;
    });
    // trend: compare final vs midterm I avg
    const finals = SUBJECTS.map((s) => results[s.id][2].score);
    const firsts = SUBJECTS.map((s) => results[s.id][0].score);
    const trend = +((finals.reduce((a, b) => a + b, 0) - firsts.reduce((a, b) => a + b, 0)) / 6).toFixed(1);

    students.push({
      id: `S${String(i + 1).padStart(3, "0")}`,
      name: uniqueName(),
      section, ability,
      results, subjectAverages,
      avg, gpa, grade: gradeFor(avg),
      failing, trend,
      attendance: Math.round(clamp(gauss(92, 7), 60, 100)),
    });
  }

  // --- risk model ----------------------------------------------------------
  // risk score 0–100; higher = more at risk
  students.forEach((st) => {
    let r = 0;
    r += clamp((62 - st.avg) * 1.6, 0, 55);          // low average
    r += st.failing * 12;                             // failed subjects
    r += st.trend < 0 ? clamp(-st.trend * 1.8, 0, 22) : 0; // declining
    r += clamp((90 - st.attendance) * 0.7, 0, 18);    // attendance
    st.risk = Math.round(clamp(r, 0, 100));
    st.riskBand = st.risk >= 60 ? "critical" : st.risk >= 38 ? "elevated" : st.risk >= 20 ? "watch" : "stable";
  });

  // --- aggregates ----------------------------------------------------------
  const all = students;
  const mean = (arr, f) => arr.reduce((a, x) => a + f(x), 0) / arr.length;

  const gradeOrder = ["A", "B", "C", "D", "E", "F"];
  const gradeDistribution = gradeOrder.map((g) => ({
    grade: g,
    count: all.filter((s) => s.grade === g).length,
  }));

  const subjectStats = SUBJECTS.map((s) => {
    const avgs = all.map((st) => st.subjectAverages[s.id]);
    const subjectAvg = +mean(all, (st) => st.subjectAverages[s.id]).toFixed(1);
    const passRate = Math.round(
      (all.filter((st) => st.subjectAverages[s.id] >= PASS_MARK).length / all.length) * 100
    );
    const aAvg = +mean(all.filter((s2) => s2.section === "A"), (st) => st.subjectAverages[s.id]).toFixed(1);
    const bAvg = +mean(all.filter((s2) => s2.section === "B"), (st) => st.subjectAverages[s.id]).toFixed(1);
    const top = [...all].sort((x, y) => y.subjectAverages[s.id] - x.subjectAverages[s.id])[0];
    return {
      id: s.id, name: s.name,
      avg: subjectAvg, passRate, aAvg, bAvg,
      spread: +(Math.max(...avgs) - Math.min(...avgs)).toFixed(0),
      topStudent: top.name, topScore: top.subjectAverages[s.id],
    };
  });

  const sectionStats = SECTIONS.map((sec) => {
    const grp = all.filter((s) => s.section === sec);
    return {
      section: sec,
      students: grp.length,
      avg: +mean(grp, (s) => s.avg).toFixed(1),
      passRate: Math.round((grp.filter((s) => s.avg >= PASS_MARK).length / grp.length) * 100),
      atRisk: grp.filter((s) => s.riskBand === "critical" || s.riskBand === "elevated").length,
      topGpa: +Math.max(...grp.map((s) => s.gpa)).toFixed(2),
    };
  });

  const topPerformers = [...all].sort((a, b) => b.avg - a.avg).slice(0, 8);
  const atRisk = [...all].filter((s) => s.risk >= 38).sort((a, b) => b.risk - a.risk);
  const passRate = Math.round((all.filter((s) => s.avg >= PASS_MARK).length / all.length) * 100);
  const overallAvg = +mean(all, (s) => s.avg).toFixed(1);

  const recentActivity = [...exams]
    .map((e) => {
      const scores = all.map((st) => {
        const r = st.results[e.subjectId].find((x) => x.examId === e.id);
        return r.score;
      });
      return {
        ...e,
        avg: +(scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1),
        passRate: Math.round((scores.filter((x) => x >= PASS_MARK).length / scores.length) * 100),
        high: Math.max(...scores),
      };
    })
    .sort((a, b) => b.order - a.order || a.subject.localeCompare(b.subject))
    .slice(0, 9);

  // --- AI insights (templated from the real aggregates) -------------------
  const weakest = [...subjectStats].sort((a, b) => a.avg - b.avg)[0];
  const strongest = [...subjectStats].sort((a, b) => b.avg - a.avg)[0];
  const widestGap = [...subjectStats].sort((a, b) => Math.abs(b.aAvg - b.bAvg) - Math.abs(a.aAvg - a.bAvg))[0];
  const leadingSection = sectionStats[0].avg >= sectionStats[1].avg ? sectionStats[0] : sectionStats[1];
  const decliners = all.filter((s) => s.trend < -4).length;

  const insights = [
    {
      id: "INS-01", tag: "RISK", weight: "HIGH",
      headline: `${atRisk.length} students cross the intervention threshold`,
      body: `${atRisk.filter(s=>s.riskBand==="critical").length} are critical — driven by sub-50 averages and negative grade trends across Final examinations. Recommend structured tutoring within two weeks.`,
      metric: atRisk.length, metricLabel: "flagged",
    },
    {
      id: "INS-02", tag: "SUBJECT", weight: "HIGH",
      headline: `${weakest.name} is the cohort's weakest subject`,
      body: `Cohort average sits at ${weakest.avg} with a ${weakest.passRate}% pass rate — ${(strongest.avg - weakest.avg).toFixed(0)} points below ${strongest.name}. The Final assessment shows the steepest drop.`,
      metric: weakest.avg, metricLabel: "avg score",
    },
    {
      id: "INS-03", tag: "SECTION", weight: "MEDIUM",
      headline: `Section ${leadingSection.section} leads by ${Math.abs(sectionStats[0].avg - sectionStats[1].avg).toFixed(1)} points`,
      body: `Section ${leadingSection.section} averages ${leadingSection.avg} versus ${(leadingSection.section==="A"?sectionStats[1].avg:sectionStats[0].avg)}. The gap is widest in ${widestGap.name} (${Math.abs(widestGap.aAvg - widestGap.bAvg).toFixed(1)} pts).`,
      metric: `${Math.abs(sectionStats[0].avg - sectionStats[1].avg).toFixed(1)}`, metricLabel: "point gap",
    },
    {
      id: "INS-04", tag: "TREND", weight: "MEDIUM",
      headline: `${decliners} students are trending downward`,
      body: `Final scores fell more than 4 points below their Midterm I baseline for ${decliners} students. Early signal — pair with attendance data before escalation.`,
      metric: decliners, metricLabel: "declining",
    },
    {
      id: "INS-05", tag: "STRENGTH", weight: "LOW",
      headline: `${strongest.name} anchors cohort performance`,
      body: `At ${strongest.avg} average and ${strongest.passRate}% pass rate, ${strongest.name} is the most reliable subject. Top student ${strongest.topStudent} reached ${strongest.topScore}.`,
      metric: strongest.passRate, metricLabel: "% pass",
    },
  ];

  // --- export --------------------------------------------------------------
  global.AID = {
    SUBJECTS, SECTIONS, exams, students,
    gradeOrder, gradeDistribution, subjectStats, sectionStats,
    topPerformers, atRisk, recentActivity, insights,
    summary: {
      students: all.length,
      sections: SECTIONS.length,
      subjects: SUBJECTS.length,
      exams: exams.length,
      results: all.length * exams.length,
      passRate, overallAvg,
      atRisk: atRisk.length,
      critical: atRisk.filter((s) => s.riskBand === "critical").length,
      weakest, strongest,
    },
    PASS_MARK, gradeFor,
  };
})(window);
