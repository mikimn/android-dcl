package com.mikimn.apkloader.testing

/**
 * Tier markers matching the tier table in docs/apk-test-log.md. Run one tier with
 * `-Pandroid.testInstrumentationRunnerArguments.annotation=com.mikimn.apkloader.testing.Tier0`.
 *
 * - Tier0: loading mechanics (class loader, extraction, manifest parsing), no Activity UI
 * - Tier1: single activity, resources, custom Application
 * - Tier2: multi-activity navigation (ATM hook)
 * - Tier3: SDK-style behavior via synthetic fixtures
 * - Tier4: real third-party APKs (smoke, device-gated)
 */
@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Tier0

@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Tier1

@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Tier2

@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Tier3

@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Tier4
