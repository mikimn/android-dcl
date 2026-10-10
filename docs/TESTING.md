# Testing

Test plan in one line: prove each tier of dynamic APK loading (see the tier table in
[`apk-test-log.md`](apk-test-log.md)) with small, purpose-built fixture APKs, plus a thin smoke
layer over real third-party APKs.

## Layers

| Layer | Where | Runs on | Purpose |
| --- | --- | --- | --- |
| A. JVM unit tests | `app/src/test` | dev machine | Pure logic: manifest parsing, zip extraction, reflection helpers, `PackageManager` plumbing |
| B. Instrumented tests | `app/src/androidTest` | device/emulator, API >= 30 | Anything touching `ActivityThread`, `ResourcesLoader` or the hook chain |

## Running

```bash
./gradlew testDebugUnitTest                  # layer A
./gradlew connectedDebugAndroidTest          # layer B (needs a device)
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mikimn.apkloader.HostAppSmokeTest
```

### Running on a specific device

`connectedDebugAndroidTest` runs on **every** attached device, including a phone you didn't mean to
test on. Pin one with `ANDROID_SERIAL=<serial> ./gradlew connectedDebugAndroidTest` and check the
`Starting N tests on <model>` lines in the output. Use a plain serial (`b61ad4f5`,
`192.168.0.111:5555`); a wireless-debugging mDNS name (`adb-...._adb-tls-connect._tcp`) did not
match and the run went to another device. A device that is attached twice (USB + Wi-Fi, or Wi-Fi +
wireless debugging) gets two competing runs: disconnect the duplicate.

The suite is expected to pass on both an old and a new Android: it has been run on API 30 (OnePlus
6T) and API 36 (CPH2581). On API 36 that means the fixture suite: loader, provider registration,
hidden-API exemptions (HiddenApiBypass 6.x changes how they are applied), per-package storage and
navigation. **Real third-party apps (e.g. OnePlus Notes) have not been run there.** API 34/35 were not
run. Tests must not assume device state (e.g. dark mode, see
`loadedResourcesResolveByNameWithTheAppsOwnPackageId`).

## JVM unit tests (layer A)

`./gradlew testDebugUnitTest` (no device). Plain JUnit + Truth for pure logic (`Zip`, `FieldMapper`,
`DCLActivityProxyPool`, `CallerClassResolver`). Robolectric 4.12.2 is used only where a real
`PackageManager` is needed (`PackageManagerAggregate`, `PlayServicesBlockingPackageManager`,
`DefaultPackageManagerPlugin`). Constraints:

- Robolectric tests run under **SDK 34** (`@Config(sdk = [34])`, matching `compileSdk`; 4.12.2 supports
  it). That choice only affects the Robolectric-backed base `PackageManager`; the completeness test
  below reflects over the compile-time `android.jar` (also API 34) and does not use Robolectric.
- Robolectric tests must use `@Config(manifest = Config.NONE, application = Application::class)`.
  The app's resource APK is built with `--package-id 0x8f`, which Robolectric's package parser
  rejects, and the manifest's `DCLApplication` is unwanted anyway.
- `AndroidManifestReader` needs a real `Resources`/XML parser, so it is covered by the on-device
  Tier 0 tests rather than here.

**Wrapper completeness.** `PackageManagerWrapperCompletenessTest` reflects over the SDK's
`PackageManager` and requires `PackageManagerWrapper` to override every concrete public method
(otherwise the caller hits the base-class `UnsupportedOperationException` stub). Deliberately
deferred methods can be listed in `app/src/test/resources/package-manager-wrapper-known-gaps.txt`
(currently empty); the test fails on any *new* gap and on any listed method that has since been
fixed, so the list can only shrink. When bumping `compileSdk`, new `PackageManager` methods show
up here as failures.

**Known bugs as `@Ignore`d tests.** A test that encodes the desired behavior of a known bug is kept
`@Ignore`d with the reason; remove the `@Ignore` in the PR that fixes it (the zip-slip test
followed this path).

## Fixture APKs

Fixtures are tiny, dependency-free apps (framework classes only, Java) under `fixtures/<name>/`,
each isolating one loader capability. Gradle builds them as ordinary debug APKs and `app`'s
`syncFixtureApks` task copies them to `assets/fixtures/<name>.apk` of the androidTest source set.
They keep the default resource package id `0x7f`, which must coexist with the host's `0x8f`.

| Fixture | Tier | Isolates |
| --- | --- | --- |
| `fx-hello` | 0/1 | One activity, no custom resources; lifecycle + `getPackageName()` + class loader reported |
| `fx-resources` | 1 | Layout, string/plural (+ night variant), color, dimen, drawable, raw, asset, custom theme |
| `fx-manifest` | 0 | Manifest-reader surface: app/component meta-data (literal + resource refs), alias-only launcher + disabled decoy alias, services, provider, receiver |
| `fx-application` | 1 | Custom `Application`; `app.onCreate` must run exactly once (throws on a 2nd instance) |

Fixtures report what they saw through `fixtures/common`'s `Probe` (file under the shared
`filesDir`; the test reads it with `ProbeChannel`). To add a fixture: create `fixtures/<name>`
(copy an existing `build.gradle.kts`, use a unique `com.mikimn.fixture.*` namespace), then add it
to `settings.gradle.kts` (the app module picks fixtures up from the `:fixtures:*` subprojects
automatically), and add a row to `FixtureApksTest`. Note `Probe` is compiled into every fixture; only one loaded APK is active at
a time, so the duplicate class names don't clash.

`FixtureApksTest` validates fixtures with the framework parser, independent of the loader, so a
later failure can be attributed to the fixture or to the loader.

## Process isolation

The loader keeps process-global state (`DCLContext` statics, patched `ActivityThread` fields,
the `IActivityTaskManager` hook). Instrumented tests therefore run under **Android Test
Orchestrator** with `clearPackageData=true`: every test method gets a fresh process, so one
test's loaded APK can never leak into the next. Consequences:

- Don't rely on state surviving between test methods.
- A test that needs a process restart (process-death restoration) can do so on purpose.
- Runs are slower than a single-process run; this is the intended trade-off.

## Test utilities (`app/src/androidTest/.../testing/`)

- `waitFor` / `waitForNotNull` - poll a condition with a timeout. Don't `Thread.sleep` to wait for the
  code under test (it is slow when things work and flaky when they don't); a sleep is fine to simulate
  a slow *producer* inside a test.
- `ProbeChannel` - how a test observes what a loaded fixture did. Fixtures share the host's
  data dir, so they append lines to `<filesDir>/probe/<channel>.log`; the test reads/awaits them.
- `FixtureApks` - copies a fixture APK from the androidTest assets (`fixtures/*.apk`) into the
  host's files dir and returns its absolute path, which `DCLActivity.intentForAPK` accepts.
- `LogcatCrashRule` - fails a test if the process logged a `FATAL EXCEPTION` that didn't kill it. It
  reads only this process's own log (by pid and start time) and does not use `UiAutomation`, which
  races between orchestrator-spawned processes ("UiAutomationService already registered").
- Always build a `ProbeChannel` from the **target** (host app) context: the protocol depends on the
  host's data dir. A fixture launched through `DCLActivity` writes under its own per-package storage
  (see below), so pass `loadedPackage` to read it; code using the host's own context (providers
  attached by a test, `FixtureLoader`) still writes to the host's `filesDir`.

`TestInfrastructureTest` tests these helpers themselves.

## Per-package storage

Every loaded APK shares the host's uid and data dir, so the loader gives the shadow Application,
shadow Activity and the loaded app's providers a `DCLContext` bound to the loaded package
(`virtualPackage`). Its private-storage APIs (`getFilesDir`, `getCacheDir`, `getDir`, `openFile*`,
databases, `getExternalFilesDir`, ...) resolve to `<hostDataDir>/virtual/<package>/...`
(`VirtualDataDirs`); SharedPreferences are isolated by a name prefix instead of a path. The host's
own contexts are untouched. JVM tests: `VirtualDataDirsTest`, `DCLContextStorageTest`; on-device:
`DCLContextStorageOnDeviceTest`.

## Two copies of host classes (gotcha)

`DCLAppComponentFactory` installs a `FileTrackingClassLoader` as the host's class loader, but the
test APK is loaded by its own loader. Host classes referenced directly from test code can
therefore resolve to a **different `Class` instance** than the one the running app uses
(observed: `isInstanceOf(DCLApplication::class.java)` fails with "expected DCLApplication but was
DCLApplication"). Rules for tests:

- Don't `isInstanceOf` / cast host types; compare `javaClass.name`.
- Don't read or write host companion/static state (e.g. `DCLContext.shadowApp`) directly: you may
  be touching a second copy. Drive the app through intents (`DCLActivity.intentForAPK` only builds
  an `Intent`, which is safe) and observe it through `ProbeChannel`, the UI or `targetContext.classLoader`
  reflection.
