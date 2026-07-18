# OfflinePay — Bug Audit, Fixes & Handoff

**Last updated:** 2026-07-07 (session 2)
**Branch:** master
**Task:** Find bugs & issues, fix them, produce a final APK.

> **Session 2 update:** Picked up 7 of the 8 "known remaining issues" below and fixed them
> (combined history filters, USSD event delivery, analytics flusher backoff, Integrity I/O
> dispatcher, merchant double-count, lifecycle-aware collection, manifest nits). Final
> `:app:assembleDebug` = **BUILD SUCCESSFUL in 1m 55s**, fresh APKs at 15:29 (all 4 ABIs).
> Only the SIM hot-swap wiring (#2) is left — it needs runtime verification I couldn't do
> headless. Details in **"Session 2 — remaining issues fixed"** and the updated remaining list.

---

## TL;DR / Where I left off

- The **debug build already compiled green** before my changes (the old `hs_err_*` / `full_result.txt`
  errors were stale — ignore them). Baseline `:app:assembleDebug` = BUILD SUCCESSFUL.
- I audited the codebase (2 parallel bug-hunt agents + manual review of the critical
  payment / crypto / DB / DI paths) and **fixed 11 real bugs** (2 CRITICAL, 3 HIGH, rest MED/LOW).
- All fixes compile. Final `:app:assembleDebug` was re-run after the fixes. See
  **"Build status"** at the bottom for the exact state.
- A short list of **known remaining issues** (deliberately NOT fixed — larger/riskier refactors)
  is at the end so you can pick them up next.

---

## How to BUILD (this env is fragile — normal wrappers are broken)

`./gradlew` (bash) is CRLF-corrupted; `gradlew.bat` via MSYS swallows output. Use the JBR java directly:

```bash
cd "D:/Apps/OFFLINE PAYMENT APPPPPPPPP"
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export GRADLE_USER_HOME="D:/GradleHome"
"$JAVA_HOME/bin/java.exe" -Dorg.gradle.appname=gradlew \
  -classpath "gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain \
  :app:assembleDebug --offline --console=plain --max-workers=1 2>&1 | tee build_out.txt | tail -70
```

- **Low-RAM host (3.9 GB):** ALWAYS pass `--max-workers=1` or the Kotlin workers OOM. Serial full
  compile is slow (~5–20 min cold). Daemon may restart mid-build due to `MaxMetaspaceSize=384m`.
- Debug APK is **signed with the debug keystore** and installable.
- Output APKs: `app/build/outputs/apk/debug/` — per-ABI + `app-universal-debug.apk`.
- **Release signing is NOT configured** (commented out in `app/build.gradle.kts` ~line 69), so a
  release build would produce an *unsigned* APK. Debug is the shippable artifact until a keystore
  is wired up.

---

## Bugs found & fixed (all applied)

| # | Sev | File | Bug | Fix |
|---|-----|------|-----|-----|
| 1 | CRITICAL | `feature/history/.../HistoryViewModel.kt` (~L428) | `ListBackedPagingSource.load()` did `useCase(filters).collect{}` on a hot Room `Flow` that never completes → `load()` never returns → **History screen = permanent spinner**. | Use `.first()` (added import `kotlinx.coroutines.flow.first`). |
| 2 | CRITICAL | `core/data/.../repository/SettingsRepositoryImpl.kt` (~L64) | Field initializer `MutableStateFlow(loadInitialSettings())` did 3× `runBlocking { dao.get() }` — as a Hilt `@Singleton` it constructs on the **main thread**, forcing SQLCipher DB open + Keystore unwrap → **ANR / cold-start jank**. | Seed StateFlow with `defaultSettings()`; hydrate async in `init { initScope.launch { … } }` on `ioDispatcher`; made `loadInitialSettings()` `suspend`, removed `runBlocking`. |
| 3 | HIGH | `feature/scanner/.../parser/UpiIntentUriParser.kt` (~L48) | Amount parsed with `Double`: `(am*100).toLong()` truncates (`am=0.29` → **28 paise**, underpay) AND **no validation** (negative / zero / >2-dec / >₹1L all accepted) for intent QRs. | Rewrote with `BigDecimal` + full NPCI validation, mirroring `StandardUpiUriParser`. |
| 4 | HIGH | `feature/ussd/.../UssdResponseParser.kt` (~L84 `classify`) | SUCCESS keywords (`credited`/`debited`) checked **before** error keywords → `"failed - not credited"` / `"insufficient … debited"` classified **SUCCESS** → failed payment shown as success. | Moved BANK_ERROR branch **before** SUCCESS. |
| 5 | HIGH | `feature/ussd/.../UssdViewModel.kt` (~L102) | `capturedTransactionId` set only *after* `initiateUssd()` returns, but callback fires `REQUESTING` **synchronously** inside it → `?: return@initiateUssd` swallowed the initial transition → UI stuck on Idle. | Always `stateMachine.transition(state)` first; only the terminal (COMPLETED/FAILED) branches require the txId (they arrive async). |
| 6 | HIGH | `feature/pay123/.../Pay123ViewModel.kt` | `init{}` → async `autoInitiateCall()` → `requireStateMachine()` = `checkNotNull` can run **before** the composable calls `attachLifecycle()` (SIM-detection race) → **crash on entering 123PAY screen**. | Added `pendingInitiation`; if state machine null, defer and replay it from `attachLifecycle()`. |
| 7 | MED | `feature/payment/.../engine/RoutingEngineImpl.kt` (~L56) | Manual-override branch checked only `voiceServiceAvailable`, not `strategy.canHandle(...)`; fallback chain not capability-filtered (contract says "if set **and capable**"). | Gate override on `strategy.canHandle(simInfo, params)`; filter fallback by `canHandle`. |
| 8 | MED | `feature/payment/.../PaymentConfirmationViewModel.kt` (~L254/L338) | `validateAmountString` allowed >2 decimals; static-QR conversion `.multiply(100).toLong()` **truncated** (`100.999` → ₹100.99). | Reject `value.scale() > 2`; `setScale(2, HALF_UP)` before converting. |
| 9 | MED | `feature/merchant/.../MerchantViewModel.kt` | `loadMerchant` re-emits `Ready(merchant = <captured snapshot>)` on every tx-flow emission; `onToggleFavourite` update gets **clobbered** on next emission → star snaps back. | Added `currentMerchant` field; collector uses latest, toggle updates it. |
| 10 | LOW | `app/.../MainActivity.kt` (~L385) | Deep-link `composable("scan"){ navController.navigate("scanner") }` navigates **during composition** → re-fires on recomposition. | Wrapped in `LaunchedEffect(Unit)` + `popUpTo("scan"){inclusive=true}`. |
| 11 | LOW | `core/data/.../work/TransactionRetentionWorker.kt` | `deleteOlderThan` returns `AppResult` (never throws), so `try/catch → retry()` was dead; failed 90-day purge silently reported success. | Inspect result: `Success → success()`, `else → retry()`. (Note: `when` must be exhaustive — `AppResult` has `Loading`; used `else`.) |

---

## Session 2 — remaining issues fixed (all applied, build green)

| # (orig) | Sev | File | Bug | Fix |
|---|-----|------|-----|-----|
| 1 | MED | `core/data/.../repository/TransactionRepositoryImpl.kt` (`buildFilteredFlow`) | `when` picked ONE DAO query by precedence; only `merchantName` applied in-memory → combined filters (e.g. status+dateRange) silently ignored all but one. | Keep the precedence-chosen query as an **index-optimised base stream**, then re-apply **every** active filter in-memory (status, method, date bounds, amount bounds, merchant) so they AND-combine. Re-filtering the base dimension is idempotent. |
| 3 | MED | `feature/ussd/.../UssdViewModel.kt` | `_events = MutableSharedFlow()` (replay=0) dropped events emitted before the screen subscribed; `OfferFallbackTo123Pay` double-emitted on FAILED (onResponse **and** `updateUiFromState`). | Switched to `Channel(BUFFERED).receiveAsFlow()` (buffered, no drop). Made `updateUiFromState` a **pure mapper** — removed its fallback emit; fallback now emitted once from the explicit terminal paths (onResponse for controller FAILED/TIMEOUT, onTimeout for the timer). |
| 4 | LOW | `core/analytics/.../work/AnalyticsQueueFlusher.kt` | `Result.retry()` between batches → WorkManager exponential backoff; large backlog drained very slowly. | Drain all batches in an internal `while` loop; return `success()` once the queue empties (or a partial batch is seen). `retry()` reserved for genuine failures. |
| 5 | LOW | `core/data/.../repository/IntegrityRepositoryImpl.kt` | suspend methods ran EncryptedSharedPreferences I/O (Keystore decryption) on the caller's dispatcher — blocking if called from Main. | Injected `@IoDispatcher`; wrapped every suspend body in `withContext(ioDispatcher)` (matches sibling repos). |
| 6 | LOW | `core/data/.../repository/MerchantRepositoryImpl.kt` (`upsertMerchant`) | `insertIfNotExists` + unconditional `updateOnPayment` (`transaction_count + 1`) → a brand-new merchant (inserted count=1) was immediately bumped to 2. | Branch on existence: `getByUpiId != null` → `updateOnPayment` (bump); else `insertIfNotExists` (no bump). |
| 7 | LOW | `feature/scanner/.../ScannerScreen.kt`, `feature/onboarding/.../OnboardingScreen.kt` | `collectAsState()` keeps collecting while the screen is stopped. | Swapped to `collectAsStateWithLifecycle()` (both modules already had `lifecycle-runtime-compose`). |
| 8 | LOW | `app/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS` declared but no notification API used anywhere (verified: zero `NotificationManager`/`.notify(` refs) → unjustified runtime prompt on Android 13+. `autoVerify="true"` on the custom `offlinepay://` scheme is a no-op (App Links only apply to http/https). | Removed the `POST_NOTIFICATIONS` permission; removed `autoVerify` from both custom-scheme intent-filters. |

## Known remaining issues (still NOT fixed — pick up next)

2. **MED — SIM hot-swap detection is dead code.**
   `core/telephony/.../SimStateBroadcastReceiver.kt` `register()`/`unregister()` are never called
   from `src/main`. `SIM_STATE_CHANGED` never observed → `simInfoList` never updates (Req 3.1/3.3).
   **Left for next session** — wiring it needs a lifecycle owner (likely `OfflinePayApplication` or a
   process-lifecycle observer) plus runtime verification on a dual-SIM device, which I couldn't do
   headless. When wiring it, also pass `ContextCompat.RECEIVER_NOT_EXPORTED` (targetSdk 34), and check
   who actually consumes `simInfoList` (the receiver is a `@Singleton` that currently nothing injects).

### Also worth a look (not verified as bugs)
- Two **duplicate `QrParser` interfaces** exist: `core/domain/.../usecase/qr/QrParser.kt`
  (returns `AppResult<…>`) appears **orphaned/dead**; the live one is
  `feature/scanner/.../parser/QrParser.kt` (returns `QrParseResult`). Consider deleting the domain one.
- `BharatQrParser.canParse` matches only the literal string `"BharatQR"`; real Bharat QR is EMVCo
  TLV, so it effectively never fires on genuine Bharat QRs (looks like a stub).
- `UssdController` never emits `COMPLETED` (only REQUESTING/ACTIVE/FAILED) → the USSD SUCCESS path
  may never fire from the controller; verify the full success flow is driven from the screen.

---

## Repo hygiene note
The repo root is littered with build artifacts that should be gitignored / removed:
`hs_err_pid*.log`, `replay_pid*.log`, `bl_*.txt`, `build_*.txt`, `full_*.txt`, `hilt_check*.txt`,
`asm_*.txt`, `*.ps1`, `*.bat`, `data_*.txt`, `domain_*.txt`, `task*_*.txt`, `dep_tree.txt`,
`kickbacks.vsix`. These are the fragile-env crash dumps, not source. My own build logs:
`build_current.txt`, `build_fixes.txt`, `build_fixes2.txt`.

---

## Build status

### Session 1 (the original 11 fixes)
- Baseline (pre-fix) `:app:assembleDebug`: **SUCCESSFUL** (produced 4 APKs).
- Post-fix build #1: FAILED — one compile error I introduced (`when` on `AppResult` not exhaustive
  in `TransactionRetentionWorker`). **Fixed** (used `else`).
- Post-fix build #2: re-run of `:app:assembleDebug` — confirmed green in session 2
  (`build_confirm.txt`: **BUILD SUCCESSFUL in 4m 22s**, APKs @ 15:03).

### Session 2 (the 7 remaining-issue fixes)
- Code-fix build (`build_fixes3.txt`): **BUILD SUCCESSFUL in 10m 11s** — validated the 6 code fixes.
- Final build incl. manifest nits (`build_fixes4.txt`): **BUILD SUCCESSFUL in 1m 55s**.
- **Final shippable artifacts:** `app/build/outputs/apk/debug/*.apk` @ **15:29** — all 4 ABIs
  (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `app-universal-debug.apk`), debug-signed & installable.

**Net: 18 bugs fixed across two sessions (11 + 7); only issue #2 (SIM hot-swap wiring) remains.**
Release signing is still unconfigured (`app/build.gradle.kts` ~L69), so debug remains the
shippable artifact until a keystore is wired.
