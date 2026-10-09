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
