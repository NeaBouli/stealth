id: SC-KIMI-STX-CLAIMS-20261011
status: ok
worker: kimi
branch: agent/kimi/SC-KIMI-STX-CLAIMS-20261011 (base origin/main c02146a8; branch carried 47 pre-existing bridge/docs commits, kept as found)
summary: Public-claims trace (website/*.html, llms.txt, operator docs; no MAP.md in repo — trace mapped ad hoc). STX-39 audited claims reworded to internal review + public register #84 linked; STX-42/56 Play status corrected to live v1.0.49 (URL 200 verified), llms.txt self-contradiction + Railway + contacts aligned with SECURITY.md/legal pages; STX-44 docs hosting drift Railway -> Hetzner (api.stealthx.tech), no IPs/internal hosts/paths published.
files: website/index.html, website/security.html, website/download.html, website/llms.txt, WORKFLOWS.md, docs/ADMIN_BROADCAST.md, docs/PLAY_STORE_UPLOAD_CHECKLIST.md, docs/BACKEND_DEPLOYMENT_GUIDE.md, docs/LAUNCH_GUIDE.md, docs/YOUR_BACKEND_URLS.md, docs/RAILWAY_ENV_VARS.md
tests: curl Play URL -> 200, listing v1.0.49 (2026-10-11); gh release list -> latest v1.0.48; grep -> no "in review"/"audited once" left outside wiki; markdownlint-cli2@0.13.0 on 7 changed .md -> 124 findings vs 125 on base (no new); visual gate: headless Chrome+puppeteer before/after 390x844 + 1440x1000 (plus 1180x820/820x1180 after) for index/security/download in .fleet/reports/SC-KIMI-STX-CLAIMS-20261011/{before,after}; inspected #flow, #audit, security-full, download-header: no overflow/clipping, mobile buttons stack; grep table in claims-table.md
risks: Play version numbers in copy can drift again; branch diff includes the 47 pre-existing commits; timebox 45 min exceeded (~70) due to reveal-animation screenshot setup
security: none
next: Draft PR opened; review owed to Codex; live recheck after deploy owed to orchestrator. Follow-ups outside brief paths: marketing/play_store "Independently audited", .coderabbit.yaml Railway mention, wiki pages (excluded by brief).
