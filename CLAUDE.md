# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

ApkLoader (`com.mikimn.apkloader`) is a proof-of-concept Android app that dynamically loads and
runs *other* APKs' Activities in-process **without installing them** — no `pm install`, no
`PackageManager` registration. It works by hooking Android's own component-instantiation,
activity-launch and resource-resolution internals via reflection, in the spirit of plugin
frameworks like VirtualApp or Shadow.

Sample target APKs used for manual testing live in `app/src/main/assets/` (`calculator.apk`,
`flappy-bird-1-3.apk`, `simple.apk`). The main screen also lists every installed non-system app
and can load it straight from its install directory (`ApplicationInfo.publicSourceDir`).

## Build / test / run

```bash
./gradlew assembleDebug        # build debug APK
./gradlew installDebug         # build + install on connected device/emulator
./gradlew test                 # JVM unit tests
./gradlew testDebugUnitTest --tests "com.mikimn.apkloader.ExampleUnitTest"   # single unit test
./gradlew connectedAndroidTest # instrumented tests (needs a device/emulator)
./gradlew lint
scripts/test-apk.sh path/to/app.apk [activityClassName]   # push + launch a real APK via DCLActivity
```

There is no CI and no CLI-runnable emulator config baked into the repo — instrumented tests and
manual verification require a connected device/emulator at API level ≥ 30 (see `minSdk` below).
Test layout, process-isolation rules and the shared helpers are in [`docs/TESTING.md`](docs/TESTING.md).
Real-APK test results, and the root-cause write-ups behind most fixes, are recorded in
[`docs/apk-test-log.md`](docs/apk-test-log.md) — add a row there when testing a new APK.

## Architecture

### The hook chain: how an external APK's Activity ends up running

1. `AndroidManifest.xml` declares `android:appComponentFactory=".dcl.DCLAppComponentFactory"`,
   so the framework routes *every* component instantiation for this process through it.
2. [`DCLAppComponentFactory`](app/src/main/java/com/mikimn/apkloader/dcl/DCLAppComponentFactory.kt)
   (`CoreComponentFactory` subclass) installs a `FileTrackingClassLoader` at
   `instantiateClassLoader`, and in `instantiateActivity` redirects any activity class name it
   doesn't recognize (including `DCLActivityProxyN` slots) to a real `DCLActivity` instance.
3. [`DCLApplication`](app/src/main/java/com/mikimn/apkloader/dcl/DCLApplication.kt) (the host
   `Application`) installs [`ActivityTaskManagerHook`](app/src/main/java/com/mikimn/apkloader/dcl/ActivityTaskManagerHook.kt)
   at startup: it swaps the process's cached `IActivityTaskManager` binder client for a
   `java.lang.reflect.Proxy`. Every outgoing `startActivity*` intent whose component belongs to a
   loaded APK (`FileTrackingClassLoader.ownerOf`) is rewritten to the next
   [`DCLActivityProxyPool`](app/src/main/java/com/mikimn/apkloader/dcl/DCLActivityProxyPool.kt)
   slot (8 manifest-declared `standard` activities, round-robin), with the real class/APK name
   stashed as extras. **This is how a loaded app's own in-app navigation works** — target
   activities don't need host manifest entries.
4. [`DCLActivity`](app/src/main/java/com/mikimn/apkloader/dcl/DCLActivity.kt) is the real host.
   `onCreate` either reads the target APK (`KEY_APK_ASSET_FILE_NAME`: an asset name or an
   absolute device path) and loads it via `FileTrackingClassLoader.addApkFile` → `LoadedApk.load()`
   and registers a `ManifestAwarePlugin`, or reuses an already-loaded APK (`KEY_LOADED_APK_NAME`,
   set by the ATM hook). Both extras hold a `LoadedApk.name`, so an activity restored into a fresh
   process after process death simply reloads its APK by name. It then resolves the target
   activity (explicit extra, else the manifest launcher — including `<activity-alias>`
   launchers), instantiates the APK's content providers,
   creates or reuses the **one** shadow `Application` per `LoadedApk`, and uses `ShadowActivity`
   to reflectively call the hidden `Activity.attach(...)` + `Instrumentation.callActivityOnCreate`
   on the real external Activity instance, sharing the host's window and token. Lifecycle callbacks
   on the host are forwarded to the shadow instance — and, because the shadow shares the host's
   token, so are `onActivityResult`/`onRequestPermissionsResult`/`onNewIntent`/
   `onConfigurationChanged`/`onRestoreInstanceState` — with state synced back via `FieldMapper.copy`
   (`mWindowAdded` is deliberately excluded — see the comment on `LIFECYCLE_COPY_FILTER`).
5. `DCLContext` wraps the base `Context` of the host Application/Activities: `getPackageManager()`
   returns a plugin-based `PackageManagerAggregate`, `getApplicationContext()` returns the shadow
   `Application`, `getResources()` is cached and falls back to the shadow package in
   `getIdentifier`, and `getPackageName()` returns the loaded package **only** when the caller (per
   `CallerClassResolver`'s stack walk) is the loaded APK's own code — platform code such as
   WebView must keep seeing the host package.

The main screen launches APKs with `DCLActivity.intentForAPK(context, apkPath)`; bundled samples
are discovered from `assets/*.apk` (`TEST_APK_DISPLAY_NAMES` only gives them nicer titles). A
few app-specific host manifest entries (`com.dotgears.GameActivity`, Shazam/WhatsApp/OnePlus
activities, Firebase `datatransport` services, …) remain from before the ATM hook existed.

### Code & resource loading

`LoadedApk.load()` writes the APK bytes to a temp file, extracts it into a per-APK cache dir
(`utils/Zip`), discovers `split_config.*.apk` siblings when loaded from an install dir, builds an
`InMemoryDexClassLoader` (parent: the host classloader's parent) whose native library path is the
install dir's `lib/`, the extracted split `lib/` dirs, and the base APK's own extracted
`lib/<abi>` for the host process's most-preferred ABI. 32-bit-only libs can't load in a 64-bit
host; `LoadedApk` logs a warning when an APK ships libs but none match. It then registers
`ResourcesProvider`s (base + splits, as APK and as extracted directory) via the Android 11+
`ResourcesLoader` API —
**this, not the classic package-ID resource trick, is why `minSdk = 30`.** The host's own
resources use package id `0x8f` (`androidResources.additionalParameters`) so they never collide
with a loaded app's `0x7f`. `FileTrackingClassLoader` is installed as the process's classloader
and delegates `loadClass` across every APK it has loaded, falling back to the host.

### Package manager plugin system (`pm/`, `plugins/`, `apk/`)

- `pm.PackageManagerWrapper` — full `PackageManager` passthrough with logging. New public
  `PackageManager` methods must be overridden explicitly here, or the base class's
  "not implemented" stub is hit instead of the real PM (this has caused real crashes).
- `pm.PackageManagerAggregate` — layers an ordered, mutable list of `pm.PackageManagerPlugin`s
  over a base `PackageManagerWrapper` (first match wins, else falls through).
- `plugins.DefaultPluginProvider` builds the process-wide aggregate:
  `PlayServicesBlockingPackageManager` (pretends GMS isn't installed, whitelists `AdActivity`) as
  the base, plus `pm.DefaultPackageManagerPlugin` (patches Firebase's `ComponentDiscoveryService`
  metadata).
- `apk.ManifestAwarePlugin` answers activity/service/provider/application info and a minimal
  action-only `resolveActivity` by parsing the loaded APK's binary `AndroidManifest.xml` via
  `apk.AndroidManifestReader` (uses the `AXML` library). `DCLActivity.onCreate` registers one per
  loaded APK.

### `reflection/` and `shadow/`

`reflection.FieldMapper` / `ReflectionUtils` are the low-level reflective field/method access
helpers everything above is built on (`HiddenApiBypass` from lsposed for hidden methods).
`shadow.ShadowActivity` / `ShadowApplication` use them to reach hidden AOSP fields
(`mMainThread`, `mInstrumentation`, `mPackageInfo`, `mInitialApplication`) and hidden methods
(`Activity.attach`, invoked positionally) — inherently fragile across OS versions, which is also
why broad `StrictMode.VmPolicy.permitNonSdkApiUsage()` is set at startup.

### Permissions and unavoidable failures

The host manifest declares every public non-`BIND_*` `android.permission.*` constant, because
the OS checks the *host's* permissions when loaded code calls permission-gated APIs.
Signature-level permissions still can't be held; `DCLApplication` installs a default uncaught
exception handler that swallows only `SecurityException("Permission Denial…")` on background
threads, and `ShadowApplication.onCreate` catches exceptions from the loaded app's
`Application.onCreate`. App-specific one-off patches also exist (`DCLActivity.disableAutoGameSignIn`,
the `MlKitInitProvider` skip) — prefer generic mechanisms for new fixes.

## Non-obvious invariants when extending this code

- Activities of a loaded APK reached via explicit intents are handled by the ATM hook + proxy
  pool. Other component types (services, receivers, providers reached through the system,
  PendingIntents) are **not** — see `docs/ROADMAP.md`.
- `DCLContext.shadowPackageName` / `shadowApp` are **companion-object (static) state**, and
  `ShadowApplication` patches process-global `ActivityThread` fields — only one loaded APK can be
  "active" at a time. Don't assume concurrent loads work.
- One shadow `Application` per `LoadedApk` (`LoadedApk.shadowApplication`): creating a second one
  per navigation hop breaks apps with path-keyed singletons (e.g. DataStore).
- Don't hardcode the host package name: derive it from a `Context` or from the intent being
  rewritten (only the dead `MyContextWrapper` and `scripts/test-apk.sh` still hardcode it).
- All loaded apps share the host's uid and granted permissions. Private storage is per package: the
  shadow Application/Activity and loaded providers get a `DCLContext(virtualPackage = ...)` whose
  storage APIs resolve to `<hostDataDir>/virtual/<package>/` (`VirtualDataDirs`; SharedPreferences by
  name prefix). Don't pass the host's own context to loaded code, and keep `mBase` out of any
  `FieldMapper.copy` between host and shadow objects. Apps hardcoding `/data/data/<pkg>` still miss. This isolates loaded apps from each other by
  accident; it is not a security boundary (same uid, same process), but package names and file/dir
  names are validated so paths can't be steered outside `virtual/<package>/`.

## Roadmap

[`docs/ROADMAP.md`](docs/ROADMAP.md) lists the remaining generalization work (R1–R20), ordered by
complexity, each written as a fileable issue. Update its status column when an item lands.

## Known dead / in-progress code

- **`pkg/` is entirely unused** — a verbatim port of AOSP's internal `PackageManagerService`
  parsing interfaces. Nothing references it; don't build on it without confirming intent.
- `apk/ApkExtract.kt` is fully commented-out legacy code (an old app-listing adapter).
- `dcl/DCLInstrumentation.kt` (a full `Instrumentation` passthrough) is never instantiated.
- `MyContextWrapper` is only imported by `DCLContext`, not used by it.
- `DCLActivity.onCreate` contains leftover `HandlerThread`/handler scaffolding that no longer
  does anything.
- Assume any given file may have stale/half-finished pieces; check for TODOs before extending a
  class rather than assuming its current behavior is final.
