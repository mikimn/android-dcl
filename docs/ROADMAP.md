# Generalization roadmap

What still stands between this loader and running arbitrary apps, ordered by complexity.
Each entry is tracked as a GitHub issue (Issue column); the bodies below mirror them.
Tiers: **1** small, local fixes · **2** moderate virtualization-layer work · **3** large, needs new
system-interface hooks · **4** needs pre-declared system-bound stubs · **5** needs root/platform
signing (documented for completeness; likely won't fix).

| # | Tier | Item | Status |
| --- | --- | --- | --- |
| R1 | #13 | 1 | [Forward result/permission/intent/config callbacks from DCLActivity to the shadow Activity](#r1) | done (pending device verification) |
| R2 | #14 | 1 | [Load native libraries bundled inside a standalone APK](#r2) | done (pending device verification) |
| R3 | #15 | 1 | [Remove hardcoded entry points, activity whitelist and host package name](#r3) | open |
| R4 | #16 | 1 | [Survive process death: reload the APK when a proxy activity is restored](#r4) | open |
| R5 | #17 | 2 | [Complete PackageManager answers for loaded packages](#r5) | open |
| R6 | #18 | 2 | [Per-package data directory isolation](#r6) | open |
| R7 | #19 | 2 | [Point ApplicationInfo paths at the loaded APK and use a file-backed dex loader](#r7) | open |
| R8 | #20 | 2 | [Honor launchMode, flags and per-activity attributes in proxy activities](#r8) | open |
| R9 | #21 | 2 | [Fix ContentProvider initialization order and context](#r9) | open |
| R10 | #22 | 2 | [Support multiple loaded APKs concurrently](#r10) | open |
| R11 | #23 | 3 | [Services: in-process Service lifecycle via a proxy service pool](#r11) | open |
| R12 | #24 | 3 | [Register manifest-declared broadcast receivers](#r12) | open |
| R13 | #25 | 3 | [Rewrite PendingIntents (notifications, alarms, shortcuts) to proxy components](#r13) | open |
| R14 | #26 | 3 | [Support android:process multi-process components](#r14) | open |
| R15 | #27 | 3 | [Harden hidden-API reflection across Android versions](#r15) | open |
| R16 | #28 | 4 | [System-bound components (widgets, IME, accessibility, wallpaper, tiles, …)](#r16) | open |
| R17 | #29 | 5 | [Package/signature-bound Google services (Play Services, Sign-In, FCM, Billing, Integrity)](#r17) | open |
| R18 | #30 | 5 | [Signature/privileged permissions](#r18) | open |
| R19 | #31 | 5 | [Anti-tamper, installer verification, DRM and uid-bound Keystore](#r19) | open |
| R20 | #32 | 5 | [Shared uid: permissions, notifications and targetSdk are the host's](#r20) | open |

<a id="r1"></a>
### R1. [Tier 1] Forward result/permission/intent/config callbacks from DCLActivity to the shadow Activity

#### Problem
`DCLActivity` forwards only the core lifecycle (`onStart`/`onResume`/`onPause`/`onStop`/`onDestroy`/`onPostCreate`/`onSaveInstanceState`/…) to the shadow Activity. These are never forwarded:

- `onActivityResult` / `dispatchActivityResult`
- `onRequestPermissionsResult`
- `onNewIntent`
- `onConfigurationChanged`
- `onRestoreInstanceState`

The shadow Activity is attached with the host's `mToken`, so `startActivityForResult` / `requestPermissions` results are delivered by AMS to the **host** `DCLActivity` and stop there.

#### Impact
File/photo pickers, sign-in/OAuth flows, runtime permission prompts and deep links into an already-running activity silently do nothing.

#### Proposed fix
Override each of these in `DCLActivity` and dispatch them to the shadow instance reflectively (same mechanism as `overrideLifecycleCall`). Note `onActivityResult`/`onRequestPermissionsResult` are also routed through `FragmentActivity` to fragments in the shadow, so calling the shadow's own override is enough to reach fragments.

<a id="r2"></a>
### R2. [Tier 1] Load native libraries bundled inside a standalone APK

#### Problem
`LoadedApk.buildClassLoader` builds the native library search path only from the *install directory's* `lib/` folder and from extracted ABI split APKs. The base APK's own `lib/<abi>/*.so` — which `Zip.unzip` already extracts into the cache directory — is never added.

#### Impact
Any APK loaded from assets (or a universal APK with `extractNativeLibs=false`) that ships `.so` files fails with `UnsatisfiedLinkError`: Unity/Unreal games, Flutter, React Native/Hermes, many media apps.

#### Proposed fix
Add `<extracted>/lib/<abi>` for the device's supported ABIs (`Build.SUPPORTED_ABIS`, in preference order — only the first ABI present in the APK) to the library path.

**Done** for ABIs matching the host process's bitness. Remaining gap: apps shipping only 32-bit libs (e.g. `flappy-bird-1-3.apk`: `armeabi`/`armeabi-v7a`/`x86`) can't load them in a 64-bit host process; that would need a separate 32-bit host APK (bitness is per-package, not per-process).

<a id="r3"></a>
### R3. [Tier 1] Remove hardcoded entry points, activity whitelist and host package name

#### Problem
Leftovers from before `ActivityTaskManagerHook` existed:

- `MainActivity.ACTIVITY_WHITELIST` + `MainActivity.startActivity` override — the ATM hook now rewrites every loaded-APK intent automatically.
- `ENTRY_POINTS` — `DCLActivity.intentForAPK` requires an entry for bundled assets, even though `DCLActivity.onCreate` already resolves the launcher activity from the manifest (and ignores the `applicationClassName` extra).
- `"com.mikimn.apkloader"` literals in `DCLActivity.intentForAPK` / `forActivityClass` (`TODO Make this dynamically resolved`).
- `intentForAPK` only treats paths ending in `base.apk` or starting with `/system/` as device paths; other absolute paths (OEM partitions, `/product/...`, `/data/app/.../Foo.apk`) throw.

#### Proposed fix
Resolve the host package via `BuildConfig.APPLICATION_ID`/`Context`, treat any absolute path as a device path, make `ENTRY_POINTS` optional (manifest launcher resolution is the default), and delete the whitelist.

<a id="r4"></a>
### R4. [Tier 1] Survive process death: reload the APK when a proxy activity is restored

#### Problem
When Android kills the process and later restores a proxy activity (recents, back-stack restore), `DCLActivity.onCreate` runs with `KEY_LOADED_APK_NAME` set but an empty `FileTrackingClassLoader`, so `loader.apkFile(loadedApkName)!!` throws a NullPointerException. The `else` branch (`loader.last!!`) has the same issue.

#### Proposed fix
When the named APK isn't loaded yet, load it from the same name (it's the device path or asset name originally passed to `addApkFile`) instead of asserting. The original `KEY_APK_ASSET_FILE_NAME` path already reloads; the proxy path should share that code.

<a id="r5"></a>
### R5. [Tier 2] Complete PackageManager answers for loaded packages

#### Problem
`ManifestAwarePlugin`:
- `getPackageInfo` returns only `applicationInfo` — no `versionCode`/`versionName`, `signatures`/`signingInfo`, `activities`, `services`, `providers`, `requestedPermissions`, `firstInstallTime`.
- `getReceiverInfo` always throws `NameNotFoundException`.
- `resolveActivity` matches only the intent action, ignoring category/data/component and package.
- `queryIntentActivities`, `queryIntentServices`, `queryBroadcastReceivers`, `getLaunchIntentForPackage`, `getInstallSourceInfo` for the shadow package aren't answered by the plugin layer.

#### Impact
Apps that check their own version or signature fail (likely the cause of Meme Generator's "Security error" dialog), and implicit intents within the loaded app don't resolve.

#### Proposed fix
Parse `<receiver>`, version attributes and permission declarations in `AndroidManifestReader`; read signatures from the APK (`PackageManager.getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)`); use full `IntentFilter.match`; extend `PackageManagerPlugin` with the query methods.

<a id="r6"></a>
### R6. [Tier 2] Per-package data directory isolation

#### Problem
All loaded apps write to the host's `files/`, `databases/`, `shared_prefs/`, `cache/`, `no_backup/`. `docs/apk-test-log.md` already records cross-contamination between different loaded apps (stale jobs, DataStore collisions).

#### Proposed fix
Override `getDataDir`, `getFilesDir`, `getCacheDir`, `getCodeCacheDir`, `getNoBackupFilesDir`, `getDir`, `getDatabasePath`/`openOrCreateDatabase`/`databaseList`/`deleteDatabase`, `getSharedPreferences`/`deleteSharedPreferences`, `getExternalFilesDir(s)`/`getExternalCacheDir(s)` in `DCLContext` to redirect to `<hostDataDir>/virtual/<shadowPackage>/...` when a shadow package is active. Also patch `ApplicationInfo.dataDir` passed to the shadow application. Apps that hardcode `/data/data/<pkg>` will still miss.

<a id="r7"></a>
### R7. [Tier 2] Point ApplicationInfo paths at the loaded APK and use a file-backed dex loader

#### Problem
`sourceDir`, `publicSourceDir`, `splitSourceDirs`, `nativeLibraryDir` and `getPackageCodePath()`/`getPackageResourcePath()` all point at the host APK. Libraries that reopen their own APK break (Flutter, Unity, crash reporters, `SplitInstallManager`, MultiDex-style helpers). Dex is loaded with `InMemoryDexClassLoader`, so nothing is AOT-compiled: big apps start slowly and use a lot of memory.

#### Proposed fix
Keep the copied APK on disk (currently a deleted temp file), load it with `DexClassLoader`/`PathClassLoader` (benefits from `dex2oat`/vdex), and set the shadow `ApplicationInfo` paths to the on-disk APK, splits, and extracted native lib dir.

<a id="r8"></a>
### R8. [Tier 2] Honor launchMode, flags and per-activity attributes in proxy activities

#### Problem
`DCLActivityProxyPool` is 8 `standard` slots allocated round-robin. Ignored:
- `launchMode` (`singleTop`/`singleTask`/`singleInstance`), `taskAffinity`, `documentLaunchMode`
- `FLAG_ACTIVITY_CLEAR_TOP` / `SINGLE_TOP` semantics relative to the *real* target class
- `screenOrientation`, `windowSoftInputMode`, `configChanges` (a rotation recreates the host even if the loaded app handles config changes itself), `excludeFromRecents`, `resizeableActivity`.

#### Proposed fix
Declare proxy pools per launch mode (and a `configChanges="all"` variant), pick a pool from the target `ActivityInfo` in `ActivityTaskManagerHook`, track slot→class assignments to implement singleTop/clear-top semantics, and apply `screenOrientation`/`softInputMode` on the host at runtime.

<a id="r9"></a>
### R9. [Tier 2] Fix ContentProvider initialization order and context

#### Problem
`DCLActivity.onCreate` instantiates and `attachInfo`s providers against the **host** base context *before* the shadow `Application` exists. Real order is `Application.attachBaseContext` → providers → `Application.onCreate`, with the provider's context being the app's context. Also providers are re-instantiated on every `DCLActivity.onCreate` (every navigation hop), and `MlKitInitProvider` is special-cased by name.

Clock (`com.oneplus.deskclock`) crashes here (see `docs/apk-test-log.md`).

#### Proposed fix
Move provider install into a once-per-`LoadedApk` step between shadow `Application` creation and `onCreate`, attach with the shadow context, respect `initOrder`, and drop the name-based special case.

<a id="r10"></a>
### R10. [Tier 2] Support multiple loaded APKs concurrently

#### Problem
- `DCLContext.shadowApp` / `shadowPackageName` are static (one active package per process).
- `ShadowApplication` overwrites `ActivityThread.mInitialApplication` and `LoadedApk.mApplication` globally.
- `FileTrackingClassLoader.loadClass` returns the **first** loaded APK that defines a class, so two apps bundling the same library (androidx, OkHttp, Firebase…) can get each other's classes when the framework loads by name (e.g. `LayoutInflater`, fragments restore).

#### Proposed fix
Make the per-package state live on `LoadedApk` and resolve "current package" from the hosting `DCLActivity` (its intent extras) rather than globals; give each shadow activity a context whose `getClassLoader()` is that APK's loader.

<a id="r11"></a>
### R11. [Tier 3] Services: in-process Service lifecycle via a proxy service pool

#### Problem
`DCLContext.startService` is a **no-op** that just returns the component. `bindService`/`startForegroundService` go to the real system and fail for any service not declared in the host manifest. `ManifestAwarePlugin` parses services but nothing runs them.

#### Impact
Music, messaging, sync, fitness, navigation, download managers — anything with background work.

#### Proposed fix
Hook `IActivityManager.startService`/`bindService`/`stopService` (analogous to `ActivityTaskManagerHook`), rewrite to manifest-declared `DCLServiceProxyN` slots (with `foregroundServiceType` variants for Android 14+), and have the proxy drive the loaded `Service` (`attach`, `onCreate`, `onStartCommand`, `onBind`, `onUnbind`, `onDestroy`). Also need a host `JobService` stub for `JobScheduler`/WorkManager `SystemJobService`.

<a id="r12"></a>
### R12. [Tier 3] Register manifest-declared broadcast receivers

#### Problem
Receivers declared in the loaded app's manifest are never registered and `ManifestAwarePlugin.getReceiverInfo` throws. Explicit broadcasts to the loaded app's own receivers (very common for internal events, alarms, notification actions) never arrive.

#### Proposed fix
On load, `registerReceiver` each manifest receiver's intent filters (with the loaded class instantiated via its classloader), and rewrite explicit-component broadcasts via an `IActivityManager.broadcastIntent` hook to a host proxy receiver that dispatches by class name. Manifest-only delivery while the process is dead is out of scope (see PendingIntents issue).

<a id="r13"></a>
### R13. [Tier 3] Rewrite PendingIntents (notifications, alarms, shortcuts) to proxy components

#### Problem
Notification `contentIntent`s, `AlarmManager`, geofences, `JobScheduler`, app shortcuts all go through `IActivityManager.getIntentSender`, which isn't hooked — components point at classes the system can't resolve in the host package. They're also invoked after process death, when nothing is loaded.

#### Proposed fix
Hook `getIntentSender(Locked)` to apply the same rewriting as activities/services/receivers, carrying the APK path in extras so the proxy can reload it (depends on the process-death issue).

<a id="r14"></a>
### R14. [Tier 3] Support android:process multi-process components

#### Problem
Everything runs in the host's single process. Components declared with `android:process=":remote"` (push, media playback, crash-isolated components) run in the main process, and apps that check `Application.getProcessName()` or communicate across their own processes via Binder/`Messenger` can misbehave.

#### Proposed fix
Declare proxy components in a few host-side stub processes (`:p0`…`:pN`), map loaded-app process names onto them, and ensure `DCLApplication` bootstraps loading in each process.

<a id="r15"></a>
### R15. [Tier 3] Harden hidden-API reflection across Android versions

#### Problem
`ShadowActivity.attachActivity` builds `Activity.attach`'s argument list positionally and only branches on `SDK_INT > R`. Any AOSP signature change breaks every loaded app. Other hardcoded names: `mMainThread`, `mInstrumentation`, `mPackageInfo`, `mInitialApplication`, `mToken`, `mIdent`, `mAssistToken`, …

#### Proposed fix
Select `attach` by parameter types (map each parameter type to a value supplier, fail loudly on unknown types), add per-API-level tests (instrumented, API 30–36 matrix) that exercise `attachActivity`, and log/raise clear errors instead of silently invoking with the wrong arity.

<a id="r16"></a>
### R16. [Tier 4] System-bound components (widgets, IME, accessibility, wallpaper, tiles, …)

#### Problem
App widgets, input methods, accessibility services, live wallpapers, VPN, device admin, Quick Settings tiles, notification listeners, autofill, `ChooserTargetService`, dream services — `system_server` binds these by manifest declaration with `BIND_*` permissions and its own metadata. None can be served from a loaded APK.

#### Proposed fix
One host stub per component type that forwards to the loaded implementation (and, for widgets, an `AppWidgetProvider` proxy that rewrites `RemoteViews` package/resources). Each must still be enabled by the user against the host's identity; metadata (e.g. `android.accessibilityservice`) must be host-side.

<a id="r17"></a>
### R17. [Tier 5] Package/signature-bound Google services (Play Services, Sign-In, FCM, Billing, Integrity)

#### Problem
Google Play Services, Google Sign-In, FCM, Play Billing, Play Integrity/SafetyNet, Maps API keys and Firebase App Check validate the calling uid's package name and signing certificate — always the host's.

#### Notes
Not fixable from user space. Options to explore: graceful degradation (fail fast with clear errors instead of async crashes, like the GameHelper patch), or stubbing GMS availability as "not installed" (`PlayServicesBlockingPackageManager` does this for `getPackageInfo`).

<a id="r18"></a>
### R18. [Tier 5] Signature/privileged permissions

#### Problem
Permissions like `READ_GSERVICES` (see Android TV Remote in `docs/apk-test-log.md`) are signature/privileged; the host can never hold them. Currently mitigated by swallowing `Permission Denial` `SecurityException`s on background threads (`DCLApplication.installUnavoidablePermissionDenialGuard`).

#### Notes
Not fixable without platform signing. Keep the targeted mitigations; consider returning empty results from the providers/services involved via the context layer instead of letting the exception happen.

<a id="r19"></a>
### R19. [Tier 5] Anti-tamper, installer verification, DRM and uid-bound Keystore

#### Problem
Banking/streaming apps check installer source, APK signature, and integrity; Widevine and Android Keystore keys are bound to the real uid. These fail or detect the loader by design.

#### Notes
Out of scope for a non-root loader. Partial mitigation for naive self-checks is covered by the PackageManager completeness issue (returning the loaded APK's real signatures/installer).

<a id="r20"></a>
### R20. [Tier 5] Shared uid: permissions, notifications and targetSdk are the host's

#### Problem
All loaded apps share the host uid: runtime permission grants (granting once grants every loaded app), notification channels/settings, battery optimization, data usage, and the `targetSdk` behavior mode (host's 34, not the loaded app's).

#### Notes
Inherent to in-process loading. Could be partially virtualized: namespace notification channel IDs per loaded package, and a per-package permission consent layer in front of `requestPermissions`.
