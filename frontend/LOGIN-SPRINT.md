# Login-Completion Sprint — Rube Goldberg Interaction Merge
**Academic Intelligence · June 2026** · Scope: login interaction layer only. JWT auth, role handling, routes, dashboard, analytics, and the global motion architecture untouched.

---

## What was merged

The interaction architecture of the uploaded **Rube Goldberg HTML form** (CodePen, Ksenia Kondrashova) is now the login interaction layer on **both** login pages, redrawn into the Swiss editorial vocabulary (ink line-work, monospace labels, hairline borders):

| Reference choreography | Login mapping (student / admin) |
|---|---|
| Name field valid → gear train spins, sprayer paints the submit label | Roll number / Operator ID valid |
| Email valid → spiral unwinds, weight drops, hammer strikes, car rolls, hand grabs the pull line | Passcode / Passkey valid |
| Checkbox → pull system rotates the submit button into the form (only once the hand has closed) | Declaration row: "I confirm this is my own record" / "Authorized operator · audited" |
| Submit → contraption disassembles (random stagger fade) | Plays on **successful** JWT login; failures get a mechanical jolt + inline error instead |

Auth pipeline unchanged: validate → `AUTH.login` (mock JWT, role, user stored) → role-scoped redirect. The machine is choreography only; credentials are still verified by the auth service, machine state can never grant access.

---

## Step-5 audit — bugs found & root causes

### In the previous login implementation
| # | Bug | Root cause | Fix |
|---|---|---|---|
| 1 | Success redirect fired even after leaving the login page | `setTimeout(() => onEnter(...))` not unmount-guarded (state was guarded, the navigation callback wasn't) | `alive` ref checked inside the timeout |
| 2 | Magnetic submit could freeze mid-offset when disabled | `disabled → pointer-events:none` suppresses `mouseleave`, so the magnetic reset tween never fired | Machine submit has a single transform owner (the pull system); magnetic removed from it |
| 3 | Layout jump when error/notice lines appeared (machine would visibly shift) | messages flowed above the form with no reserved space | `.rg-msgs` reserved min-height slot |

### In the reference implementation (fixed during the merge)
| # | Bug | Root cause | Fix |
|---|---|---|---|
| 4 | Checkbox-before-password ordering left the submit button stranded vertical forever | pulling timeline only rebuilt on checkbox change — never when the grabbing hand closed | unlock timeline watches `handClosed` and rebuilds the pull |
| 5 | Live pulling timelines stacked on the same targets on every checkbox toggle | old timeline never killed | killed before rebuild |
| 6 | Shrink-only responsive scale (growing the window never restored size) | `scaleToFit` only applied inside an `if (smaller)` branch | scale always computed, fitted to the machine's asymmetric extents (~170 svg-px above / ~420 below the form) so the car track never clips |
| 7 | Document-global singleton: two login pages (or a React remount) would cross-wire selectors and leak timelines + listeners | all `document.querySelector`, no teardown | engine is a per-root factory (`createLoginMachine(stageEl)`) with full `destroy()` (kills timelines, clears injected SVG and inline transforms) |

### z-index / opacity / hidden-component findings
| # | Issue | Resolution |
|---|---|---|
| 8 | Machine line-work was **invisible on dark grounds** (admin mission-control; dark app theme persisting onto entry pages) — ink strokes & fills are hardcoded in the drawing | `filter: invert(1)` on the SVG for `.auth.admin` and `html[data-theme="dark"]`; spray-paint text colour parameterised (`inkRGB`) per page |
| 9 | Spray-painted submit label could still be faint at the moment of login | `celebrate()` snaps label fully opaque before the disassembly |
| 10 | Stacking: the pull line must draw **over** the submit button, but never over frame chrome, and never catch clicks | SVG layer z-2 / form z-1 inside the stage, frame rows z-3, `pointer-events:none` on the SVG |
| 11 | Rotated submit (-90°) would be unreachable if the machine were hidden (narrow viewport / reduced motion) while GSAP transforms persisted | engine goes **inert** below 701px and under `prefers-reduced-motion` (applies no transforms); CSS fallback also forces `transform:none !important` on the submit — plain stacked form, fully usable |

---

## Modified files
- `login-machine.js` **(new)** — scoped, destroyable machine engine + SVG contraption template; controller API `setPrimary / setSecondary / setCheckbox / celebrate / reject / destroy` (+ `_debug` for tests)
- `entry-auth.jsx` — `MachineStage` shared component (geometry contract: 270px form, four 60px rows, anchored with the 1000×1000 SVG); both login pages rewired to it; declaration check; unmount-guarded redirect
- `entry.css` — `.rg-*` styles (Swiss-styled rows, message slot, dark-ground inversion, no-machine fallback)
- `index.html` — loads `login-machine.js`

Untouched: `auth.js`, `entry.jsx` (router/guards), `app.jsx`, all dashboard/analytics views, `motion.js`.

---

## Verification checklist
- ✓ Student login: machine mounts, valid roll (`S001`–`S100` pattern) spins gears, valid passcode runs the spiral→hammer→car→hand chain, declaration docks the submit (rotation −90 → 0 confirmed), JWT login → student dashboard
- ✓ Admin login: same chain on the dark ground (inverted ink), JWT login → admin console
- ✓ Wrong credentials → inline 401 message + mechanical jolt; machine state never grants access
- ✓ Unticked declaration → friendly inline error, no request
- ✓ Demo buttons drive both auth and machine states (explicit credentials, no stale state)
- ✓ Navigating away mid-machine → engine destroyed, no leaked timelines/listeners, no stranded transforms
- ✓ Narrow viewport / reduced motion → plain stacked form, submit upright and legible
- ✓ Role guards, logout, refresh persistence, route protection — unchanged and re-verified end-to-end (login → role-scoped dashboard)
- ✓ Console clean

*Testing note: animation states were verified by driving GSAP's clock manually (`gsap.updateRoot`) — preview-iframe rAF throttling freezes all tweens there; in a live tab the ticker runs normally.*
