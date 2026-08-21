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

## How to add a new APK to this log

```bash
export ADB_SERIAL=<device-serial>   # only needed with >1 device attached
scripts/test-apk.sh path/to/app.apk
```

No rebuild or manifest edit needed for a single-activity target - the script
pushes the APK to the device and launches it directly through `DCLActivity`.
Record the result as a new row above.
