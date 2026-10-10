package com.mikimn.apkloader.apk

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Resolves an [Intent] against a loaded package's components the way the system's own resolver
 * does: explicit component/package first, then every [IntentFilter]'s action, category, data and
 * type, ordered by filter priority then match quality.
 */
object ComponentMatcher {
    private const val TAG = "ComponentMatcher"

    fun <T : ComponentInfo> resolve(
        packageName: String,
        components: List<Pair<T, List<IntentFilter>>>,
        intent: Intent,
        flags: Int,
        toResolveInfo: (T) -> ResolveInfo
    ): List<ResolveInfo> {
        val includeDisabled = flags and PackageManager.MATCH_DISABLED_COMPONENTS != 0
        @Suppress("NAME_SHADOWING")
        val components = if (includeDisabled) components else components.filter { it.first.enabled }

        intent.component?.let { target ->
            if (target.packageName != packageName) return emptyList()
            val found = components.firstOrNull { it.first.name == target.className } ?: return emptyList()
            return listOf(toResolveInfo(found.first))
        }
        if (intent.`package` != null && intent.`package` != packageName) return emptyList()

        val defaultOnly = flags and PackageManager.MATCH_DEFAULT_ONLY != 0
        val matches = mutableListOf<Triple<ResolveInfo, IntentFilter, Int>>()
        for ((component, filters) in components) {
            // The system lists a component once, using its best-matching filter.
            val best = filters
                .filter { !defaultOnly || it.hasCategory(Intent.CATEGORY_DEFAULT) }
                .map { it to it.match(intent.action, intent.type, intent.scheme, intent.data, intent.categories, TAG) }
                .filter { it.second >= 0 }
                .maxWithOrNull(compareBy({ it.first.priority }, { it.second })) ?: continue
            val (filter, match) = best
            matches.add(Triple(toResolveInfo(component).also {
                it.filter = filter
                it.priority = filter.priority
                it.match = match
                it.isDefault = filter.hasCategory(Intent.CATEGORY_DEFAULT)
            }, filter, match))
        }
        // sortedWith is stable: equal candidates keep manifest order.
        return matches.sortedWith(compareByDescending<Triple<ResolveInfo, IntentFilter, Int>> { it.second.priority }
            .thenByDescending { it.third }).map { it.first }
    }
}
