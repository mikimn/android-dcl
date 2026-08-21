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
| `flappy-bird-1-3.apk` | bundled asset | 3 | FAIL | 2026-08-19 | Splash → GameActivity navigation works (whitelist redirect OK), but the bundled legacy (~2014) Play Games Services SDK does a binder-level round-trip to the real on-device GMS, which rejects the shadow-hosted package/signature identity with `IllegalStateException: A fatal developer error has occurred` (`com.google.android.gms.internal.cw/ct/cs`). Re-enabling `PlayServicesBlockingPackageManager` (see below) did not help - it only blocks `getPackageInfo`-based checks, not this SDK's direct binder communication. Likely needs the SDK's Games-client init call itself intercepted/no-op'd, not a PackageManager-level fix. Tracked, not solved this round. |

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

## Known open issue: the multi-activity ceiling

Every activity a loaded app navigates to via its own explicit-component
`startActivity()` calls must be pre-declared in the host `AndroidManifest.xml`,
because that resolution happens at the OS/ActivityManagerService level before
`DCLAppComponentFactory` ever runs. This caps how complex a real app's
in-app navigation can be without hand-editing the manifest per target class.
See the "Explicitly deferred" section of the test-cycle plan for the intended
fix (wiring `DCLInstrumentation` to generically rewrite outgoing explicit
intents via a placeholder-activity pool).

## How to add a new APK to this log

```bash
export ADB_SERIAL=<device-serial>   # only needed with >1 device attached
scripts/test-apk.sh path/to/app.apk
```

No rebuild or manifest edit needed for a single-activity target - the script
pushes the APK to the device and launches it directly through `DCLActivity`.
Record the result as a new row above.
