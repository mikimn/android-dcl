# APK test log

A running record of real APKs run through the DCL loader, in increasing
complexity, using [`scripts/test-apk.sh`](../scripts/test-apk.sh). See
[CLAUDE.md](../CLAUDE.md) for the architecture these results are exercising.

## Tiers

| Tier | Definition |
| --- | --- |
| 0 | Single activity, no custom resources/assets beyond basics |
| 1 | Single activity, custom resources/assets and/or a custom `Application` class |
| 2 | Multiple activities, in-app navigation, no third-party SDKs |
| 3 | Bundles a third-party SDK (ads, analytics, Play Services, etc.) |
| 4 | Real, unmodified app pulled from an installed device (`adb shell pm path`) |

## Results

| APK | Source | Tier | Result | Date | Notes |
| --- | --- | --- | --- | --- | --- |
| `simple.apk` | bundled asset | 1 | PASS | 2026-08-19 | Renders and retains click-count state correctly. |
| `calculator.apk` | bundled asset | 1 | PASS | 2026-08-19 | Fixed by the `LoadedApk.load()` null-parent bug below; renders and computes correctly. |
| `flappy-bird-1-3.apk` | bundled asset | 3 | PASS | 2026-08-21 | Was FAIL as of 2026-08-19 (see "Fixes" below for the full root-cause chain: three separate bugs, all now fixed). Splash → GameActivity navigation, the actual menu screen, sprites and background now render correctly and stay stable (screenshot-verified, no flicker, process alive after a settle period). |
| Clock (`com.oneplus.deskclock`) | `pm path` (`/product/app/Clock/Clock.apk`) | 4 | FAIL | 2026-08-21 | Crashes during provider init: a static initializer in one of its `ContentProvider`s (`SPContentProvider`) calls `Context.getPackageName()` on a null `Context`, before `attachInfo` even runs. Not fixed - the provider-init hardening below only catches exceptions from construction/`attachInfo` itself, not from a bad assumption baked into the provider's own static init. |
| Breath Mode (`com.oneplus.brickmode`) | `pm path` | 4 | PARTIAL PASS | 2026-08-21 | **First real confirmation of generic multi-activity navigation**: the app's own onboarding flow called `startActivity` targeting `com.oneplus.brickmode.guide.GuideActivityNew` - a class never declared anywhere in our manifest - and `ActivityTaskManagerHook` retargeted it to `DCLActivityProxy0` automatically. Confirmed at the WindowManager/AMS level (not just app logs): the task genuinely reached `numActivities=2` with `topActivity=DCLActivityProxy0`, and both windows (`DCLActivity` and `.../GuideActivityNew`) got real `Displayed`/draw events. The process died shortly after with no Java or native crash signal in logcat - looks like an ordinary empty-task reclaim (plausibly `GuideActivityNew` calling `finish()` once it decides onboarding is already done, an ordinary pattern) rather than a bug in the new mechanism, but not conclusively ruled out. Also hit (and fixed, see below) a non-exported-provider crash from `SearchProvider`. |
| Meme Generator (`com.zombodroid.MemeGenerator`) | `pm path` (split APK, launched from install dir) | 4 | PASS | 2026-08-21 | Was FAIL as of first testing this app (split APKs, R8-minified release, a heavy modern SDK stack: Firebase, Google Mobile Ads, Facebook Audience Network, Vungle, MLKit, Play Billing). The `FirebaseCrashlytics component is not present` crash - the real blocker - is now root-caused and fixed (see "Firebase Crashlytics: actually solved" below). Now reaches the app's own native UI (a self-tamper/installer-verification "Security error" dialog - a real, separate, expected-in-this-context check, not a bug). Screenshot-verified reproducible on a clean `pm clear` state. |

## Fixes made while establishing this baseline

- [`LoadedApk.kt`](../app/src/main/java/com/mikimn/apkloader/apk/LoadedApk.kt): `load()` threw
  `IllegalStateException: Bad APK at path <name>` for any bundled-asset name (e.g.
  `"calculator.apk"`), because `File(name).parentFile` is `null` for a bare filename. This is
  now tolerated (native-lib-dir and split-APK discovery are simply skipped when there's no
  parent directory) rather than fatal.
- [`LoadedApk.kt`](../app/src/main/java/com/mikimn/apkloader/apk/LoadedApk.kt): the per-APK
  extraction cache dir (`cache-<name>`) was never cleared before re-extracting, so re-running the
  same `name` with different content (e.g. rebuilding a sample app during development) silently
  accumulated stale files from every previous version. Now deleted and recreated on each load.
- [`DefaultPluginProvider.kt`](../app/src/main/java/com/mikimn/apkloader/plugins/DefaultPluginProvider.kt):
  re-enabled the previously-commented-out `PlayServicesBlockingPackageManager`, which makes
  `getPackageInfo("com.google.android.gms")` throw `NameNotFoundException` so apps using the
  standard `GoogleApiAvailability` check correctly see Play Services as "not installed" rather
  than attempting to talk to it. Doesn't fix the `flappy-bird-1-3.apk` failure above (different
  code path), but is correct behavior for any app that does check properly, and was clearly
  built for this purpose (its `ACTIVITY_WHITELIST` targets `com.google.ads.AdActivity`).
- [`DCLActivity.kt`](../app/src/main/java/com/mikimn/apkloader/dcl/DCLActivity.kt): provider
  initialization is now best-effort (wrapped per-provider in try/catch, matching the existing
  `MlKitInitProvider` special-case) instead of letting one broken provider crash the whole
  activity load. Found via two different real apps failing here for two different reasons (see
  the Clock and Breath Mode rows above).

## Generic multi-activity navigation

Solved this round. Every activity a loaded app navigates to via its own
explicit-component `startActivity()` calls previously had to be pre-declared
in the host `AndroidManifest.xml`, because that resolution happens at the
OS/ActivityManagerService level before `DCLAppComponentFactory` ever runs -
the factory can only redirect a component the OS already agreed to launch.

The fix, confirmed working against a real app (Breath Mode, above):
- [`ActivityTaskManagerHook.kt`](../app/src/main/java/com/mikimn/apkloader/dcl/ActivityTaskManagerHook.kt)
  replaces the process's cached `IActivityTaskManager` binder client with a
  `java.lang.reflect.Proxy` (found reflectively via `Class.forName` - neither
  `ActivityTaskManager`, `IActivityTaskManager`, nor `android.util.Singleton`
  are in the `compileSdk` stub jars, and neither is
  `Instrumentation.execStartActivity`, the interception point every other
  DCL-style framework uses directly). Every outgoing `startActivity`-family
  call is inspected; if its `Intent` targets a class belonging to a loaded
  APK (`FileTrackingClassLoader.ownerOf`), the component is rewritten to a
  free slot in `DCLActivityProxyPool` and the real class/APK name are stashed
  as intent extras - the same trick `DCLActivity.forActivityClass` already
  did manually via a hardcoded whitelist, just automatic now.
- [`DCLActivityProxyPool.kt`](../app/src/main/java/com/mikimn/apkloader/dcl/DCLActivityProxyPool.kt)
  + 8 new `<activity android:launchMode="standard">` entries in
  `AndroidManifest.xml` give each in-app navigation target its own
  back-stack entry (unlike `DCLActivity`'s own `singleTask` entry, kept
  untouched for the existing `intentForAPK` entry-point flow). No new
  Kotlin classes needed per slot - `DCLAppComponentFactory` already
  redirects any activity class name it doesn't recognize to a real
  `DCLActivity` instance.
- `DCLActivity.onCreate` resolves the already-loaded APK by name when the
  `loadedApkName` extra is present, instead of re-extracting or falling back
  to "most recently loaded".

Pool is fixed at 8 slots, round-robin allocated (no reuse-on-finish
tracking yet) - fine for shallow navigation depth, would need real
slot lifecycle management for deep/long-lived back stacks.

## flappy-bird-1-3.apk: FAIL → PASS (three separate bugs)

Root-caused by decompiling the APK with `apktool` rather than guessing from
the obfuscated stack traces - each fix is small and targeted once the actual
cause was known:

1. **The Play Games Services crash.** `GameActivity` (via AndEngine's
   `SimpleBaseGameActivity` → `BaseGameActivity`) owns its own bundled
   `com.google.example.games.basegameutils.a` (Google's public 2014-era
   `GameHelper` sample class) and calls its `onStart()` unconditionally, which
   auto-connects to Play Games unless `setConnectOnStart(false)` was called -
   which this app's code never does. A loaded-but-not-installed app can't
   legitimately authenticate as `com.dotgears.flappybird`'s registered
   identity, so Play Services always rejects it with `IllegalStateException:
   A fatal developer error has occurred`, thrown asynchronously on the main
   thread. Fixed in `DCLActivity.disableAutoGameSignIn()`: after
   `callActivityOnCreate`, it structurally finds the `GameHelper` instance
   (searching the activity's own class hierarchy's declared fields for one
   typed `com.google.example.games.basegameutils.a`, since different apps'
   activities own the field under different obfuscated names) and flips its
   `l` field (the obfuscated `mConnectOnStart`) to `false`. Necessarily a
   one-off, app-specific patch (matching the existing `MlKitInitProvider`
   special-case) - the field name is specific to this exact compiled build.
2. **The Ads SDK warning banner.** `com.google.ads.AdActivity` was already
   present in `AndroidManifest.xml` but commented out. Just needed
   uncommenting.
3. **The real root cause of the black-screen/flicker**:
   [`DCLContext.getResources()`](../app/src/main/java/com/mikimn/apkloader/dcl/DCLContext.kt)
   built a **brand-new** anonymous `Resources` wrapper on every single call,
   with no caching - breaking the normal `Context` contract that
   `getResources()` returns a stable identity. Any `ResourcesLoader` attached
   elsewhere (`DCLActivity.initResourceLoader`'s
   `baseContext.resources.addLoaders(...)`) was silently discarded the moment
   anything else called `.resources` again, which native framework code does
   constantly. Symptom: `Resources$NotFoundException: Resource ID
   #0x7f040000` (`R.raw.atlas`, the game's sprite atlas, per `apktool`'s
   decoded `public.xml`) from AndEngine's GL thread - `onCreateScene` caught
   the exception internally (no crash), but with no atlas the GL thread kept
   rendering an incompletely-initialized scene every frame, which is what
   surfaced as violent screen flicker rather than a clean black screen. Fixed
   by caching the wrapped `Resources` instance instead of rebuilding it per
   call.

## Meme Generator: research notes on the unsolved Firebase Crashlytics failure

Picked as the next complexity candidate because it's a real, unmodified Play
Store app (not an OEM system utility) with no login wall blocking the first
screen. It immediately surfaced genuinely new territory: split APKs and an
R8-minified release build, neither exercised by any prior test.

### Fixed: `LoadedApk`'s cache directory path construction

`LoadedApk.load()` built the extraction cache directory as
`File(parent, "cache-$name")`. For bundled asset names like `"calculator.apk"`
this is a fine flat directory name, but `name` is a full device path for any
APK loaded via `apkAssetFileName` pointing at an install location (e.g.
`/data/app/~~.../base.apk`) - confirmed on-device that this silently produced
a shared top-level `cache-` directory containing a nested `data/app/~~.../...`
tree, with *every* device-path-loaded APK's cache nested inside it, instead
of each getting its own flat directory. Fixed by sanitizing embedded path
separators before using `name` as a directory name component. Verified fixed
on-device (flat `cache-_data_app_~~..._base.apk` directory) and
regression-tested against `calculator.apk`/`simple.apk`/`flappy-bird-1-3.apk`.

### `FirebaseCrashlytics.getInstance()` throws "component is not present" (solved - see below for the actual fix)

Several plausible causes checked and ruled out first, before the real one
was found:

- **Not a crash during provider init** - all 7 of the app's providers
  (`FileProvider`, `MlKitInitProvider`, `MobileAdsInitProvider`,
  `AudienceNetworkContentProvider`, `FirebaseInitProvider`,
  `androidx.startup.InitializationProvider`, `VungleProvider`) are correctly
  parsed from the real manifest and `attachInfo()` on all of them completes
  without throwing.
- **Not a resource-resolution failure** - directly verified
  `baseContext.resources.getIdentifier("google_app_id", "string", pkg)`
  resolves to the correct value (`1:94759511251:android:ac6fe252403aa32d`,
  matching the app's own `res/values/strings.xml`). `FirebaseOptions` should
  build successfully from this.
- **Not the `ComponentDiscoveryService` manifest-metadata quickfix missing a
  match** - `DefaultPackageManagerPlugin`'s hardcoded Firebase registrar list
  (already present, already includes `CrashlyticsRegistrar`) never even gets
  queried: no `getServiceInfo` call for the real
  `com.google.firebase.components.ComponentDiscoveryService` name was
  observed at all during the crash. The one `getServiceInfo` call that *does*
  happen (for a class named `i.C`) is an R8-obfuscated, app-owned class
  queried from `SplashActivity`'s own `attachBaseContext()` - confirmed via
  a captured call stack - unrelated to Firebase's discovery mechanism.
- **Tried and reverted: enabling `DCLContext.getPackageName()`'s
  already-written-but-commented-out override** (`return shadowPackageName ?:
  super.getPackageName()`). This is a real, important finding even though it
  didn't fix Crashlytics: enabling it caused a *new*, different crash in
  `flappy-bird-1-3.apk`'s Ads `WebView` (`WebViewFactory.getProvider()`
  failing during `AbsoluteLayout`/`View` construction) - a confirmed
  regression, reverted. This explains *why* that override was left disabled
  in the first place rather than being an oversight: `getPackageName()` is
  relied on by real OS-level subsystems (WebView's own data-directory/
  provider resolution among them) that only know about the host's *real*,
  actually-installed identity (`com.mikimn.apkloader`) - spoofing it
  globally breaks those, even though it might help identity-sensitive SDK
  init checks elsewhere (plausibly including Firebase's own multi-process
  guard, which was the working theory that prompted the test).

### Built and verified this round: caller-aware `getPackageName()` - and it's still not the cause

Implemented the "caller-aware identity spoofing" idea from above:
[`CallerClassResolver.kt`](../app/src/main/java/com/mikimn/apkloader/dcl/CallerClassResolver.kt)
identifies the real caller of `getPackageName()` and
[`DCLContext.getPackageName()`](../app/src/main/java/com/mikimn/apkloader/dcl/DCLContext.kt)
now returns the shadow package name only when that caller is code belonging
to the loaded APK itself, falling back to the real host identity for
everyone else (framework internals included).

- `SecurityManager.getClassContext()` (the classic cheap "get the call stack
  as `Class[]`, no string re-resolution needed" trick, confirmed present in
  the `compileSdk` stub via `javap`) was the first design and **did not
  work**: confirmed on-device that it returns `null` unconditionally on this
  ART build, for every single call, not just edge cases - a real gap between
  "present in the SDK stub" and "actually functional at runtime" that static
  analysis alone couldn't have caught. Fell back to the universally-reliable
  `Thread.currentThread().stackTrace` (class names as strings) instead.
- That surfaced two more real bugs, both fixed: `dalvik.system.VMStack` is a
  native stack-capture implementation artifact that Android's (unlike
  desktop JVM's) `stackTrace` includes as a real frame, polluting "who's the
  real caller" - now explicitly skipped. And the existing
  `FileTrackingClassLoader.ownerOf()` (reused here, already built for the
  multi-activity work) had a latent false-positive: `ClassLoader.loadClass()`
  delegates to the parent first, so it "resolves" platform/JDK classes via
  the shared boot classloader too - fixed by additionally checking that the
  resolved `Class.classLoader` really is that specific APK's own loader, not
  just reachable through it.
- **Verified independently correct before testing the actual hypothesis**:
  relaunching `flappy-bird-1-3.apk` showed every `getPackageName()` caller
  correctly classified as framework code (`ContextWrapper`, `ComponentName`)
  → real identity, still renders and stays stable, no regression. Relaunching
  Meme Generator showed its own obfuscated code (class `e3.Z0`) correctly
  classified as loaded-APK code → shadow identity.
- **The hypothesis test itself: negative.** With the mechanism confirmed
  working correctly, Meme Generator still crashes with the exact same
  `NullPointerException: FirebaseCrashlytics component is not present`. This
  cleanly rules out the "Firebase's own multi-process/identity guard" theory
  - not a wasted result, but a real one: two plausible causes (resource
  resolution, package identity) are now eliminated with evidence, not just
  assumption.

The caller-aware `getPackageName()` mechanism is kept regardless of the
negative result - it's a strict improvement over both the old
always-host-identity behavior and the reverted always-shadow-identity
attempt, and may matter for other apps/SDKs even though it didn't explain
this one.

### Firebase Crashlytics: actually solved

With the cheap hypotheses exhausted, went to the source instead of more
inference: decompiled `M4/g.smali` (found by grepping the decompiled dex for
Firebase's own surviving log-message string literals, e.g. `"FirebaseApp was
deleted"` - R8 safely renames a class like `FirebaseApp` when it's only ever
called directly, since every caller gets renamed consistently together, but
string *literals* inside its methods survive renaming untouched, so they're
a reliable way to find a renamed class by content instead of by name). This
confirmed `M4.g` **is** the renamed `FirebaseApp`, and tracing the exact
crash site in `SplashActivity.smali` (matched to the crash trace's line
number, R8 had fully inlined the failing code into `onCreate` itself) showed
precisely:

```smali
invoke-static {}, LM4/g;->d()LM4/g;              ; FirebaseApp.getInstance() - succeeds
const-class v1, Lb5/c;                           ; FirebaseCrashlytics.class (renamed)
invoke-virtual {v0, v1}, LM4/g;->b(Ljava/lang/Class;)Ljava/lang/Object;  ; app.get(Class) - returns null
if-eqz v0, :cond_2                               ; null -> throw the NPE we've been chasing
```

So `FirebaseApp` itself was never the problem - it initializes fine. The
component lookup on it returns null because the component was never
*registered*. Reading `M4.g`'s constructor and the discovery class it calls
(`U/l0`, method `e()`) confirmed this is exactly the mechanism assumed from
the start: `PackageManager.getServiceInfo(ComponentName(context,
ComponentDiscoveryService.class), GET_META_DATA)`, then read
`serviceInfo.metaData` - if null, log `"Could not retrieve metadata,
returning empty list of registrars."` and return nothing.

That call **does** reach `ManifestAwarePlugin.getServiceInfo()` and succeed
(it's tried first, before `DefaultPackageManagerPlugin`'s hardcoded
quickfix, and finds `com.google.firebase.components.ComponentDiscoveryService`
by its real, unobfuscated name - manifest-declared components are protected
from R8 renaming, unlike `FirebaseApp`) - but
[`AndroidManifestReader.getServices()`](../app/src/main/java/com/mikimn/apkloader/apk/AndroidManifestReader.kt)
only ever populated `name` and `exported` on the returned `ServiceInfo`,
never `metaData`. The returned object was real but empty-handed, which reads
identically to "not found" as far as Firebase's own null check is concerned.

The fix: `getApplicationInfo()` already had a complete, working `<meta-data>`
parser (handles string/int/float/bool values and `@id/` resource
references) - extracted into a shared `parseMetaData()` helper and reused in
`getServices()`. One more real bug surfaced immediately: the extracted
resource-type `when` never handled `"raw"` (a `<meta-data
android:resource="@raw/...">` entry on one of `calculator.apk`'s own
services, never exercised before since `getServices()` never called this
parser at all) - now handled the same way `drawable`/`style`/`array` already
were (returns the raw resource ID as a placeholder int).

**Verified end-to-end**: Meme Generator now gets past `FirebaseCrashlytics`
entirely and reaches its own native UI - a "Security error" dialog, the
app's own installer/tamper-verification check correctly (and accurately)
detecting it wasn't installed via Google Play. That's a genuine, separate,
expected-in-this-context finding, not a bug. Reproduced on a clean `pm
clear` state. Full regression pass
(`calculator.apk`/`simple.apk`/`flappy-bird-1-3.apk`) still passes.

One sharp edge found and worked around during verification, not a bug in
the fix itself: Meme Generator's now-fully-functional `com.google.
android.datatransport` job-scheduling infrastructure scheduled a real,
OS-level persistent job tied to the host's actual installed identity
(`com.mikimn.apkloader`), which then fired independently during a later,
unrelated `calculator.apk` test run and crashed it
(`ClassNotFoundException: ...JobInfoSchedulerService`, not present in
calculator.apk's own dex). `adb shell pm clear com.mikimn.apkloader`
between testing *different* apps clears this. Worth keeping in mind for
future rounds: unblocking real SDK functionality can have side effects that
outlive the specific test session, since the host's process identity is
real and shared across everything loaded into it.

`DefaultPackageManagerPlugin`'s hardcoded Firebase registrar list is now
redundant for any app whose real manifest is readable this way (which is
all of them) - left in place as a defensive fallback rather than removed,
since it's harmless and never reached on the success path.

## How to add a new APK to this log

```bash
export ADB_SERIAL=<device-serial>   # only needed with >1 device attached
scripts/test-apk.sh path/to/app.apk
```

No rebuild or manifest edit needed for a single-activity target - the script
pushes the APK to the device and launches it directly through `DCLActivity`.
Record the result as a new row above.
