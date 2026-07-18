# Glass / Apple-Premium UI Redesign — Progress & Handoff

**Goal:** Redesign the whole OfflinePay Android app (Kotlin + Jetpack Compose) with an
**Apple-style premium glassmorphism** look — frosted translucent surfaces, an ambient
"aurora" gradient background, hairline borders, soft shadows, iOS-style spring motion.
**Scope agreed with user:** FULL app redesign, building **in place** (no worktree / PR —
this is not a git repo).

Last updated: 2026-07-05.

## ✅ STATUS: COMPLETE — verified compiling

- Design-system foundation: **done** (`:core:designsystem:compileDebugKotlin` ✅).
- All **15 screens** migrated to the glass system (audited file-by-file — 15/15 DONE).
- Full app compiles: **`:app:compileDebugKotlin` BUILD SUCCESSFUL** (serial, see build note).
  Only two *pre-existing* warnings remain (unused `context` in `CameraPreviewComposable.kt`,
  unused `responseText` in `UssdViewModel.kt`) — unrelated to the redesign.
- Remaining optional work: eyeball on a device/emulator (build env permitting).

> ⚠️ **Low-RAM build gotcha (3.9 GB host):** compiling all modules in parallel OOMs
> ("Not enough memory to run compilation"). Always pass **`--max-workers=1`** to the
> `:app:compileDebugKotlin` command below so modules compile serially within the 1280m heap.
> Serial full-app compile takes ~20 min cold on this host.

---

## Key constraints (already accounted for)

- **minSdk 26**, compileSdk/targetSdk 34, Compose BOM `2024.06.00`, Kotlin `1.9.24`.
- True `RenderEffect` blur only works on **API 31+**. So the glass system is built to be
  **dependency-free** and self-contained (NO Haze/third-party lib — avoids build-break risk
  in this fragile offline build env):
  - Translucent fills + soft **radial-gradient** aurora blobs render on ALL API levels.
  - Real `Modifier.blur(64.dp)` on the background blob layer is layered on **only** on API 31+
    as an enhancement (see `OfflinePayBackground.kt`).

## How to BUILD / verify (IMPORTANT — the normal wrappers are broken here)

- `./gradlew` (bash script) is **CRLF-corrupted** → "Could not find or load main class".
- `gradlew.bat` piped through MSYS **swallows all output** (looks like it does nothing).
- ✅ **Working command** — invoke the wrapper jar directly with the JBR java:

```bash
cd "D:/Apps/OFFLINE PAYMENT APPPPPPPPP"
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export GRADLE_USER_HOME="D:/GradleHome"
"$JAVA_HOME/bin/java.exe" -Dorg.gradle.appname=gradlew \
  -classpath "gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain \
  :core:designsystem:compileDebugKotlin --offline --console=plain 2>&1 | tail -40
```

- Cold config ~2 min; warm faster. Env is unstable (many `hs_err_pid*.log` JVM crash dumps).
- To compile everything after screen edits: replace the task with `:app:compileDebugKotlin`
  (or `assembleDebug`) **and add `--max-workers=1`** (see low-RAM gotcha above — without it the
  parallel Kotlin workers OOM). Compile a single feature module with e.g.
  `:feature:payment:compileDebugKotlin`.
- Stopping warm daemons first frees memory: prepend a `... GradleWrapperMain --stop` call.

---

## DONE — Design-system foundation (`core/designsystem/.../designsystem/`)

All NEW files unless noted. Public API that screens should use:

| File | What it provides |
|------|------------------|
| `GlassTokens.kt` | `GlassTokens` data class + `LightGlassTokens`/`DarkGlassTokens`/`HighContrastGlassTokens`, `glassTokensFor(isDark,isHighContrast)`, `LocalGlassTokens` CompositionLocal. Translucent fills, borders, sheen, aurora colors. |
| `GlassSurface.kt` | `Modifier.glassSurface(tokens, shape, elevated)` — frosted treatment (shadow+clip+fill+sheen+hairline border). `GlassCard(modifier, shape, elevated, onClick, content)` — drop-in premium card. `GlassCornerRadius = 26.dp`. |
| `OfflinePayBackground.kt` | `OfflinePayBackground { content }` — ambient aurora backdrop (gradient + 3 soft radial blobs + API31+ blur). |
| `GlassScaffold.kt` | `GlassScaffold(topBar,bottomBar,fab,...) { padding -> }` = `OfflinePayBackground` + transparent `Scaffold`. `glassTopBarColors()` → translucent `TopAppBarColors`. |
| `GlassMotion.kt` | `gentleSpring()`, `bouncySpring()`, `Modifier.pressScale(interactionSource)`, `rememberPressInteraction()` for iOS tactile press. |
| `OfflinePayTheme.kt` (edited) | Now provides `LocalGlassTokens`; shapes bumped to premium radii (small=20, medium=26, large=32, xl=36, xs=14). |
| `components/PaymentCard.kt` (edited) | Being converted to render as glass (keeps exact public signature). |

### Status of foundation compile
- `GlassSurface.kt` unresolved `clickable` error → **FIXED** and `:core:designsystem:compileDebugKotlin`
  now passes. `PaymentCard.kt` delegates to `GlassCard` (20dp radius) — done.

---

## ✅ DONE — Restyled screens (Task #2)

Every screen was migrated with this **mechanical, safe transformation:**

1. Wrap the screen body in `OfflinePayBackground { ... }` **or** swap `Scaffold(...)` →
   `GlassScaffold(...)` (import from `com.offlinepay.core.designsystem`).
2. If keeping a raw `Scaffold`, set `containerColor = Color.Transparent`.
3. `TopAppBar(colors = glassTopBarColors())` instead of the opaque surface color.
4. Replace `Card` / `ElevatedCard` / `PaymentCard` content surfaces with `GlassCard { }`.
   (Note: `PaymentCard` itself is being upgraded to glass, so its call sites auto-benefit.)
5. Optional polish: `Modifier.pressScale(rememberPressInteraction())` on tappable cards/buttons;
   `bouncySpring()` for success/confirmation reveals.

### Screen inventory (15 files) — all migrated ✅
- [x] `feature/dashboard/.../DashboardScreen.kt` — `GlassScaffold` + `glassTopBarColors()`; `GlassCard` stats/last-payment/offline-status.
- [x] `feature/history/.../HistoryScreen.kt` — `GlassScaffold` + `glassTopBarColors()`.
- [x] `feature/history/.../TransactionReceiptScreen.kt` — `GlassScaffold`; detail surfaces `GlassCard`.
- [x] `feature/merchant/.../MerchantProfileScreen.kt` — `GlassScaffold` + `glassTopBarColors()`.
- [x] `feature/onboarding/.../OnboardingScreen.kt` — root `OfflinePayBackground`.
- [x] `feature/pay123/.../ui/Pay123GuidanceScreen.kt` — `GlassScaffold`.
- [x] `feature/pay123/.../ui/PaymentResultConfirmationScreen.kt` — `OfflinePayBackground`; `GlassCard`.
- [x] `feature/payment/.../PaymentConfirmationScreen.kt` — `OfflinePayBackground`.
- [x] `feature/payment/.../ui/PaymentFailureScreen.kt` — `OfflinePayBackground`; `GlassCard`.
- [x] `feature/payment/.../ui/PaymentSuccessScreen.kt` — `OfflinePayBackground`; `GlassCard`; `bouncySpring()` celebration.
- [x] `feature/scanner/.../ScannerScreen.kt` — full-bleed camera; chrome/error/rationale as `GlassCard` over `OfflinePayBackground`.
- [x] `feature/settings/.../SettingsScreen.kt` — `GlassScaffold` all sub-screens; `GlassCard` rows; `pressScale`.
- [x] `feature/settings/.../SettingsSubScreens.kt` — `GlassScaffold`; `GlassCard`.
- [x] `feature/ussd/.../UssdInProgressScreen.kt` — `GlassScaffold`.
- [x] `app/.../SecurityBlockedScreen.kt` — `OfflinePayBackground`; `GlassCard`.

Note: the security-alert banners (`SecurityBannerCard.kt`) and loading `SkeletonCard.kt` are
intentionally left opaque — coloured alerts must stay legible and shimmers are placeholders,
so they are NOT glassified by design.

Each `feature/*` module already depends on `:core:designsystem` (it exposes compose via `api(...)`),
so the new APIs are importable without gradle changes.

## ✅ DONE — Verify (Task #3)
- `:app:compileDebugKotlin --offline --max-workers=1` → **BUILD SUCCESSFUL** (2026-07-05).
- Optional remaining: launch on device/emulator to eyeball the glass look (build env permitting).

---

## Design token cheat-sheet (for consistency)
- Card radius 26dp; buttons 20dp; sheets 32–36dp.
- Light glass fill = white @58% alpha; Dark = white @8%; borders white @70%/16%.
- Aurora: indigo `#6B5CE7`, amber `#F59E0B`, cyan `#22D3EE` glows over indigo→amber (light) /
  near-black indigo→violet (dark) base gradient.
- Existing brand palette unchanged in `OfflinePayColors.kt` (indigo `#3D2DB5` + amber `#F59E0B`).

## Notes / cleanup
- Temp build scripts (`_ds.bat`, `_ds_compile.bat`, `_ds_compile.log`, `_ds.log`) — **removed**.
- Do NOT commit `local.properties` (has machine paths). Not a git repo currently anyway.
