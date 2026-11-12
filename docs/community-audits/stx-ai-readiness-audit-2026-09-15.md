# StealthX / SecureCall — AI-Readiness & SEO Anchor Audit

- **Series:** Collateral Web3 Open Audits
- **Date:** 2026-09-15
- **Target:** stealthx.tech (live) + website source @ `e06d018417bae5be16bf6b89d0a1887586a99d3b`
- **Scope:** llms.txt, robots.txt, sitemap.xml, JSON-LD (all pages), meta/OG/canonicals, ai.txt, hreflang, redirects config, security headers, indexing exposure of transactional pages
- **Method:** deep-recon agent sweep + lead live probes of every claim (anonymous GET/HEAD only)
- **Register:** STX-56 … STX-62 (this report) — **0 Critical / 0 High / 2 Medium / 4 Low / 1 Info**

---

## Executive summary

The AI-facing foundation is unusually good for a project this size: a 115-line llms.txt with multilingual summaries, explicit AI-crawler allows in robots.txt, a complete sitemap (27/27 URLs live — lead-probed), 62 JSON-LD blocks, and consistent canonicals. The gaps are factual drift inside llms.txt (self-contradicting Play status, stale Railway hosting), structured-data spam signals (return/shipping schema for a digital download), and indexing/config hygiene (invite page in the sitemap, deprecated crawler names, no security headers on the GitHub Pages host).

## Severity table

| ID | Severity | Title |
|----|----------|-------|
| STX-56 | Medium | llms.txt factual drift: self-contradicting Play status (live vs under review), stale Railway hosting, contact contradiction |
| STX-57 | Medium | JSON-LD: MerchantReturnPolicy/OfferShippingDetails for a digital app (structured-data spam risk); zero blocks on download/ifr/siwe |
| STX-58 | Low | Sitemap: invite.html (transactional) included; audit.html (index,follow) missing; uniform stale lastmod |
| STX-59 | Low | robots.txt: deprecated crawler token (`Claude-Web`), no `ClaudeBot`; no Disallow for transactional returns; no ai.txt |
| STX-60 | Low | No security headers on GitHub Pages host (no HSTS/CSP/X-Frame-Options/X-Content-Type-Options/Referrer-Policy — lead live-verified) |
| STX-61 | Low | `_redirects` targets the wrong GitHub org (`nicokimmel/stealth`) and is inert Netlify syntax on a Pages host |
| STX-62 | Info | hreflang only `en` + `x-default` despite DE/EL/IT/ES/RU/ZH/PT/AR/KO content blocks; 404.html doubles as SPA invite router with GA |

---

## STX-56 — Medium — llms.txt factual drift

**Evidence (lead-verified against live state):**
- Line 68: "Google Play: public listing live" vs line 73: "v1.0.49 / versionCode 78016 **remains under review**" — internal contradiction; live probe confirms the listing **is** live (STX-42).
- Technical-stack block: "Signaling: WebSocket (Node.js/Express on **Railway**)" — DNS shows Hetzner (STX-44).
- "Contact: GitHub Issues only" (line 79) vs support@/contact@/kaspartisan@ addresses published elsewhere (STX-47).
- Pricing block (lines 37–43) inherits the €25-vs-€49 conflict (STX-38).
- "best alternative to Signal, Wickr, and Silent Phone" (line 11) — superlative that AI assistants will parrot (STX-46).
- Accurate parts worth keeping: 3-device QA statement, source-available wording, VPN-split description, no-Double-Ratchet claim (correctly absent — unlike README/website, STX-22).

**Recommendation:** regenerate llms.txt from the same canonical facts file as the website; it is otherwise the best AI anchor the project has.

## STX-57 — Medium — JSON-LD structured-data issues

**Evidence:** index.html has 5 blocks (SoftwareApplication, Organization, BreadcrumbList, FAQPage, Product) — but the Product schema includes `MerchantReturnPolicy` with `returnMethod: ReturnByMail`, `merchantReturnDays: 14` and full `OfferShippingDetails` (`index.html:214-231`) **for a digital app download** — exactly the pattern search engines flag as structured-data spam. Meanwhile `download.html`, `ifr.html`, `siwe.html` have **zero** JSON-LD blocks (lead-verified), and `audit.html`/`invite.html` carry generic WebPage only. `softwareVersion: "1.0.48-free"` is accurate; offers only list the €0 Free tier.

**Recommendation:** drop return/shipping schema from the Product block; add SoftwareApplication JSON-LD to download.html; keep FAQPage (it is valid).

## STX-58 — Low — Sitemap hygiene

**Evidence:** `sitemap.xml` = 27 URLs, all live (lead-probed 27/27). But: `invite.html` — a per-user transactional page carrying `?id=`/`?name=` parameters — is sitemap-listed (encouraging indexed invite URLs, compounded by GA on the page, STX-29); `audit.html` is `index,follow` yet **absent** from the sitemap; `download.html` (the main conversion page) is absent (consistent with its noindex, but SEO-incoherent for the primary funnel); all lastmod values are uniformly 2026-08-28 (stale relative to the 2026-09-04 deploy, visible in the live `last-modified` header).

**Recommendation:** remove invite.html (and set noindex on it), add audit.html, refresh lastmod per real deploy dates.

## STX-59 — Low — robots.txt / ai.txt

**Evidence:** robots.txt explicitly allows 8 AI crawlers (good) but uses the deprecated `Claude-Web` token instead of `ClaudeBot`; allows crawling of payment-success/success/return pages (they rely on meta-noindex instead — works, but belt-and-suspenders would Disallow them); references the sitemap correctly. `ai.txt` does not exist (live 404 — optional emerging standard).

**Recommendation:** update to `ClaudeBot`; consider Disallow for `/payment-success.html`, `/success.html`, `/return`; optionally publish ai.txt mirroring llms.txt.

## STX-60 — Low — No security headers on the Pages host

**Evidence (lead live-verified full header dump, `evidence/live/headers-apex.txt`):** stealthx.tech serves via GitHub Pages with **no** `Strict-Transport-Security`, `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy` or `Permissions-Policy`; `access-control-allow-origin: *` (Pages default, harmless for static assets). API host additionally lacks HSTS and exposes `x-powered-by: Express` (STX-20). Impact is bounded for a static site (clickjacking of the IFR/checkout frames would matter once enabled), but trivially fixable at the CDN layer or by moving hosts.

## STX-61 — Low — `_redirects` wrong org + inert syntax

**Evidence:** `website/_redirects` maps `/github` and `/source` to `https://github.com/**nicokimmel**/stealth` — a different org (likely fork heritage) — and uses Netlify syntax on a GitHub Pages host where the file is inert; the `/* → /404.html 404` rule would break all pretty URLs if ever deployed to Netlify as-is.

## STX-62 — Info — hreflang / 404-router

hreflang declares only `en` + `x-default` while llms.txt and privacy pages carry DE/EL/IT/ES/RU/ZH/PT/AR/KO blocks (either add real localized pages or trim the blocks); `404.html` doubles as the SPA invite router (serves invite links through a 404 status path) and loads GA (STX-29) — real 404s correctly return status 404 (lead-probed).

---

## Verified strengths (evidence-checked)

- llms.txt present, 115 lines, 9 language summaries, technically mostly accurate — rare and valuable for AI search.
- robots.txt AI-crawler policy explicit and permissive; sitemap referenced.
- Sitemap 27/27 live; canonicals on 28 pages (missing only on noindex utility pages — correct); real 404 status works.
- JSON-LD SoftwareApplication/Organization/FAQPage blocks validate structurally; Bing site auth consistent with meta.
- No `http://` resources anywhere; invite domain-guard prevents off-domain invite rendering.
