# SecureCall — device, package and UI audit

- **Date:** 2026-09-18
- **Auditor:** Claude Code (Opus 5), commissioned by Gio
- **Baseline:** `origin/main` @ `e06d018`
- **Artifact audited:** `com.securecall.app.premium` **1.0.48-premium** (versionCode 78015), pulled from a real device
- **Devices:** Samsung Galaxy S10 (SM-G973F, Android 12, 320×676 dp) and Galaxy Tab S4 (SM-T835, Android 10, 711×1138 dp), both over USB
- **Mode:** READ-ONLY on the code. No fix was applied — this report proposes them. The only device-side changes were temporary display-metric overrides on the tablet, reverted at the end, and one test file that was deleted again.

---

## 0. Relationship to PR #83 (Collateral Web3 Open Audits, STX-01…STX-62)

That series audits the backend, protocol, crypto and content layers on the same baseline and is thorough. **This report deliberately does not repeat it.** Two areas are genuinely additive:

1. **The UI/device layer**, which the existing series does not cover at all — verified by searching it for `48dp`, `Touch`, `BottomNavigation`, `labelVisibility`, `320dp` and `small screen`: zero hits.
2. **A time-dated re-measurement of the certificate chain.** STX-23 correctly reports that the declarative XML pin-set expired on 2026-08-14 and notes that the OkHttp `CertificatePinner` still enforces "leaf + R12 + ISRG Root X1". Measured today, that statement no longer holds — see A1. This is an update to STX-23, not a contradiction of it.

---

## 1. Findings

| # | Severity | Finding | Device-specific? |
|---|---|---|---|
| A1 | **HIGH (availability)** | OkHttp pin-set matches the live chain only through the leaf; intermediate and root pins are stale — the next key rotation locks every installed client out of signaling | no |
| A2 | MEDIUM | Bottom navigation loses **all** text labels and falls to 47 dp on a 320 dp-wide phone | phone only |
| A3 | MEDIUM | Dial-pad keys are 91×42 dp on the S10 — below the 48 dp minimum touch target | phone only |
| A4 | LOW | Settings rows are 35 dp tall on both device classes | both |
| A5 | LOW (structural) | No screen-size resource qualifiers exist at all — the reason "it works on the tablet and breaks on the phone" | — |
| A6 | INFO | OkHttp debug logging active in the release build; universal APK is 62 MB and ships x86/x86_64 | — |

---

### A1 — Certificate pins no longer match the live chain except through the leaf *(HIGH, availability)*

**Measured 2026-09-18** against `api.stealthx.tech`:

| Chain position | Subject | SPKI pin (SHA-256) | In the app? |
|---|---|---|---|
| leaf | `CN=api.stealthx.tech` | `1e85xNSEj+dcImOJS0iNkfMZOrZdvJJzzPCqT1/CZDc=` | ✅ pin #1 |
| intermediate | `C=US, O=Let's Encrypt, CN=YR2` | `nWN7PSep5XDQdge5zK24CnCRXHr3KvzhKEGxsdqCX9E=` | ❌ not pinned |
| root | `C=US, O=ISRG, CN=Root YR` | `fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=` | ❌ not pinned |

The app pins three values in `client_android/app/src/main/java/com/securecall/app/net/NetworkManager.kt:215-218`:

```
1e85xNSEj+dcImOJS0iNkfMZOrZdvJJzzPCqT1/CZDc=   leaf              → still matches
kZwN96eHtZftBWrOZUsd6cA4es80n3NzSk/XtYz2EqQ=   Let's Encrypt R12 → no longer in the chain
C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=   ISRG Root X1      → no longer in the chain
```

Let's Encrypt has moved this certificate to its newer hierarchy (`YR2` / `ISRG Root YR`). The leaf renewed on 2026-09-14 (valid to 2026-12-13) and kept its key, which is the only reason the first pin still matches.

**Why this is the more urgent half of the pinning story.** The XML pin-set (STX-23) expired, so Android ignores it — that is a *security* degradation, and a mild one given the OkHttp layer. The OkHttp `CertificatePinner` has **no expiry** and *is* enforced, on the signaling WebSocket, the heartbeat client and the GhostNet client (`NetworkManager.kt:228`, `HeartbeatClient.kt:71`, `GhostNetWebSocketClient.java:71`). It currently passes on a single pin. The moment the leaf key changes — a routine renewal with a fresh key, a forced reissue, a server migration — **all three pins fail simultaneously and every installed client loses signaling**. There is no server-side remedy: the fix would have to ship through Play review and reach users who may not update for days.

This is the classic pinning failure mode: pinning to a leaf you do not control the rotation of, with backup pins that quietly stopped being part of the chain.

**Proposed fix** (not applied):

```kotlin
// NetworkManager.kt — pin the current hierarchy, keep one long-lived anchor,
// and keep a spare so a single rotation can never lock clients out.
fun buildCertificatePinner(): CertificatePinner = CertificatePinner.Builder()
    .add("api.stealthx.tech", "sha256/1e85xNSEj+dcImOJS0iNkfMZOrZdvJJzzPCqT1/CZDc=") // leaf (current)
    .add("api.stealthx.tech", "sha256/nWN7PSep5XDQdge5zK24CnCRXHr3KvzhKEGxsdqCX9E=") // LE YR2 intermediate
    .add("api.stealthx.tech", "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=") // ISRG Root YR
    .add("api.stealthx.tech", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1 (legacy anchor)
    .build()
```

Pin the intermediate and root rather than only the leaf, keep the old root as a transition anchor, and mirror the same values in `network_security_config.xml` with a future `expiration` so the two layers stop disagreeing. Then add an external check that fails loudly when the live chain stops matching the shipped pins — a weekly job comparing the measured chain against the constant would have surfaced both this and STX-23 before an incident.

**How to re-measure:**
```bash
echo | openssl s_client -connect api.stealthx.tech:443 -servername api.stealthx.tech -showcerts 2>/dev/null \
  | awk '/BEGIN CERT/,/END CERT/' > chain.pem
# per certificate:
openssl x509 -in cert.pem -pubkey -noout | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
```

---

### A2 — Bottom navigation loses all labels on a 320 dp phone *(MEDIUM, phone only)*

Measured from the live view hierarchy on both devices, same app build, dialer tab active:

| | Tab S4 (711 dp wide) | **S10 (320 dp wide)** |
|---|---|---|
| nav item size | 168 × 80 dp | **80 × 47 dp** |
| `navigation_bar_item_labels_group` | 40–80 × 16 dp, text rendered | **0 × 0 dp on every item** |
| visible label text | "Anrufe", "Kontakte", "Dialer", "Einstellungen" | none |

**Cause.** `activity_main.xml:49-56` declares the `BottomNavigationView` with `layout_height="wrap_content"` and no `app:labelVisibilityMode`. Material's default is `auto`, which drops labels once four items no longer fit — at 320 dp each item gets 80 dp, and the German strings ("Einstellungen") do not fit. The bar then collapses to 47 dp, which also puts the tab targets just under the 48 dp minimum.

**Proposed fix** (not applied):

```xml
<com.google.android.material.bottomnavigation.BottomNavigationView
    android:id="@+id/bottomNav"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="72dp"
    android:layout_gravity="bottom"
    app:labelVisibilityMode="labeled"
    app:itemTextAppearanceActive="@style/TextAppearance.SecureCall.NavLabel"
    app:itemTextAppearanceInactive="@style/TextAppearance.SecureCall.NavLabel"
    app:menu="@menu/bottom_nav_menu" />
```

with a `NavLabel` appearance at 10–11 sp. If "Einstellungen" still does not fit at 320 dp, shorten the label rather than let the framework silently drop all four.

---

### A3 — Dial-pad keys fall below the minimum touch target on phones *(MEDIUM, phone only)*

| | Tab S4 | S10 (real) | 360 dp simulated |
|---|---|---|---|
| key size | comfortable, 0 findings | **91 × 42–43 dp** | **104 × 36 dp** |
| keys below 48 dp | 0 | 12 of 12 | 12 of 12 |

**Cause.** The keys have no minimum size at all. `styles.xml:37-47` defines `Widget.SecureCall.DialButton` with `layout_height=0dp` + `layout_rowWeight=1`, inside a `GridLayout` that is itself `layout_height="0dp"` with `layout_weight="3"` (`fragment_dialer.xml:57-64`). The key height is therefore a pure fraction of leftover vertical space. On the tablet that fraction is generous; on a 676 dp-tall phone — minus app bar, number field, ABC toggle, call FAB and its `96dp` bottom margin — it lands at 42 dp. Android's minimum recommended touch target is 48 × 48 dp, and a dial pad is the one control in a phone app that must not be fiddly.

**Proposed fix** (not applied) — a floor, not a redesign:

```xml
<!-- styles.xml: Widget.SecureCall.DialButton -->
<item name="android:minHeight">48dp</item>
<item name="android:minWidth">48dp</item>
```

```xml
<!-- fragment_dialer.xml: guarantee the pad its natural size, let the screen scroll instead -->
<GridLayout
    android:id="@+id/dialPad"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="220dp"
    ... />
```

and reduce `fabCall`'s `layout_marginBottom` from `96dp` to `@dimen/spacing_md`, since the bottom navigation already reserves that space. Re-measure afterwards: all twelve keys must report ≥ 48 dp on the S10.

---

### A4 — Settings rows are 35 dp tall *(LOW, both device classes)*

The settings list renders clickable `LinearLayout` rows at 320 × 35 dp on the S10 and 711 × 37 dp on the tablet. Unlike A2 and A3 this is not screen-dependent — it is simply below the 48 dp target everywhere. Set `android:minHeight="48dp"` on the preference row layout.

---

### A5 — No screen-size resource qualifiers exist *(LOW, structural — the reason for the pattern)*

```
client_android/app/src/*/res/  →  layout, values, values-de, values-night, drawable, mipmap-*
no layout-sw*, no values-sw*, no values-w*, no layout-land
```

Every screen ships one layout for a 320 dp phone and an 1138 dp tablet alike, and the layouts lean on weights to absorb the difference. That works until a weight-driven dimension crosses a usability floor, which is exactly A2 and A3. It is also why testing on the tablet cannot catch these defects — the tablet is the configuration where weights are generous.

Two cheap, durable measures:

1. Introduce `values-sw320dp/dimens.xml` (or simply `dimens.xml` defaults plus `values-sw600dp` overrides) for the dial-pad minimum, the nav height and the FAB margin, so small and large screens stop sharing one number.
2. Add a UI smoke test that runs the existing instrumentation on a 320 × 640 dp configuration. The repo already has `android-instrumentation.yml` in CI; a `wm size`/`wm density` override before the run, plus an assertion that no clickable node is smaller than 48 dp, would have caught A2, A3 and A4 mechanically. The measurement script used for this audit is ~40 lines against `uiautomator dump`.

Also worth noting: there is no `layout-land` anywhere, and the dialer's fixed vertical budget would be worse in landscape. Not verified in this pass — the devices were tested in portrait only.

---

### A6 — Release hygiene *(INFO)*

- **OkHttp debug logs in the release build.** `logcat` on the S10 shows `D/okhttp.TaskRunner … OkHttp WebSocket https://api.stealthx.tech/… writer` while the app is idle. Low impact — since Android 4.1 an app cannot read another app's log — but it is avoidable noise from a privacy-positioned product. Positively: I typed a 10-digit number into the dialer and **it never appeared in the log**; no PII leakage was observed.
- **Universal APK is 62 MB**, of which 58 MB is `lib/` across four ABIs including `x86` and `x86_64` (WireGuard + WebRTC + own crypto). Fine for Play, which splits per ABI from the AAB, but heavy for the direct-APK distribution path. Consider dropping x86 from the universal artifact or publishing per-ABI APKs for direct download.

---

## 2. What was verified as sound

Checked and found correct, so it does not have to be re-checked:

- **Manifest hardening:** `allowBackup=false`, `usesCleartextTraffic=false`, `networkSecurityConfig` set, no `debuggable`, `extractNativeLibs=false`.
- **Permissions are lean and appropriate** for a calling app: 16 in total — `RECORD_AUDIO`, `READ_CONTACTS`, the three `FOREGROUND_SERVICE_*` types, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, networking and wake/boot. No location, no storage, no `READ_PHONE_STATE`.
- **Exported surface is minimal:** of 25 components only four are exported — `MainActivity` (launcher), `BootReceiver`, and the Firebase and ProfileInstaller receivers, the latter two permission-guarded by the framework's own permissions.
- **R8 works as intended.** Only 34 `com.securecall.*` class names survive — manifest components, XML-inflated views and the two explicitly kept JNI classes. Internal code is renamed (`La/a;`, `…/transport/ws/a`).
- **16 KB page alignment: all six `arm64-v8a` libraries report `p_align=16384`**, so the Play requirement for Android 15 devices is met. `zipalign -c 4` verifies.
- **Signing:** APK Signature Scheme v2, RSA 4096. No keystore, password or alias is committed; `build.gradle:170-177` reads them from the environment. (Scheme v3 is not enabled — worth adding for key-rotation support, not urgent.)
- **No secrets in the package:** no API keys, tokens or private keys in the DEX; only public URLs. TURN credentials are runtime-issued by design, as documented in `build.gradle:185-188`.
- **Crypto primitives are sound:** XChaCha20-Poly1305 with 192-bit random nonces from `OsRng`, X25519, HKDF-SHA256, `zeroize` on key types, and exactly one `.expect()` outside tests, on a documented length invariant. 1 034 lines total — small enough to review properly. (The protocol-level concerns remain as filed in STX-21…STX-28; this pass did not re-audit them.)

---

## 3. Method and limits

Every number above comes from one of: a live view hierarchy (`uiautomator dump`) on the named device at its real metrics; `aapt2`/`apksigner`/`zipalign` on the APK pulled from the S10; an ELF program-header parse for page alignment; `openssl s_client` against the live host on 2026-09-18; or the source at `e06d018`.

Screenshots were not possible — the app sets `FLAG_SECURE`, which is correct for this product; the geometry was measured numerically instead.

Not covered: landscape orientation, tablet-specific layouts beyond the two tested screens, the free and pro flavors (premium was audited), the call and incoming-call screens under a real call, accessibility services (TalkBack, large font scales), the backend and protocol layers (see PR #83), and anything requiring a Play Console or production server session.

An observation worth a separate ticket: the tablet runs **1.0.48-pro** while the S10 runs **1.0.48-premium**, and `BRIDGE.md` records 1.0.49-free in Play production. Three flavors at two versions across the test fleet makes "reproduced on device" ambiguous. Pinning the test fleet to one build per release would make future UI findings unarguable.
