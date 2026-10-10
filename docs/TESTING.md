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
- `LogcatCrashRule` - fails a test if the process logged a `FATAL EXCEPTION` that didn't kill it.
  It runs `logcat -c`, which clears the **device-wide** log buffer (not just this process's), so don't
  run the suite on a device where someone is collecting logs.
- Always build a `ProbeChannel` from the **target** (host app) context: the protocol depends on the
  host's `filesDir`, which is where loaded fixtures write.

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
