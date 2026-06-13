# CC — Full Professional Audit Auftrag

Datum: 2026-06-13  
Rolle: CC ist AGENT-B / Co-Auditor. Codex ist AGENT-A / Hauptauditor und orchestriert.  
Bridge: `/Users/gio/Desktop/repos/stealth/docs/agent-bridge/BRIDGE.md` ist Source of Truth.

## Ziel

Fuehre einen vollstaendigen Pre-Live-Audit aller drei StealthX-Projekte durch und fixe alle belastbar reproduzierbaren Findings gemeinsam mit Codex:

- SecureCall: `/Users/gio/Desktop/repos/stealth`
- SecureChat: `/Users/gio/Desktop/repos/securechat`
- Chameleon: `/Users/gio/Desktop/repos/chameleon`

Audit umfasst:

- Android Apps: Code, UI-Text, Settings, Manifest, Deep Links, Wallet/IFR, Payments/Activation, Build/Release, Icons, Versionen.
- Backend: Signaling, Stripe, activation codes, wallet/IFR verification, JSON data persistence, rate limits, production deploy assumptions.
- Websites: Landingpages, download pages, pricing, Stripe buttons, IFR/Uniswap links, wiki links, legal pages, SEO metadata, public claims.
- Wikis/Docs: user manuals, installation guides, roadmap, security audit, README, SECURITY, release notes, GitHub release assets.
- GitHub structure: branches, release assets, stale zips, README claims vs shipped code, broken links.

## Arbeitsregeln

1. **Keine Screenshots lesen oder speichern.** FLAG_SECURE respektieren. Nur Textquellen: `uiautomator`, `logcat`, `adb dumpsys`, `curl`, `rg`, `git`, Buildlogs.
2. **Bridge zuerst und nach jedem relevanten Schritt updaten.**
3. **Nicht parallel an Codex vorbei arbeiten.** Vor groesseren Fixes Bridge lesen und in Bridge schreiben, was du aenderst.
4. **Nicht blind refactoren.** Fixe konkrete Findings mit kleinstem korrektem Patch.
5. **Keine User-Aenderungen revertieren.**
6. **Jeder Fix braucht Verifikation:** Build/Test/Linkcheck/Logcat/Device-Smoke je nach Scope.
7. **Alle Findings severity-ranken:** BLOCKING, HIGH, MEDIUM, LOW.
8. **Wenn etwas bewusst Roadmap ist:** Website/Wiki/UI muss es klar als Roadmap/SOON ausweisen, nicht als fertige Kaufleistung.

## Start

```bash
cd /Users/gio/Desktop/repos
export PATH="/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:$PATH:/Users/gio/Library/Android/sdk/platform-tools"

tail -160 stealth/docs/agent-bridge/BRIDGE.md
tail -80 securechat/BRIDGE.md
tail -80 chameleon/BRIDGE.md

git -C stealth status --short
git -C securechat status --short
git -C chameleon status --short
```

Schreibe danach in `stealth/docs/agent-bridge/BRIDGE.md`:

```md
## 2026-06-13 — AGENT-B Full Audit Start

[AGENT-B] Full Audit gestartet.
Scope: SecureCall, SecureChat, Chameleon, Backend, Websites, Wikis, GitHub Releases.
Arbeitsmodus: Findings dokumentieren, reproduzieren, fixen, verifizieren, Bridge updaten.
```

## Audit-Matrix

### 1. Public Claims vs Shipped Features

Pruefe alle oeffentlichen Claims gegen den Code:

```bash
rg -n "in development|coming soon|SOON|not built|placeholder|mock|dummy|Lock IFR|locked IFR|stake|staking|WalletConnect v2|No central server|zero metadata|Google Play|Play Store|versionCode 67|0\\.1\\.0-alpha|1,000|5,000|1000 IFR|5000 IFR" \
  stealth/website securechat chameleon \
  -S
```

Fix-Regel:

- Fertige Features duerfen als fertig beworben werden.
- Unfertige Features muessen Roadmap/SOON sein.
- Tote Play-Links entfernen, solange Store-Listing nicht public 200 liefert.
- IFR immer als **hold** beschreiben, nicht lock/stake.
- SecureCall aktuell v1.0.40 / basis versionCode 68.
- SecureChat/Chameleon aktuell v0.1.1-alpha / versionCode 2.

### 2. Website/Wiki/Link Audit

Pruefe:

- `stealth/website/**/*.html`
- `securechat/index.html`, `securechat/wiki/**/*.html`, FAQ/privacy/impressum falls vorhanden
- `chameleon/index.html`, `chameleon/wiki/**/*.html`, FAQ/privacy/impressum falls vorhanden

Pflicht:

- Alle APK/GitHub Release Links zeigen auf aktuelle Assets.
- Alle Uniswap/IFR Links zeigen direkt auf offiziellen IFR Token:
  `https://app.uniswap.org/explore/tokens/ethereum/0x77e99917Eca8539c62F509ED1193ac36580A6e7B`
- Pricing/IFR konsistent: 2,000 IFR Pro, 6,000 IFR Premium/Elite, Suite falls dokumentiert 8,000 IFR nur wenn App/Backend es sauber unterstuetzt.
- Wiki Navigation nicht im Header erzwingen; Footer reicht.
- Heller Landingpage/Wiki-Stil konsistent und lesbar.
- Keine dunklen Hover-Kacheln mit unlesbarem Text.

Linkcheck:

```bash
python3 - <<'PY'
import re, pathlib, urllib.request, ssl
roots=[pathlib.Path('stealth/website'), pathlib.Path('securechat'), pathlib.Path('chameleon')]
files=[]
for root in roots:
    if root.name in ('securechat','chameleon'):
        files += [root/'index.html'] + list((root/'wiki').glob('*.html'))
    else:
        files += list(root.rglob('*.html'))
seen=[]; seen_set=set()
for f in files:
    if not f.exists(): continue
    text=f.read_text(errors='ignore')
    for m in re.finditer(r'<a\s+[^>]*href=["\']([^"\']+)["\'][^>]*>|<link\s+[^>]*href=["\']([^"\']+)["\'][^>]*>', text, re.I):
        tag=m.group(0); href=m.group(1) or m.group(2)
        if '<link' in tag.lower() and 'rel="stylesheet"' not in tag.lower() and "rel='stylesheet'" not in tag.lower(): continue
        if href.startswith(('mailto:','tel:','javascript:','#')): continue
        if href.startswith('/') or href.startswith('./') or href.startswith('../') or not re.match(r'https?://', href): continue
        url=href.split('#')[0]
        if url not in seen_set:
            seen_set.add(url); seen.append((str(f),url))
ctx=ssl._create_unverified_context(); fail=[]
for src,url in seen:
    if 'api.stealthx.tech/api/report' in url: continue
    try:
        req=urllib.request.Request(url, method='HEAD', headers={'User-Agent':'StealthX-Audit/1.0'})
        with urllib.request.urlopen(req, timeout=12, context=ctx) as r: code=r.status
    except Exception:
        try:
            req=urllib.request.Request(url, method='GET', headers={'User-Agent':'StealthX-Audit/1.0'})
            with urllib.request.urlopen(req, timeout=12, context=ctx) as r: code=r.status
        except Exception as e:
            fail.append((src,url,str(e)[:180])); continue
    if code >= 400: fail.append((src,url,str(code)))
print(f'checked={len(seen)} failures={len(fail)}')
for item in fail[:80]: print('FAIL', *item, sep=' | ')
PY
```

### 3. Backend / Production Audit

Pruefe:

```bash
cd /Users/gio/Desktop/repos/stealth
rg -n "TODO|FIXME|mock|dummy|placeholder|rateLimit|stripe|webhook|activation|wallet|verify-ifr|lockedBalance|balanceOf|ETH_RPC_URL|JSON|writeJsonAtomic|fs.writeFile" backend/signaling/src -S
curl -s https://api.stealthx.tech/health
curl -s https://api.stealthx.tech/licenses/status | python3 -m json.tool
curl -s -X POST https://api.stealthx.tech/verify-ifr \
  -H "Content-Type: application/json" \
  -d '{"walletAddress":"0x80fF32c5441cBCbFa5c3ce0dC70359BDD05B6958"}' | python3 -m json.tool
```

Pflichtpruefungen:

- Stripe webhook signature + idempotency.
- Checkout tiers: SecureCall Pro/Premium, SecureChat Pro/Elite, Chameleon Pro/Elite, Suite.
- JSON persistence atomisch, keine offensichtlichen corrupt-on-crash Writes.
- IFR Hold-Modell: `balanceOf()` statt `lockedBalance()`.
- LlamaRPC/403 Provider nicht mehr in Prod-ENV.
- Keine Secrets im Repo.
- Rate limits sinnvoll und nicht UX-blockierend fuer legitime Flows.

Tests:

```bash
cd /Users/gio/Desktop/repos/stealth/backend/signaling
npm test
```

### 4. Android Code / App Feature Audit

#### SecureCall

Scope:

- `stealth/client_android/app/src/main`
- Manifest, strings, resources, deeplinks, billing, VPN, call UI, Wallet/IFR, activation.

Check:

```bash
cd /Users/gio/Desktop/repos/stealth/client_android
rg -n "TODO|FIXME|mock|dummy|placeholder|lockedBalance|Lock IFR|stake|BillingClient|WalletConnect|MetaMask|IncomingCall|CALL_ACCEPT|verify-ifr|versionCode|versionName" app/src/main app/build.gradle -S
./gradlew testFreeDebugUnitTest assembleFreeDebug assembleFreeRelease bundleFreeRelease
```

Pflicht:

- Incoming Call UI erscheint.
- Accept beendet Klingeln beim Anrufer.
- Phone Prompt `Skip` blockiert keine frischen Flows.
- Deep Links `/invite/` + assetlinks kompatibel mit Play App Signing SHA-256.
- Billing Library v7.1.1.
- 16KB page-size support/Play warnings erledigt.
- AAB bei SecureCall-Codeaenderungen auf Desktop aktualisieren.

#### SecureChat

Scope:

- `securechat/app`, `securechat/presentation`, `securechat/data`, `securechat/domain`, `securechat/features`, `securechat/stealthx-ifr`

Check:

```bash
cd /Users/gio/Desktop/repos/securechat
rg -n "TODO|FIXME|mock|dummy|placeholder|0\\.1\\.0-alpha|lockedBalance|Lock IFR|stake|WalletConnect|MetaMask|comingSoon|SOON|Group Messaging|File Transfer|Kaspa|Onion|Decoy|Threat" . -S
./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleRelease
```

Pflicht:

- Settings verkauft nur gebaute Features:
  - Free: E2E Messaging, QR Contact Exchange
  - Pro: Unlimited Contacts
  - Elite: Emergency Broadcast
  - Andere Features klar Roadmap/SOON
- Wallet-App-Erkennung funktioniert via package visibility.
- IFR Hold per `balanceOf()`.
- Version im Settings/About dynamisch.
- Release APK auf Desktop aktualisieren wenn Codeaenderung.

#### Chameleon

Scope:

- `chameleon/app`, `chameleon/presentation`, `chameleon/features`, `chameleon/core`, `chameleon/stealthx-ifr`

Check:

```bash
cd /Users/gio/Desktop/repos/chameleon
rg -n "TODO|FIXME|mock|dummy|placeholder|0\\.1\\.0-alpha|No central server|encrypted messenger|lockedBalance|Lock IFR|stake|WalletConnect|MetaMask|comingSoon|SOON|Advanced Threat Detection|Decoy Profile" . -S
./gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleRelease
```

Pflicht:

- Intro/Settings/Wiki Claims passen zu App.
- Decoy Profile Tier stimmt Code/Docs/UI.
- Advanced Threat Detection ist SOON, falls nicht fertig.
- Wallet-App-Erkennung funktioniert via package visibility.
- IFR Hold per `balanceOf()`.
- Version im Settings/About dynamisch.
- Release APK auf Desktop aktualisieren wenn Codeaenderung.

### 5. Device Audit

Wenn angeschlossen:

- Tab S4 `ce12182c68644439037e`
- S7 `ce10160adc00152604`
- S10 `RF8N313QMFL` falls wieder da

Pruefen:

```bash
adb devices
for d in ce12182c68644439037e ce10160adc00152604 RF8N313QMFL; do
  adb -s "$d" shell dumpsys package com.securecall.app.free | grep -E "versionName|versionCode" || true
  adb -s "$d" shell dumpsys package com.securecall.app.pro | grep -E "versionName|versionCode" || true
  adb -s "$d" shell dumpsys package com.securecall.app.premium | grep -E "versionName|versionCode" || true
  adb -s "$d" shell dumpsys package com.stealthx.securechat | grep -E "versionName|versionCode" || true
  adb -s "$d" shell dumpsys package com.stealthx.chameleon | grep -E "versionName|versionCode" || true
done
```

Text-only smoke:

```bash
for d in ce12182c68644439037e ce10160adc00152604 RF8N313QMFL; do
  for p in com.stealthx.securechat com.stealthx.chameleon; do
    adb -s "$d" logcat -c
    adb -s "$d" shell monkey -p "$p" --ignore-crashes --ignore-timeouts --pct-syskeys 0 --throttle 30 120
    adb -s "$d" logcat -d | grep -Ei "FATAL EXCEPTION|ANR in|AndroidRuntime.*(Exception|Error)" | tail -30 || true
  done
done
```

SecureCall call tests nur textbasiert:

- S7 Pro -> S4 Free
- S7 Pro -> S10 Premium, sobald S10 wieder angeschlossen
- Logcat auf `INVITE`, `ACCEPT`, `OFFER`, `ANSWER`, `connected`, `CALL_END`.

### 6. GitHub / Release Audit

Pruefe:

```bash
gh release view v1.0.40 -R NeaBouli/stealth --json assets --jq '.assets[].name'
gh release view v0.1.1-alpha-securechat -R NeaBouli/securechat --json assets --jq '.assets[].name'
gh release view v0.1.1-alpha-chameleon -R NeaBouli/chameleon --json assets --jq '.assets[].name'
```

Pflicht:

- Desktop-Artefakte und GitHub Release Assets stimmen.
- SecureCall AAB nur bei SecureCall-Codeaenderung neu bauen.
- README/SECURITY/Docs duerfen nicht `Internal Alpha / not for public use` sagen, wenn Website APK als verfuegbar bewirbt, ausser sauber kontextualisiert.

## Findings/Fix-Protokoll

Fuer jedes Finding:

```md
### [AGENT-B][SEVERITY] <Titel>
- Repo:
- Datei/Zeile:
- Was:
- Repro:
- Fix-Plan:
- Status: OPEN/FIXING/FIXED/VERIFIED
```

Fix-Regel:

- BLOCKING/HIGH: fixen, testen, committen, pushen.
- MEDIUM/LOW: fixen, wenn risikoarm; sonst dokumentieren.
- Nach jedem Commit Bridge updaten mit Commit Hash und Verifikation.

## Abschlussbericht

Am Ende in die Bridge schreiben:

```md
## 2026-06-13 — AGENT-B Full Audit Report

Result:
- BLOCKING: X found / X fixed / X open
- HIGH: X found / X fixed / X open
- MEDIUM: X found / X fixed / X open
- LOW: X found / X fixed / X open

Builds:
- SecureCall:
- SecureChat:
- Chameleon:
- Backend:

Device Tests:
- S4:
- S7:
- S10:

Website/Wiki:
- Linkcheck:
- Claims:
- IFR/Uniswap:
- Stripe:

GitHub Releases:
- SecureCall:
- SecureChat:
- Chameleon:

Open External Blockers:
- Google Play Review:
- S10 currently disconnected if still unavailable:
- Any user/manual action:
```

## Wichtigster aktueller Kontext

- IFR Hold-Modell ist live:
  - `POST https://api.stealthx.tech/verify-ifr`
  - Wallet `0x80fF32c5441cBCbFa5c3ce0dC70359BDD05B6958`
  - Erwartung: `success=true`, `tier=premium`, `balanceAmount=33333333`, `model=hold`.
- S10 ist laut User aktuell abgeklemmt und kommt spaeter wieder.
- Keine Screenshots verwenden.
