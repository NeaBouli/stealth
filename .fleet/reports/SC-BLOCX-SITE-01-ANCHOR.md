id: SC-BLOCX-SITE-01-ANCHOR
status: ok
worker: claude
branch: agent/claude/SC-BLOCX-SITE-01-FIX
summary: Module WEB.PLATFORM.BLOCX, hop nav link -> #platform -> section h2 (map unchanged). Real click navigation passes at all 5 viewports; heading is fully clear of the sticky navbar. The earlier element-capture overlap is a screenshot artifact (element.screenshot re-scrolls the section), not a user-path defect. sourceSHA 8a76bdbebae6220797c77ba955583b7e95612082 (code 9a094e14c1fa7b2f5795f966c83a0e1ecf8e28fa), source immutable, no application change.
files: outputs/website/SC-BLOCX-SITE-01-ANCHOR/{anchor.cjs,measurements.json,anchor-desktop-1440x1000.png,anchor-tablet-landscape-1180x820.png,anchor-tablet-portrait-820x1180.png,anchor-mobile-390x844.png,anchor-narrow-360x800.png} (coordinator workspace); this report only in the worktree.
tests: Path: fresh load, (collapsed nav: click #navToggle, menu visible) click a[href="#platform"], poll scrollY until stable (700-1200 ms smooth-scroll settle), regular viewport screenshot; no DOM/style/nav overrides. Measured h2 "Three products today. One more in development." vs sticky #navbar (position:sticky):
 1440x1000 navBottom 129.5, h2 267.5-368.3, gap 138.0, scrollY 1047
 1180x820 navBottom 129.5, h2 267.1-353.9, gap 137.6, scrollY 949
 820x1180 (menu) navBottom 143.0, h2 280.2-343.2, gap 137.2, scrollY 1491
 390x844 (menu) navBottom 119.0, h2 232.1-326.6, gap 113.1, scrollY 1938
 360x800 (menu) navBottom 119.0, h2 232.0-326.5, gap 113.0, scrollY 1987
 All: h2 inside viewport, h2 top not under nav, h2 top-left probe hits heading, no horizontal overflow. Section label "PLATFORM" also clear (gap 73-98 px). All 5 PNGs inspected: heading fully readable, nav above it. Outcome: scroll lands via smooth scroll; location.hash stays empty (existing JS handles the click without hash update); focus remains on the link on desktop/landscape, on BODY on collapsed nav; mobile menu closes after the click. No prior suites rerun.
risks: Checked heading is the section h2; bLocX card itself sits below it and is not in view after navigation on 390/360/820 (card top ~y 590+/below fold) - expected, not an obscuring issue. Chromium 1243 headless only; no real device/Safari. Empty hash means the URL is not shareable via nav click (pre-existing).
security: none; local static server on 127.0.0.1 only, stopped; no secrets/operator data.
next: Root views the 5 PNGs and may close the element-capture concern. Optional separate task: use viewport screenshots for the visual gate instead of element.screenshot.
