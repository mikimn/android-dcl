# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

ApkLoader (`com.mikimn.apkloader`) is a proof-of-concept Android app that dynamically loads and
runs *other* APKs' Activities in-process **without installing them** — no `pm install`, no
`PackageManager` registration. It works by hooking Android's own component-instantiation and
resource-resolution internals via reflection, in the spirit of plugin frameworks like VirtualApp
or Shadow.

Sample target APKs used for manual testing live in `app/src/main/assets/` (`calculator.apk`,
`flappy-bird-1-3.apk`, `simple.apk`).

## Build / test / run

```bash
./gradlew assembleDebug        # build debug APK
./gradlew installDebug         # build + install on connected device/emulator
./gradlew test                 # JVM unit tests
./gradlew testDebugUnitTest --tests "com.mikimn.apkloader.ExampleUnitTest"   # single unit test
./gradlew connectedAndroidTest # instrumented tests (needs a device/emulator)
./gradlew lint
```

There is no CLI-runnable emulator config baked into the repo — instrumented tests and manual
verification require a connected device/emulator at API level ≥ 30 (see `minSdk` below).

## Architecture

### The hook chain: how an external APK's Activity ends up running

1. `AndroidManifest.xml` declares `android:appComponentFactory=".dcl.DCLAppComponentFactory"`,
   so the framework routes *every* component instantiation for this process through it.
2. [`DCLAppComponentFactory`](app/src/main/java/com/mikimn/apkloader/dcl/DCLAppComponentFactory.kt)
   (`CoreComponentFactory` subclass) installs a `FileTrackingClassLoader` at
   `instantiateClassLoader`, and in `instantiateActivity` redirects any activity class name it
   doesn't recognize to the manifest-registered shim `DCLActivity`, stashing the real target
   class name as an intent extra.
3. [`DCLActivity`](app/src/main/java/com/mikimn/apkloader/dcl/DCLActivity.kt) is the real,
   manifest-registered host. `onCreate` reads the target APK's bytes (`AssetReader`), hands them
   to `FileTrackingClassLoader.addApkFile` → `LoadedApk.load()`, registers a `ManifestAwarePlugin`
   for that package, resolves the target `ActivityInfo`, builds a `ShadowApplication`, then uses
   `ShadowActivity` to reflectively call the hidden `Activity.attach(...)` and
   `Instrumentation.callActivityOnCreate` on the *real* external Activity instance. Every
   lifecycle callback on the host (`onStart`/`onResume`/`onPause`/…) is manually forwarded to the
   shadow instance, with state synced via `FieldMapper.copy`.
4. `MainActivity.startActivity` intercepts any `Intent` targeting a class in
   `ACTIVITY_WHITELIST` and redirects it through `DCLActivity.forActivityClass` instead of
   launching it directly — this is how a loaded app's internal navigation (e.g. "next screen")
   keeps working.
5. `DCLContext` / `DCLApplication` / `MyContextWrapper` wrap the base `Context`, overriding
   `getPackageManager()` (returns a plugin-based `PackageManagerAggregate`) and
   `getApplicationContext()` (returns the shadow `Application`), so loaded code perceives a
   package-scoped illusion of "being installed."

### Code & resource loading

`LoadedApk.load()` extracts the target APK's dex/resources to a cache dir (`utils/Zip`), builds
an `InMemoryDexClassLoader` chained to the host's classloader, and registers `ResourcesProvider`s
via the Android 11+ `ResourcesLoader` overlay API — **this, not the classic package-ID resource
trick, is why `minSdk = 30`.** `FileTrackingClassLoader` is installed as the process's classloader
and delegates `loadClass` across every APK it has loaded.

### Package manager plugin system (`pm/`, `plugins/`, `apk/`)

- `pm.PackageManagerWrapper` — full `PackageManager` passthrough with logging.
- `pm.PackageManagerAggregate` — layers an ordered, mutable list of `pm.PackageManagerPlugin`s
  over the wrapper (first match wins, else falls through to the real PM).
- `apk.ManifestAwarePlugin` (implements `PackageManagerPlugin`) answers component-info queries by
  parsing the loaded APK's binary `AndroidManifest.xml` via `apk.AndroidManifestReader`
  (uses the `AXML` library). `DCLActivity.onCreate` registers one of these per loaded APK.
- `pm.DefaultPackageManagerPlugin` patches Firebase's `ComponentDiscoveryService` metadata so
  loaded apps bundling Firebase don't crash on missing component info.
- `plugins.PlayServicesBlockingPackageManager` exists to block GMS-related PM lookups but is
  currently **not wired in** (`plugins.DefaultPluginProvider` has it commented out).

### `reflection/` and `shadow/`

`reflection.FieldMapper` / `ReflectionUtils` are the low-level reflective field/method access
helpers everything above is built on. `shadow.ShadowActivity` / `ShadowApplication` use them to
reach hidden AOSP fields (`mMainThread`, `mInstrumentation`, `mPackageInfo`) and hidden methods
(`Activity.attach`) — this hardcodes AOSP-internal names and is inherently fragile across OS
versions, which is why `HiddenApiBypass` (lsposed) plus a broad
`StrictMode.VmPolicy.permitNonSdkApiUsage()` are used at startup.

## Non-obvious invariants when extending this code

- **Every external Activity/Service class the shim will proxy must be pre-declared** as a
  component in the host `AndroidManifest.xml` (see the many app-specific `<activity>`/`<service>`
  entries there) *and* added to `MainActivity.ACTIVITY_WHITELIST` (for in-app navigation) or
  referenced via `ENTRY_POINTS`/`DCLActivity.intentForAPK` (for the initial launch). There is no
  dynamic manifest merging into the host process's component table — this is the main thing to
  update when wiring up a new target APK.
- `DCLContext.shadowPackageName` / `shadowApp` are **companion-object (static) state** — only one
  loaded APK's shadow application can be "active" at a time. Don't assume concurrent loads work.
- `MyContextWrapper` currently hardcodes the host package name (`"com.mikimn.apkloader"`) in a few
  places marked `TODO Make this dynamically resolved` — don't copy that pattern into new code
  without checking whether it should be reading from the currently loaded shadow package instead.

## Known dead / in-progress code (mid-refactor state)

The project was recently refactored from a flatter structure (deleted `loader/ApkLoader.kt`,
`loader/ApkLoaderImpl.kt`, root-level `DCLActivity.kt`/`DCLApplication.kt`) into the current
`dcl/`, `apk/`, `pkg/`, `plugins/`, `pm/` package layout. Artifacts of that refactor still present:

- **`pkg/` is entirely unused** — a verbatim port of AOSP's internal `PackageManagerService`
  parsing interfaces (`AndroidPackage`, `ParsedActivity`, `ParsedComponent`, `ParsedMainComponent`,
  `ParsedPackage`, `ParsingPackage`). Nothing references it; likely scaffolding for eventually
  replacing `AndroidManifestReader`'s ad hoc parsing. Don't build on it without confirming intent.
- `apk/ApkExtract.kt` is fully commented-out legacy code (an old app-listing adapter).
- `dcl/DCLInstrumentation.kt` (a full `Instrumentation` passthrough) is never instantiated
  anywhere — appears intended to be installed via reflection but that wiring isn't done.
- Assume any given file may have stale/half-finished pieces; check for TODOs before extending a
  class rather than assuming its current behavior is final.
