# USSD Auto-Drive Feature — Progress & Handoff

**Last updated:** 2026-07-07
**Branch:** master
**Goal:** Make offline UPI payment near one-tap. After scan → amount → Confirm, the app dials
`*99#` and an **AccessibilityService auto-fills the menus** (Send Money → UPI ID → payee → amount),
stopping only at the **UPI PIN** for the user. PIN is never automated.

Full approved plan: `C:\Users\dell\.claude\plans\greedy-growing-rabin.md`

---

## Why this exists (root cause the user hit)
- Confirm Payment used to dial an unrelated number (`9311382882`) — already fixed earlier
  (`Pay123Controller.kt` now dials NPCI `08045163666`). That fix is in the latest APK.
- Deeper problem: BOTH offline rails were fully manual. USSD used `sendUssdRequest("*99#")` which
  **cannot reply to interactive menus**, so it could never complete a payment; the scanned
  payee/amount were never injected. This feature replaces that with an interactive `ACTION_CALL`
  to `*99#` + an AccessibilityService that drives the menus.
- Hard constraints (confirmed vs NPCI + Android docs): no single-string `*99#` send code; UPI PIN
  is a mandatory secure prompt that can never be automated; `sendUssdRequest` can't answer menus.
  AccessibilityService auto-fill (Hover-style) is the only near-one-tap path.

---

## UPDATE 2026-07-07 (later): On-device *99# test on JIO = CONFIRMED UNSUPPORTED.
- Forced USSD via a temporary `_memoryOverride = USSD` patch in `RoutingEngineImpl.kt`, rebuilt, ran on Jio.
- Auto-drive WORKED (dialed *99# via ACTION_CALL, accessibility service read the dialog), but Jio network
  returned `[BANK_ERROR] Reliance Jio: Connection problem or invalid MMI code.` — *99# never reached NPCI.
- Conclusion: Jio 4G-only network rejects *99# at the network layer; not an app bug. Default routing
  (Jio → 123PAY, others → *99#) is correct. **Test patch REVERTED** (`_memoryOverride = null` again).

## UPDATE 2026-07-07 (bug fixes): routing normal; two dead-button bugs fixed. BUILD SUCCESSFUL (20:52), installed.
- **Transaction history download** was broken: `HistoryScreen.kt` `ShareExport` handler was a stub — CSV was
  written + URI emitted but no share Intent fired. FIXED: fires `ACTION_SEND` chooser with the FileProvider
  URI; `ShowError` now shows a Toast. (FileProvider + `file_provider_paths.xml` `exports/` already correct.)
- **Settings dead buttons**: Theme (`onClick = {}`) and Payment Method Override (`onClick = {}`) did nothing.
  FIXED: Theme → navigates to `settings/theme` (the orphaned `SettingsThemeScreen`, now route-registered in
  MainActivity + imported); Override → navigates to `settings/routing` (already has the override radios).
  All other Settings items (Language, Routing, SIM, Permissions, Security, About) were already wired correctly.

## STATUS: code complete + BUILD SUCCESSFUL (2026-07-07 19:28). On-device verify pending (phone was disconnected).

- `:feature:ussd` + `:core:security` compiled clean (only a harmless `recycle()` deprecation warning).
- `:app:assembleDebug` = **BUILD SUCCESSFUL**; fresh APKs at 19:28 in `app/build/outputs/apk/debug/`.
- Verified the compiled dex of `app-arm64-v8a-debug.apk` contains `UssdAccessibilityService`.
- NOT yet installed/run: device `ZD222H9B9L` was unplugged. Reconnect, then do steps 2–6 below.

### Done (all in `feature/ussd/`, package `com.offlinepay.feature.ussd`)
NEW files under `.../feature/ussd/.../autodrive/`:
- **`UssdMenuScript.kt`** — `StepResponse` (Digit/PayeeUpiId/AmountRupees/Remark/StopForPin),
  `UssdStep(label, matchers, response)`, `UssdMenuScript`, and `DefaultNpciScript` (keyword-driven
  send-to-UPI-ID flow). Matching is keyword-based, forward-only cursor.
- **`UssdAutoDriveSession.kt`** (`@Singleton`) — shared bridge. `arm(payeeUpiId, amountRupees,
  remark, script)` / `disarm()`; `onMenu(text): String?` returns what to type (forward cursor,
  debounced by last-handled text); `markAwaitingPin/markCompleted/markFailed`. Publishes
  `progress: StateFlow<UssdDriveProgress>` (Idle/Navigating/AwaitingPin/Completed/Failed).
  Mirrors `SimStateBroadcastReceiver` shape. NEVER holds a PIN.
- **`UssdAccessibilityService.kt`** (`@AndroidEntryPoint : AccessibilityService`) — injects
  `UssdAutoDriveSession` + `UssdResponseParser`. Reads USSD dialog nodes (message text + EditText +
  positive button), classifies via parser: PIN_PROMPT → mark AwaitingPin (do nothing to field);
  SUCCESS/BANK_ERROR → mark terminal; else → `session.onMenu()` → `ACTION_SET_TEXT` + click Send.
  Ignores our own app package; only acts while `session.isArmed`. Debug logs are PII-sanitised.
- **`UssdAccessibilitySettings.kt`** — `isServiceEnabled(context)` (reads
  `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` + `ACCESSIBILITY_ENABLED`), `openAccessibilitySettings()`.
- **`res/xml/ussd_accessibility_config.xml`** + **`res/values/strings.xml`** (label/summary/description).

MODIFIED files:
- **`UssdController.kt`** — rewritten. `initiateUssd(params, subscriptionId): String` now: saves
  PENDING record, `paiseToRupees()`, `autoDriveSession.arm(...)`, then dials `*99#` via
  `ACTION_CALL` (`Uri.fromParts("tel","*99#",null)` → `tel:*99%23`) on the chosen SIM using
  `TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE` (resolved from subscriptionId; falls back to default
  SIM). SecurityException/ActivityNotFound → `ACTION_DIAL` fallback. Uses `context.checkSelfPermission`
  (not ContextCompat, to avoid a transitive-dep risk). Old `sendUssdRequest`/`menuStack`/`clearStack`
  and `onResponse` callback REMOVED.
- **`UssdViewModel.kt`** — rewritten. Injects `@ApplicationContext context`, `UssdController`,
  `UssdAutoDriveSession`, `UpdateTransactionStatusUseCase`, `UpsertMerchantUseCase`, `SavedStateHandle`.
  `init` → `startSession()` (calls controller) + `observeProgress()` (maps
  `autoDriveSession.progress` → `UiState`). Added `UiState.AwaitingPin`. `isAutoPayEnabled: Boolean`
  exposed for the screen gate. `openAutoPaySettings()`. 45s inactivity countdown → OfferFallbackTo123Pay.
  On Completed: update status SUCCESS + upsertMerchant + NavigateToSuccess. On Failed/Timeout:
  update FAILURE + OfferFallbackTo123Pay. `cancel()`/`onCleared()` disarm the session.
- **`UssdInProgressScreen.kt`** — added `AwaitingPin` branch (exhaustive `when`), a prominent
  "Enter your UPI PIN" callout, and an "Enable Auto-Pay" button shown when `!isAutoPayEnabled`.
- **`feature/ussd/src/main/AndroidManifest.xml`** — added `<application>` with the `<service>`
  (BIND_ACCESSIBILITY_SERVICE, intent-filter, meta-data → accessibility config).
- **`core/security/.../AccessibilityAbuseDetector.kt`** — whitelisted `context.packageName` so the
  app's own service is not flagged as suspicious.

---

## TODO / where to resume
1. **BUILD** (fragile host — use JBR java directly, `--max-workers=1`):
   ```bash
   cd "D:/Apps/OFFLINE PAYMENT APPPPPPPPP"
   export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
   export GRADLE_USER_HOME="D:/GradleHome"
   "$JAVA_HOME/bin/java.exe" -Dorg.gradle.appname=gradlew \
     -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain \
     :feature:ussd:compileDebugKotlin --offline --console=plain --max-workers=1 2>&1 | tail -60
   ```
   then `:app:assembleDebug`. Fix any compile errors (watch: unused `UssdSessionStateMachine` is now
   orphaned but harmless; the OLD unit tests in `feature/ussd/src/test/` reference the removed
   `initiateUssd(ussdCode=, onResponse=)` API and `viewModel.controller`/`sessionStateMachine` — they
   will NOT compile but `assembleDebug` skips tests. Update or delete those tests before running unit tests).
2. **INSTALL** on device `ZD222H9B9L` (arm64): `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
3. **ENABLE** the service: Settings → Accessibility → "OfflinePay Auto-Pay (*99#)" → On. (App shows an
   "Enable Auto-Pay" button on the USSD screen if it's off.)
4. **TEST end-to-end** (only real test — automation is bank/operator-specific): scan a real UPI QR →
   amount → Confirm → watch menus auto-advance → **pause at UPI PIN** → type PIN → success.
5. **TUNE** `DefaultNpciScript` matchers in `UssdMenuScript.kt` against the actual menu wording on the
   user's bank/operator (read `adb logcat -s UssdAutoDrive` — logs are PII-sanitised).
6. Confirm the app's security notice does NOT flag its own service.

### Known risks / notes
- Some OEMs restrict `ACTION_SET_TEXT` injection into the system USSD dialog — if `set` fails the
  service logs a warning and leaves it to the user (graceful). This is the main device-dependent risk.
- Routing still decides USSD vs 123PAY in `PaymentConfirmationViewModel` (unchanged). JIO defaults to
  123PAY. To force USSD for testing, use the manual override in Settings if available, or test on
  Airtel/VI/BSNL.
- Dual-SIM PhoneAccountHandle matching is best-effort (id == subId, else slot index); falls back to
  default SIM.
- Play Store: automating banking menus via AccessibilityService may violate policy — fine for
  sideload/personal use; revisit before any Play release.
