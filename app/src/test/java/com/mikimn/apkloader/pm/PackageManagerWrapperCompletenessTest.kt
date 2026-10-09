package com.mikimn.apkloader.pm

import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * `PackageManager` has many *concrete* public methods whose default body throws
 * `UnsupportedOperationException` (e.g. `getInstallSourceInfo`, added in API 30). A wrapper that
 * doesn't override one hits that stub instead of the real PackageManager, which has crashed real
 * apps (see docs/apk-test-log.md). The compiler only forces overriding the abstract ones, so this
 * test enforces the rest by reflection against the SDK's `PackageManager`.
 */
class PackageManagerWrapperCompletenessTest {
    private fun overridable(m: Method) =
        Modifier.isPublic(m.modifiers) &&
            !Modifier.isStatic(m.modifiers) &&
            !Modifier.isFinal(m.modifiers) &&
            !Modifier.isAbstract(m.modifiers) &&
            !m.isSynthetic

    private fun key(m: Method) = m.name + m.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }

    private fun knownGaps(): Set<String> =
        javaClass.getResourceAsStream("/package-manager-wrapper-known-gaps.txt")!!
            .bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()

    private fun missingMethods(): Set<String> {
        val declaredByWrapper = PackageManagerWrapper::class.java.declaredMethods.map(::key).toSet()
        return PackageManager::class.java.declaredMethods
            .filter(::overridable).map(::key).filter { it !in declaredByWrapper }.toSet()
    }

    @Test fun noNewUnoverriddenPackageManagerMethods() {
        val newGaps = (missingMethods() - knownGaps()).sorted()
        assertWithMessage(
            "PackageManagerWrapper does not override these concrete PackageManager methods, so callers " +
                "get the base-class stub instead of the real PackageManager. Override them (delegating " +
                "to the base) or, if deliberately deferred, add to package-manager-wrapper-known-gaps.txt:\n  " +
                newGaps.joinToString("\n  ")
        ).that(newGaps).isEmpty()
    }

    @Test fun knownGapsListHasNoStaleEntries() {
        val stale = (knownGaps() - missingMethods()).sorted()
        assertWithMessage(
            "These methods are now overridden (or no longer exist); remove them from " +
                "package-manager-wrapper-known-gaps.txt:\n  " + stale.joinToString("\n  ")
        ).that(stale).isEmpty()
    }
}
