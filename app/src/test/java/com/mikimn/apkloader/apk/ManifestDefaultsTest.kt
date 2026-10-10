package com.mikimn.apkloader.apk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManifestDefaultsTest {
    @Test fun anExplicitValueAlwaysWins() {
        for (sdk in listOf(0, 21, 30, 31, 34)) for (filter in listOf(false, true)) {
            assertThat(defaultExported(true, filter, sdk)).isTrue()
            assertThat(defaultExported(false, filter, sdk)).isFalse()
        }
    }

    // Before API 31 a component with an intent filter was exported by default.
    @Test fun aFilterMakesAComponentExportedByDefaultOnlyForAppsTargetingBelow31() {
        assertThat(defaultExported(null, true, 30)).isTrue()
        assertThat(defaultExported(null, true, 21)).isTrue()
        assertThat(defaultExported(null, true, 31)).isFalse()
        assertThat(defaultExported(null, true, 34)).isFalse()
    }

    @Test fun withoutAFilterAComponentIsNeverExportedByDefault() {
        for (sdk in listOf(0, 21, 30, 31, 34)) assertThat(defaultExported(null, false, sdk)).isFalse()
    }

    @Test fun anAbsentTargetSdkIsLegacyLikeThePlatform() {
        assertThat(defaultExported(null, true, 0)).isTrue()
    }
}
