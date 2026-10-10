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

## JVM unit tests (layer A)

`./gradlew testDebugUnitTest` (no device). Plain JUnit + Truth for pure logic (`Zip`, `FieldMapper`,
`DCLActivityProxyPool`, `CallerClassResolver`). Robolectric (SDK 34) is used only where a real
`PackageManager` is needed (`PackageManagerAggregate`, `PlayServicesBlockingPackageManager`,
`DefaultPackageManagerPlugin`). Constraints:

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
`@Ignore`d with the reason (e.g. `ZipTest.rejectsEntriesEscapingOutputDir`, zip-slip); remove the
`@Ignore` in the PR that fixes it.

## Fixture APKs

Fixtures are tiny, dependency-free apps (framework classes only, Java) under `fixtures/<name>/`,
each isolating one loader capability. Gradle builds them as ordinary debug APKs and `app`'s
`syncFixtureApks` task copies them to `assets/fixtures/<name>.apk` of the androidTest source set.
They keep the default resource package id `0x7f`, which must coexist with the host's `0x8f`.

| Fixture | Tier | Isolates |
| --- | --- | --- |
| `fx-hello` | 0/1 | One activity, no custom resources; lifecycle + `getPackageName()` + class loader reported |
| `fx-resources` | 1 | Layout, string/plural (+ night variant), color, dimen, drawable, raw, asset, custom theme |
| `fx-application` | 1 | Custom `Application`; `app.onCreate` must run exactly once (throws on a 2nd instance) |

Fixtures report what they saw through `fixtures/common`'s `Probe` (file under the shared
`filesDir`; the test reads it with `ProbeChannel`). To add a fixture: create `fixtures/<name>`
(copy an existing `build.gradle.kts`, use a unique `com.mikimn.fixture.*` namespace), then add it
to `settings.gradle.kts` and `fixtureNames` in `app/build.gradle.kts`, and a row to
`FixtureApksTest`. Note `Probe` is compiled into every fixture; only one loaded APK is active at
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

- `waitFor` / `waitForNotNull` - poll a condition with a timeout. No `Thread.sleep` in tests.
- `ProbeChannel` - how a test observes what a loaded fixture did. Fixtures share the host's
  data dir, so they append lines to `<filesDir>/probe/<channel>.log`; the test reads/awaits them.
- `FixtureApks` - copies a fixture APK from the androidTest assets (`fixtures/*.apk`) into the
  host's files dir and returns its absolute path, which `DCLActivity.intentForAPK` accepts.
- `LogcatCrashRule` - fails a test if the process logged a `FATAL EXCEPTION` that didn't kill it.

`TestInfrastructureTest` tests these helpers themselves.

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
