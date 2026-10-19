package com.mikimn.apkloader.dcl

import android.app.job.JobScheduler
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * `JobScheduler` has *concrete* public methods whose default body throws `UnsupportedOperationException` (the same
 * trap as `PackageManager`, see `PackageManagerWrapperCompletenessTest`). The compiler only forces the abstract ones,
 * so this enforces the rest, by reflection against the compile SDK's `JobScheduler`: a loaded app must never reach
 * a base-class stub because [DCLJobScheduler] forgot to delegate.
 */
class DCLJobSchedulerCompletenessTest {
    private fun key(m: Method) = m.name + m.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }

    @Test fun everyPublicJobSchedulerMethodIsOverridden() {
        val declared = DCLJobScheduler::class.java.declaredMethods.map(::key).toSet()
        val missing = JobScheduler::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) && !it.isSynthetic }
            .map(::key).filter { it !in declared }.sorted()
        assertWithMessage(
            "DCLJobScheduler does not override these JobScheduler methods, so a loaded app calling one gets the " +
                "base-class stub (UnsupportedOperationException) instead of the real scheduler:\n  " + missing.joinToString("\n  ")
        ).that(missing).isEmpty()
    }
}
